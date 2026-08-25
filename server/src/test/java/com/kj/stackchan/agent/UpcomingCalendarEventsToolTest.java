package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.calendar.ICloudCalendarConnectionStatus;
import com.kj.stackchan.calendar.ICloudCalendarService;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UpcomingCalendarEventsToolTest {

    @Test
    void returnsFreshDeviceCalendarEventsInTheConfiguredTimeZone() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-25T14:55:00Z");
        Instant lastSyncedAt = Instant.parse("2026-08-25T14:50:41Z");
        ICloudCalendarService calendarService = mock(ICloudCalendarService.class);
        WorkdaySettingsService settingsService = mock(WorkdaySettingsService.class);
        when(calendarService.get(deviceId)).thenReturn(connection(deviceId, lastSyncedAt));
        when(settingsService.resolve(deviceId)).thenReturn(settings(deviceId, "Asia/Shanghai", now));
        when(calendarService.cachedEvents(deviceId, now, now.plusSeconds(7 * 24 * 60 * 60))).thenReturn(List.of(
                new ICloudCalendarService.CachedEventSnapshot(
                        Instant.parse("2026-08-25T16:00:00Z"),
                        Instant.parse("2026-08-26T16:00:00Z"),
                        true,
                        true,
                        false,
                        "项目评审",
                        "会议室"
                )
        ));

        String result = new UpcomingCalendarEventsTool(
                deviceId,
                calendarService,
                settingsService,
                Clock.fixed(now, ZoneOffset.UTC),
                new ObjectMapper()
        ).upcomingEvents();

        JsonNode json = new ObjectMapper().readTree(result);
        assertThat(json.path("available").asBoolean()).isTrue();
        assertThat(json.path("zoneId").asText()).isEqualTo("Asia/Shanghai");
        assertThat(json.path("total").asInt()).isEqualTo(1);
        assertThat(json.path("events").get(0).path("title").asText()).isEqualTo("项目评审");
        assertThat(json.path("events").get(0).path("startsAt").asText())
                .isEqualTo("2026-08-26T00:00+08:00");
    }

    @Test
    void distinguishesAnExpiredCacheFromAnEmptyFreshCalendar() throws Exception {
        UUID deviceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-26T15:00:00Z");
        ICloudCalendarService calendarService = mock(ICloudCalendarService.class);
        WorkdaySettingsService settingsService = mock(WorkdaySettingsService.class);
        when(calendarService.get(deviceId)).thenReturn(connection(
                deviceId,
                Instant.parse("2026-08-25T14:50:41Z")
        ));
        when(settingsService.resolve(deviceId)).thenReturn(settings(deviceId, "Asia/Shanghai", now));

        String result = new UpcomingCalendarEventsTool(
                deviceId,
                calendarService,
                settingsService,
                Clock.fixed(now, ZoneOffset.UTC),
                new ObjectMapper()
        ).upcomingEvents();

        JsonNode json = new ObjectMapper().readTree(result);
        assertThat(json.path("available").asBoolean()).isFalse();
        assertThat(json.path("unavailableReason").asText()).isEqualTo("CACHE_EXPIRED");
        assertThat(json.path("events")).isEmpty();
    }

    private ICloudCalendarService.ConnectionSnapshot connection(UUID deviceId, Instant lastSyncedAt) {
        return new ICloudCalendarService.ConnectionSnapshot(
                deviceId,
                true,
                "me***@icloud.com",
                true,
                ICloudCalendarConnectionStatus.CONNECTED,
                null,
                lastSyncedAt,
                lastSyncedAt,
                1,
                lastSyncedAt.plusSeconds(24 * 60 * 60),
                List.of()
        );
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings(
            UUID deviceId,
            String zoneId,
            Instant now
    ) {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                deviceId,
                false,
                31,
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                50,
                10,
                10,
                45,
                null,
                null,
                null,
                zoneId,
                now
        );
    }
}
