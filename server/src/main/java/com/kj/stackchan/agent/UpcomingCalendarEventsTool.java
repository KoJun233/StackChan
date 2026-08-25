package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.calendar.ICloudCalendarService;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.springframework.ai.tool.annotation.Tool;

public class UpcomingCalendarEventsTool {

    public static final String ID = "upcoming_device_calendar_events";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration QUERY_WINDOW = Duration.ofDays(7);
    private static final int MAX_EVENTS = 8;

    private final UUID deviceId;
    private final ICloudCalendarService calendarService;
    private final WorkdaySettingsService workdaySettingsService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public UpcomingCalendarEventsTool(
            UUID deviceId,
            ICloudCalendarService calendarService,
            WorkdaySettingsService workdaySettingsService,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.deviceId = deviceId;
        this.calendarService = calendarService;
        this.workdaySettingsService = workdaySettingsService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = ID,
            description = "返回当前认证设备未来七天内尚未结束的只读 iCloud 日历缓存；"
                    + "只包含管理员允许的日历，不接受模型指定设备。"
    )
    public String upcomingEvents() {
        Instant now = clock.instant();
        ICloudCalendarService.ConnectionSnapshot connection = calendarService.get(deviceId);
        ZoneId zoneId = ZoneId.of(workdaySettingsService.resolve(deviceId).zoneId());
        if (!connection.configured()) {
            return json(Result.unavailable(zoneId.getId(), "NOT_CONNECTED"));
        }
        if (connection.lastSyncedAt() == null) {
            return json(Result.unavailable(zoneId.getId(), "NOT_SYNCED"));
        }
        Instant cacheExpiresAt = connection.lastSyncedAt().plus(CACHE_TTL);
        if (!cacheExpiresAt.isAfter(now)) {
            return json(Result.unavailable(zoneId.getId(), "CACHE_EXPIRED"));
        }

        List<Event> events = calendarService.cachedEvents(deviceId, now, now.plus(QUERY_WINDOW)).stream()
                .map(event -> new Event(
                        event.title(),
                        localDateTime(event.startsAt(), zoneId),
                        localDateTime(event.endsAt(), zoneId),
                        event.allDay(),
                        event.busy(),
                        event.privateEvent(),
                        event.location()
                ))
                .toList();
        return json(new Result(
                true,
                null,
                zoneId.getId(),
                connection.lastSyncedAt().toString(),
                cacheExpiresAt.toString(),
                events.size(),
                events.size() > MAX_EVENTS,
                events.stream().limit(MAX_EVENTS).toList()
        ));
    }

    private String localDateTime(Instant instant, ZoneId zoneId) {
        return ZonedDateTime.ofInstant(instant, zoneId).toOffsetDateTime().toString();
    }

    private String json(Result result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize calendar events", exception);
        }
    }

    private record Result(
            boolean available,
            String unavailableReason,
            String zoneId,
            String lastSyncedAt,
            String cacheExpiresAt,
            int total,
            boolean truncated,
            List<Event> events
    ) {
        private static Result unavailable(String zoneId, String reason) {
            return new Result(false, reason, zoneId, null, null, 0, false, List.of());
        }
    }

    private record Event(
            String title,
            String startsAt,
            String endsAt,
            boolean allDay,
            boolean busy,
            boolean privateEvent,
            String location
    ) {
    }
}
