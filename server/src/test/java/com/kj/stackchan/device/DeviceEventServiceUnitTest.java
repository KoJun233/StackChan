package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.workday.WorkdayPilotService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void recordsSequenceRollbackAsDeviceRestartWithoutDuplicatingMotionFailure() {
        UUID deviceId = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity("body-unit", "legacy");
        device.recordDeviceSequence(50L);
        DeviceRepository repository = mock(DeviceRepository.class);
        WorkdayPilotService pilotService = mock(WorkdayPilotService.class);
        when(repository.findById(deviceId)).thenReturn(Optional.of(device));
        DeviceEventService service = new DeviceEventService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        service.setWorkdayPilotService(pilotService);
        DeviceBodyDiagnostics body = new DeviceBodyDiagnostics(
                true, true, true, true, true, true, true,
                "NORMAL", "ARMED", "TIMEOUT", 2);

        service.recordHeartbeat(
                deviceId, 1L, "motion_armed", "body001", -48, true, null, body
        );

        verify(pilotService).recordDeviceRestart(deviceId);
        verify(pilotService, org.mockito.Mockito.never())
                .recordMotionObservation(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(device.getLastDeviceSequence()).isEqualTo(1L);
    }

    @Test
    void recordsOnlyTheNewMotionFailureDelta() {
        UUID deviceId = UUID.randomUUID();
        DeviceEntity device = new DeviceEntity("body-unit", "legacy");
        device.recordDeviceSequence(50L);
        device.recordHeartbeat(
                NOW.minusSeconds(10), "motion_disabled", "body001", -48, true, null,
                new DeviceBodyDiagnostics(
                        true, true, true, true, true, true, true,
                        "NORMAL", "DISABLED", "TIMEOUT", 2)
        );
        DeviceRepository repository = mock(DeviceRepository.class);
        WorkdayPilotService pilotService = mock(WorkdayPilotService.class);
        when(repository.findById(deviceId)).thenReturn(Optional.of(device));
        DeviceEventService service = new DeviceEventService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        service.setWorkdayPilotService(pilotService);
        DeviceBodyDiagnostics body = new DeviceBodyDiagnostics(
                true, true, true, true, true, true, true,
                "NORMAL", "DISABLED", "TIMEOUT", 4);

        service.recordHeartbeat(
                deviceId, 51L, "motion_disabled", "body001", -48, true, null, body
        );

        verify(pilotService).recordMotionObservation(deviceId, "TIMEOUT", 2L);
    }
}
