package com.kj.stackchan.interaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProactiveSchedulePlannerTest {

    @Test
    void choosesAFutureInstantInsideTheRemainingWindow() {
        Instant now = Instant.parse("2026-09-10T10:00:00Z");
        var planner = new ProactiveSchedulePlanner(() -> 0.5);

        Instant next = planner.next(settings(
                LocalTime.of(9, 0), LocalTime.of(21, 0), null, null, 0
        ), now);

        assertThat(next).isAfterOrEqualTo(now.plusSeconds(60));
        assertThat(next).isBefore(Instant.parse("2026-09-10T21:00:00Z"));
    }

    @Test
    void movesToTheNextLocalDayAfterTheDailyLimit() {
        Instant now = Instant.parse("2026-09-10T18:00:00Z");
        var planner = new ProactiveSchedulePlanner(() -> 0.0);

        Instant next = planner.next(settings(
                LocalTime.of(9, 0), LocalTime.of(21, 0), now.minusSeconds(3600),
                LocalDate.of(2026, 9, 10), 3
        ), now);

        assertThat(next).isEqualTo(Instant.parse("2026-09-11T09:00:00Z"));
    }

    @Test
    void keepsCrossMidnightWindowsAndMinimumInterval() {
        Instant now = Instant.parse("2026-09-10T23:00:00Z");
        var planner = new ProactiveSchedulePlanner(() -> 0.0);

        Instant next = planner.next(settings(
                LocalTime.of(22, 0), LocalTime.of(7, 0), now, null, 0
        ), now);

        assertThat(next).isEqualTo(Instant.parse("2026-09-11T00:00:00Z"));
    }

    private InteractionSettingsService.InteractionSettingsSnapshot settings(
            LocalTime start,
            LocalTime end,
            Instant lastAt,
            LocalDate counterDate,
            int counter
    ) {
        return new InteractionSettingsService.InteractionSettingsSnapshot(
                java.util.UUID.randomUUID(), 50, false, false, 8, false,
                LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, true, start, end,
                60, 3, "你好", lastAt, counterDate, counter, Instant.EPOCH,
                null, true, null
        );
    }
}
