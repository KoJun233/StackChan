package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.workday.WorkdayCompanionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceEventServicePresenceTest {

    @Test
    void forwardsBothShortPresenceTransitionsFromHeartbeats() {
        DeviceRepository repository = mock(DeviceRepository.class);
        WorkdayCompanionService workday = mock(WorkdayCompanionService.class);
        DeviceEntity device = new DeviceEntity("presence-" + UUID.randomUUID(), "d18b3cd");
        UUID deviceId = device.getId();
        when(repository.findById(deviceId)).thenReturn(Optional.of(device));
        DeviceEventService events = new DeviceEventService(repository,
                Clock.fixed(Instant.parse("2026-09-29T01:00:00Z"), ZoneOffset.UTC));
        events.setWorkdayCompanionService(workday);

        events.recordHeartbeat(deviceId, 1L, DeviceEventService.MOTION_DISABLED,
                "d18b3cd", -48, true, null, body(true));
        assertThat(device.getBodyDiagnostics().present()).isTrue();

        events.recordHeartbeat(deviceId, 2L, DeviceEventService.MOTION_DISABLED,
                "d18b3cd", -48, true, null, body(false));
        assertThat(device.getBodyDiagnostics().present()).isFalse();

        InOrder order = inOrder(workday);
        order.verify(workday).presenceChanged(deviceId, true);
        order.verify(workday).presenceChanged(deviceId, false);
        order.verifyNoMoreInteractions();
    }

    private static DeviceBodyDiagnostics body(boolean present) {
        return new DeviceBodyDiagnostics(true, true, true, true, true, true,
                present, "NORMAL", "DISABLED", "NONE", 0);
    }
}
