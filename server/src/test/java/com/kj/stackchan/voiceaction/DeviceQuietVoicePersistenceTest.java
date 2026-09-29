package com.kj.stackchan.voiceaction;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.interaction.DeviceQuietTodayService;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.ProactivePauseService;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DeviceQuietVoicePersistenceTest {
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

    @Autowired VoiceActionCoordinator coordinator;
    @Autowired DeviceQuietTodayService quiet;
    @Autowired ProactivePauseService pauses;
    @Autowired DeviceRepository devices;
    @Autowired CompanionRoleRepository roles;
    @Autowired InteractionSettingsService settings;
    @Autowired JdbcTemplate jdbc;

    @Test
    void realVoiceRoutingKeepsDeviceQuietAndPartnerPauseIndependentAcrossPartnerChanges() {
        Instant now = Instant.now();
        UUID device = devices.saveAndFlush(new DeviceEntity("quiet-" + UUID.randomUUID(), "test")).getId();
        UUID other = devices.saveAndFlush(new DeviceEntity("quiet-other-" + UUID.randomUUID(), "test")).getId();
        UUID roleA = CompanionRoleEntity.DEFAULT_ROLE_ID;
        UUID roleB = roles.saveAndFlush(new CompanionRoleEntity("安静范围测试伙伴", PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", "", now)).getId();
        UUID conversationA = conversation(roleA, now);
        UUID conversationB = conversation(roleB, now);

        coordinator.handle(device, conversationA, UUID.randomUUID(), "今天别主动聊");
        assertThat(pauses.get(device, roleA).paused()).isTrue();
        assertThat(pauses.get(device, roleB).paused()).isFalse();
        assertThat(quiet.get(device).quiet()).isFalse();

        assertThat(coordinator.handle(device, conversationB, UUID.randomUUID(), "今天安静点").reply())
                .contains("所有伙伴", "普通提醒照常");
        assertThat(quiet.get(device).quiet()).isTrue();
        assertThat(quiet.get(other).quiet()).isFalse();
        ZoneId zone = ZoneId.of(settings.resolve(device).zoneId());
        assertThat(quiet.get(device).pausedUntil()).isEqualTo(now.atZone(zone).toLocalDate()
                .plusDays(1).atStartOfDay(zone).toInstant());
        assertThat(pauses.get(device, roleA).paused()).isTrue();
        assertThat(pauses.get(device, roleB).paused()).isFalse();

        coordinator.handle(device, conversationA, UUID.randomUUID(), "恢复设备陪伴");
        assertThat(quiet.get(device).quiet()).isFalse();
        assertThat(pauses.get(device, roleA).paused()).isTrue();
        assertThat(devices.findById(device).orElseThrow().getSafetyState()).isEqualTo("motion_disabled");
    }

    private UUID conversation(UUID role, Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into conversations(id,title,created_at,updated_at,role_id) values (?,?,?,?,?)",
                id, "安静范围测试对话", Timestamp.from(now), Timestamp.from(now), role);
        return id;
    }
}
