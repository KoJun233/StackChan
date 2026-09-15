package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaProactivity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class RoleTopicFeedbackPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired ProactiveTopicCooldownRepository topics;
    @Autowired DeviceRepository devices;
    @Autowired CompanionRoleRepository roles;

    @Test
    void muteListAndResumeStayWithinTheSameRoleAndDeviceEvenForIdenticalTopicKeys() {
        var service = new ProactiveTopicCooldownService(topics, Clock.systemUTC());
        UUID device = devices.saveAndFlush(new DeviceEntity("feedback", "1")).getId();
        UUID otherDevice = devices.saveAndFlush(new DeviceEntity("feedback-other", "1")).getId();
        UUID first = CompanionRoleEntity.DEFAULT_ROLE_ID;
        UUID second = roles.saveAndFlush(new CompanionRoleEntity("另一个伙伴", PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", "", Instant.now())).getId();
        service.recordMention(device, first, "coffee", Instant.now());
        service.recordMention(device, second, "coffee", Instant.now());
        service.recordMention(otherDevice, second, "coffee", Instant.now());
        assertThat(service.muteLastTopic(device, first)).isTrue();
        assertThat(service.muteLastTopic(device, second)).isTrue();
        topics.flush();
        assertThat(service.list(device, second)).singleElement().satisfies(topic -> assertThat(topic.userMuted()).isTrue());
        service.resume(device, second, "coffee");
        topics.flush();
        assertThat(service.list(device, second)).singleElement().satisfies(topic -> assertThat(topic.userMuted()).isFalse());
        assertThat(service.list(device, first)).singleElement().satisfies(topic -> assertThat(topic.userMuted()).isTrue());
        assertThat(service.list(otherDevice, second)).singleElement().satisfies(topic -> assertThat(topic.userMuted()).isFalse());
        assertThatThrownBy(() -> service.resume(otherDevice, first, "coffee"))
                .isInstanceOf(ProactiveTopicCooldownNotFoundException.class);
    }
}
