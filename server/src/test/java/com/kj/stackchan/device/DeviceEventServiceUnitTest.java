package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceEventServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-08-29T00:00:00Z");

    @Test
    void recordsArmedHeartbeatOnlyWithCalibratedServoFeedback() {
        UUID deviceId = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity("body-unit", "legacy");
        DeviceRepository repository = mock(DeviceRepository.class);
        when(repository.findById(deviceId)).thenReturn(Optional.of(device));
        DeviceEventService service = new DeviceEventService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        DeviceBodyDiagnostics body = new DeviceBodyDiagnostics(
                true, true, true, true, true, true, true,
                "NORMAL", "ARMED", "NONE", 0);

        service.recordHeartbeat(
                deviceId, "motion_armed", "body001", -48, true, null, body);

        assertThat(device.getLastSeenAt()).isEqualTo(NOW);
        assertThat(device.getSafetyState()).isEqualTo("motion_armed");
        assertThat(device.getBodyDiagnostics()).isEqualTo(body);
    }

    @Test
    void rejectsArmedHeartbeatWithoutServoFeedback() {
        DeviceRepository repository = mock(DeviceRepository.class);
        DeviceEventService service = new DeviceEventService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC));
        DeviceBodyDiagnostics body = new DeviceBodyDiagnostics(
                true, true, true, true, false, true, false,
                "NORMAL", "ARMED", "NONE", 0);

        assertThatThrownBy(() -> service.recordHeartbeat(
                UUID.randomUUID(), "motion_armed", "body001", -48, true, null, body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("calibrated feedback");
    }
}
