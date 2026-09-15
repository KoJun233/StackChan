package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProactivePausePersistenceTest {
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
    @Autowired JdbcTemplate jdbc;
    @Autowired DeviceRepository devices;
    @Autowired CompanionRoleRepository roles;

    @Test
    void pauseSurvivesReloadExpiresOnLocalMidnightAndResumesOnlyItsScope() {
        Instant now = Instant.parse("2026-11-01T04:00:00Z");
        var device = devices.saveAndFlush(new DeviceEntity("pause", "1")).getId();
        var otherDevice = devices.saveAndFlush(new DeviceEntity("pause-other", "1")).getId();
        var role = CompanionRoleEntity.DEFAULT_ROLE_ID;
        var settings = mock(InteractionSettingsService.class, RETURNS_DEEP_STUBS);
        when(settings.resolve(device).zoneId()).thenReturn("America/New_York");
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var service = new ProactivePauseService(jdbc, clock, settings, devices, roles);
        var state = service.pause(device, role, null);
        assertThat(state.pausedUntil()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
        var reloaded = new ProactivePauseService(jdbc, clock, settings, devices, roles);
        assertThat(reloaded.get(device, role).paused()).isTrue();
        assertThat(reloaded.isPaused(device, role, state.pausedUntil().minusSeconds(1))).isTrue();
        assertThat(reloaded.isPaused(device, role, state.pausedUntil())).isFalse();
        assertThat(reloaded.isPaused(otherDevice, role, now)).isFalse();
        service.pause(otherDevice, role, 60);
        assertThat(service.pause(device, role, 30).pausedUntil()).isEqualTo(now.plusSeconds(1800));
        assertThat(jdbc.queryForObject("select count(*) from role_proactive_pauses where device_id = ?", Integer.class, device)).isEqualTo(1);
        assertThatThrownBy(() -> service.pause(device, role, 0)).isInstanceOf(InvalidInteractionSettingsException.class);
        var otherRole = roles.saveAndFlush(new CompanionRoleEntity("暂停隔离伙伴",
                com.kj.stackchan.persona.PersonaTone.CALM, com.kj.stackchan.persona.PersonaReplyLength.SHORT,
                com.kj.stackchan.persona.PersonaProactivity.RESERVED, "", "", "", now)).getId();
        service.pause(device, otherRole, 60);
        service.resume(device, role);
        assertThat(service.get(device, role).paused()).isFalse();
        assertThat(service.get(otherDevice, role).paused()).isTrue();
        assertThat(service.get(device, otherRole).paused()).isTrue();
    }
}
