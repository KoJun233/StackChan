package com.kj.stackchan.reminder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

@Component
public class ReminderScheduleCalculator {

    public Instant nextAfter(ReminderEntity reminder, Instant after) {
        if (reminder.getRecurrenceType() == ReminderRecurrence.NONE
                || reminder.getRecurrenceAnchorLocal() == null) {
            return null;
        }
        LocalDateTime candidate = reminder.getRecurrenceAnchorLocal();
        ZoneId zone = ZoneId.of(reminder.getZoneId());
        int interval = reminder.getRecurrenceInterval();
        while (!candidate.atZone(zone).toInstant().isAfter(after)) {
            candidate = reminder.getRecurrenceType() == ReminderRecurrence.DAILY
                    ? candidate.plusDays(interval)
                    : candidate.plusWeeks(interval);
        }
        return candidate.atZone(zone).toInstant();
    }
}
