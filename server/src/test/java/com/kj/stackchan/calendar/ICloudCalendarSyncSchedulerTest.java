package com.kj.stackchan.calendar;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ICloudCalendarSyncSchedulerTest {

    @Test
    void syncsOnlyDueConnectionsWithAllowedCalendarsAndContinuesAfterFailure() {
        Instant now = Instant.parse("2026-08-25T15:00:00Z");
        ICloudCalendarConnectionEntity due = connected(UUID.randomUUID(), now.minusSeconds(7200));
        ICloudCalendarConnectionEntity fresh = connected(UUID.randomUUID(), now.minusSeconds(1800));
        ICloudCalendarConnectionEntity failing = connected(UUID.randomUUID(), now.minusSeconds(7200));
        ICloudCalendarConnectionEntity noAllowedCalendars = connected(UUID.randomUUID(), now.minusSeconds(7200));
        ICloudCalendarConnectionRepository connectionRepository = mock(ICloudCalendarConnectionRepository.class);
        ICloudCalendarRepository calendarRepository = mock(ICloudCalendarRepository.class);
        ICloudCalendarService calendarService = mock(ICloudCalendarService.class);
        when(connectionRepository.findAllByStatusIn(List.of(
                ICloudCalendarConnectionStatus.CONNECTED,
                ICloudCalendarConnectionStatus.ERROR
        ))).thenReturn(List.of(due, fresh, failing, noAllowedCalendars));
        when(calendarRepository.existsByDeviceIdAndAllowedTrue(due.getDeviceId())).thenReturn(true);
        when(calendarRepository.existsByDeviceIdAndAllowedTrue(failing.getDeviceId())).thenReturn(true);
        when(calendarRepository.existsByDeviceIdAndAllowedTrue(noAllowedCalendars.getDeviceId())).thenReturn(false);
        when(calendarService.sync(failing.getDeviceId())).thenThrow(new ICloudCalendarUnavailableException(
                ICloudCalendarFailureCode.SYNC_FAILED,
                "sync failed"
        ));

        new ICloudCalendarSyncScheduler(
                connectionRepository,
                calendarRepository,
                calendarService,
                Clock.fixed(now, ZoneOffset.UTC)
        ).syncDueCalendars();

        verify(calendarService).sync(due.getDeviceId());
        verify(calendarService).sync(failing.getDeviceId());
        verify(calendarService, never()).sync(fresh.getDeviceId());
        verify(calendarService, never()).sync(noAllowedCalendars.getDeviceId());
    }

    private ICloudCalendarConnectionEntity connected(UUID deviceId, Instant lastSyncedAt) {
        ICloudCalendarConnectionEntity connection = new ICloudCalendarConnectionEntity(
                deviceId,
                "me@icloud.com",
                "ciphertext",
                "initialization-vector",
                lastSyncedAt.minusSeconds(60)
        );
        connection.connected(
                "https://p01-caldav.icloud.com/principal/",
                "https://p01-caldav.icloud.com/calendars/",
                lastSyncedAt.minusSeconds(30)
        );
        connection.synced(lastSyncedAt);
        return connection;
    }
}
