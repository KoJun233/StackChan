package com.kj.stackchan.interaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SilentPresenceSchedulePlannerTest {

    private static final Instant NOW = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void schedulesThirtyToNinetyMinutesAheadInsideTheAllowedWindow() {
        var planner = new SilentPresenceSchedulePlanner(() -> 0.5);

        Instant next = planner.next(settings(null, 0), NOW);

        assertThat(next).isEqualTo(Instant.parse("2026-09-11T11:00:00Z"));
    }

    @Test
    void movesToTheNextDayAfterEightExpressions() {
        var planner = new SilentPresenceSchedulePlanner(() -> 0.5);

        Instant next = planner.next(settings(LocalDate.of(2026, 9, 11), 8), NOW);

        assertThat(next).isEqualTo(Instant.parse("2026-09-12T09:30:00Z"));
    }

    private InteractionSettingsService.InteractionSettingsSnapshot settings(LocalDate counterDate, int counter) {
        return new InteractionSettingsService.InteractionSettingsSnapshot(
                java.util.UUID.randomUUID(), 50, false, false, 8, false,
                LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, false, LocalTime.of(9, 0), LocalTime.of(21, 0),
                60, 3, "你好", null, null, 0, NOW, null, false, null,
                true, null, null, counterDate, counter
        );
    }
}
