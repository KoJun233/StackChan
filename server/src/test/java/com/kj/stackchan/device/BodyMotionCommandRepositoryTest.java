package com.kj.stackchan.device;

import java.time.Instant;
import java.util.UUID;

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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BodyMotionCommandRepositoryTest {
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

    @Autowired DeviceRepository devices;
    @Autowired BodyMotionCommandRepository commands;

    @Test
    void finalResultIsBoundToDeviceMotionAndCommandAndCannotBeDowngraded() {
        UUID deviceId = devices.saveAndFlush(new DeviceEntity("body-result-" + UUID.randomUUID(), "test")).getId();
        UUID commandId = UUID.randomUUID();
        Instant created = Instant.parse("2026-09-23T00:00:00Z");
        commands.saveAndFlush(new BodyMotionCommandEntity(commandId, deviceId, "NOD_SMALL", created));

        assertThat(commands.markAcknowledged(deviceId, commandId, "ACCEPTED", created.plusSeconds(1))).isEqualTo(1);
        assertThat(commands.markFinal(deviceId, commandId, "WAKE", "COMPLETED", "NONE", created.plusSeconds(2)))
                .isZero();
        assertThat(commands.markFinal(UUID.randomUUID(), commandId, "NOD_SMALL", "COMPLETED", "NONE",
                created.plusSeconds(2))).isZero();
        assertThat(commands.markFinal(deviceId, commandId, "NOD_SMALL", "COMPLETED", "NONE",
                created.plusSeconds(2))).isEqualTo(1);
        assertThat(commands.markFinal(deviceId, commandId, "NOD_SMALL", "FAILED", "FEEDBACK_FAULT",
                created.plusSeconds(3))).isZero();
        assertThat(commands.markAcknowledged(deviceId, commandId, "REJECTED", created.plusSeconds(4))).isZero();
        assertThat(commands.findByIdAndDeviceId(commandId, deviceId).orElseThrow().getStatus())
                .isEqualTo("COMPLETED");

        UUID unsentId = UUID.randomUUID();
        commands.saveAndFlush(new BodyMotionCommandEntity(unsentId, deviceId, "DROWSY", created));
        assertThat(commands.markDeliveryFailed(deviceId, unsentId, created.plusSeconds(1))).isEqualTo(1);
        assertThat(commands.markFinal(deviceId, unsentId, "DROWSY", "COMPLETED", "NONE",
                created.plusSeconds(2))).isZero();
    }

    @Test
    void lateMatchingResultCanResolveAnUnconfirmedCommand() {
        UUID deviceId = devices.saveAndFlush(new DeviceEntity("body-late-" + UUID.randomUUID(), "test")).getId();
        UUID commandId = UUID.randomUUID();
        Instant created = Instant.parse("2026-09-23T00:00:00Z");
        commands.saveAndFlush(new BodyMotionCommandEntity(commandId, deviceId, "THINK", created));

        assertThat(commands.markUnconfirmed(deviceId, commandId,
                created.plusSeconds(15), created.plusSeconds(16))).isEqualTo(1);
        assertThat(commands.markAcknowledged(deviceId, commandId, "ACCEPTED", created.plusSeconds(17)))
                .isZero();
        assertThat(commands.markFinal(deviceId, commandId, "THINK", "STOPPED", "VOICE_STOP",
                created.plusSeconds(18))).isEqualTo(1);
        assertThat(commands.findByIdAndDeviceId(commandId, deviceId).orElseThrow().getStatus())
                .isEqualTo("STOPPED");
    }

    @Test
    void deliveryFailureCannotReplaceAnAlreadyAcceptedOrCompletedCommand() {
        UUID deviceId = devices.saveAndFlush(new DeviceEntity("body-delivery-" + UUID.randomUUID(), "test")).getId();
        UUID commandId = UUID.randomUUID();
        Instant created = Instant.parse("2026-09-23T00:00:00Z");
        commands.saveAndFlush(new BodyMotionCommandEntity(commandId, deviceId, "WAKE", created));

        assertThat(commands.markDeliveryFailed(UUID.randomUUID(), commandId, created.plusSeconds(1))).isZero();
        assertThat(commands.markAcknowledged(deviceId, commandId, "ACCEPTED", created.plusSeconds(1))).isEqualTo(1);
        assertThat(commands.markDeliveryFailed(deviceId, commandId, created.plusSeconds(2))).isZero();
        assertThat(commands.markFinal(deviceId, commandId, "WAKE", "COMPLETED", "NONE",
                created.plusSeconds(3))).isEqualTo(1);
        assertThat(commands.markDeliveryFailed(deviceId, commandId, created.plusSeconds(4))).isZero();
        assertThat(commands.findByIdAndDeviceId(commandId, deviceId).orElseThrow().getStatus())
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiryScanOnlyFindsOldUnresolvedAutomaticCommands() {
        UUID deviceId = devices.saveAndFlush(new DeviceEntity("body-expiry-" + UUID.randomUUID(), "test")).getId();
        Instant now = Instant.parse("2026-09-23T00:01:00Z");
        var oldAutomatic = commands.saveAndFlush(new BodyMotionCommandEntity(
                UUID.randomUUID(), deviceId, "THINK", true, "web-think:old", now.minusSeconds(20)));
        commands.saveAndFlush(new BodyMotionCommandEntity(
                UUID.randomUUID(), deviceId, "WAKE", true, "workday-start:new", now.minusSeconds(5)));
        commands.saveAndFlush(new BodyMotionCommandEntity(
                UUID.randomUUID(), deviceId, "NOD_SMALL", now.minusSeconds(20)));

        assertThat(commands.findTop100ByAutomaticTrueAndStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                java.util.List.of("SENT", "ACCEPTED"), now.minusSeconds(15)))
                .extracting(BodyMotionCommandEntity::getId)
                .containsExactly(oldAutomatic.getId());
    }
}
