package com.kj.stackchan.reminder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderScheduleCalculatorTest {

    private final ReminderScheduleCalculator calculator = new ReminderScheduleCalculator();

    @Test
    void advancesDailyReminderByLocalClockAcrossDstGap() {
        Instant first = Instant.parse("2026-03-07T07:30:00Z");
        ReminderEntity reminder = new ReminderEntity(
                UUID.randomUUID(), "早安", first, "America/New_York",
                ReminderRecurrence.DAILY, 1, LocalDateTime.parse("2026-03-07T02:30"),
                ReminderSource.USER, first
        );

        assertThat(calculator.nextAfter(reminder, first))
                .isEqualTo(Instant.parse("2026-03-08T07:30:00Z"));
    }

    @Test
    void returnsNullForOneTimeReminder() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        ReminderEntity reminder = new ReminderEntity(UUID.randomUUID(), "一次", now, "Asia/Shanghai", now);

        assertThat(calculator.nextAfter(reminder, now)).isNull();
    }
}
