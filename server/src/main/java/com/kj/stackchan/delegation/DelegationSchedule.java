package com.kj.stackchan.delegation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class DelegationSchedule {
    private DelegationSchedule() {}
    /** Local date/time conversion handles DST gaps and overlaps with java.time's deterministic rules. */
    public static Instant next(DelegationService.TaskInput input, Instant after) {
        if(input.scheduleType()==DelegationService.ScheduleType.ONCE)
            return input.onceAt().isAfter(after)?input.onceAt():null;
        if(input.scheduleType()==DelegationService.ScheduleType.INTERVAL) {
            Instant anchor=input.onceAt();
            if(anchor.isAfter(after)) return anchor;
            long duration=input.intervalMinutes()*60L;
            long steps=Math.floorDiv(java.time.Duration.between(anchor,after).getSeconds(),duration)+1;
            return anchor.plusSeconds(Math.multiplyExact(steps,duration));
        }
        ZoneId zone = ZoneId.of(input.zoneId());
        LocalDate today = LocalDate.ofInstant(after, zone);
        for (int offset = 0; offset <= 8; offset++) {
            LocalDate date = today.plusDays(offset);
            if (input.scheduleType() == DelegationService.ScheduleType.WEEKLY
                    && !input.daysOfWeek().contains(date.getDayOfWeek().getValue())) continue;
            Instant candidate = date.atTime(input.localTime()).atZone(zone).toInstant();
            if (candidate.isAfter(after)) return candidate;
        }
        throw new IllegalArgumentException("DELEGATION_SCHEDULE_INVALID");
    }
    public static Instant occurrence(DelegationService.TaskInput input,Instant now) {
        if(input.scheduleType()==DelegationService.ScheduleType.ONCE) return input.onceAt();
        if(input.scheduleType()==DelegationService.ScheduleType.INTERVAL) {
            long duration=input.intervalMinutes()*60L;
            long steps=Math.max(0,Math.floorDiv(java.time.Duration.between(input.onceAt(),now).getSeconds(),duration));
            return input.onceAt().plusSeconds(Math.multiplyExact(steps,duration));
        }
        ZoneId zone=ZoneId.of(input.zoneId());
        return LocalDate.ofInstant(now,zone).atTime(input.localTime()).atZone(zone).toInstant();
    }
    public static String occurrenceKey(DelegationService.TaskInput input,Instant occurrence,LocalDate date) {
        return switch(input.scheduleType()) {
            case DAILY,WEEKLY -> "local:"+date;
            case ONCE -> "once:"+occurrence;
            case INTERVAL -> "interval:"+occurrence;
        };
    }
}
