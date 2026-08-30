package com.kj.stackchan.workday;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkdayPilotCompletionSchedulerTest {

    @Test
    void continuesWithOtherDevicesAfterOneNotificationFails() {
        UUID first = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("10000000-0000-0000-0000-000000000002");
        WorkdayPilotCompletionService service = mock(WorkdayPilotCompletionService.class);
        when(service.candidateDeviceIds()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("queue failed")).when(service).queueIfComplete(first);

        new WorkdayPilotCompletionScheduler(service).queueCompletedPilotNotifications();

        verify(service).queueIfComplete(first);
        verify(service).queueIfComplete(second);
    }
}
