package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Import(DeviceEventServiceTest.FixedClockConfiguration.class)
class DeviceEventServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceEventService deviceEventService;

    @Test
    void persistsHeartbeatLivenessWithoutEnablingMotion() {
        DeviceEntity device = deviceRepository.save(new DeviceEntity(
                "heartbeat-" + UUID.randomUUID(),
                "1.2.3"
        ));

        deviceEventService.recordHeartbeat(device.getId(), "motion_disabled");

        DeviceEntity updated = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(updated.getLastSeenAt()).isEqualTo(FIXED_NOW);
        assertThat(updated.getSafetyState()).isEqualTo("motion_disabled");
    }

    @Test
    void persistsFirmwareVersionWhenTheHeartbeatReportsOne() {
        DeviceEntity device = deviceRepository.save(new DeviceEntity(
                "firmware-" + UUID.randomUUID(),
                "7f16bd7"
        ));

        deviceEventService.recordHeartbeat(device.getId(), "motion_disabled", "b954a43");

        DeviceEntity updated = deviceRepository.findById(device.getId()).orElseThrow();
        assertThat(updated.getFirmwareVersion()).isEqualTo("b954a43");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }
}
