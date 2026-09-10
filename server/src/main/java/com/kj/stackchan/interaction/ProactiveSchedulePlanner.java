package com.kj.stackchan.interaction;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import org.springframework.stereotype.Component;

@Component
public class ProactiveSchedulePlanner {

    private static final Duration MINIMUM_LEAD = Duration.ofMinutes(1);

    private final DoubleSupplier randomFraction;

    public ProactiveSchedulePlanner() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    ProactiveSchedulePlanner(DoubleSupplier randomFraction) {
        this.randomFraction = randomFraction;
    }

    public Instant next(InteractionSettingsService.InteractionSettingsSnapshot settings, Instant now) {
        ZoneId zone = ZoneId.of(settings.zoneId());
        Instant earliest = now.plus(MINIMUM_LEAD);
        if (settings.proactiveLastAt() != null) {
            Instant afterInterval = settings.proactiveLastAt()
                    .plus(Duration.ofMinutes(settings.proactiveMinIntervalMinutes()));
            if (afterInterval.isAfter(earliest)) earliest = afterInterval;
        }
        LocalDate localDate = now.atZone(zone).toLocalDate();
        if (localDate.equals(settings.proactiveCounterDate())
                && settings.proactiveCounter() >= settings.proactiveDailyLimit()) {
            Instant tomorrow = localDate.plusDays(1).atStartOfDay(zone).toInstant();
            if (tomorrow.isAfter(earliest)) earliest = tomorrow;
        }

        Window window = windowAtOrAfter(
                earliest, zone, settings.proactiveStart(), settings.proactiveEnd()
        );
        long availableSeconds = Math.max(0, Duration.between(window.start(), window.end()).toSeconds());
        if (availableSeconds == 0) return window.start();
        double fraction = Math.max(0, Math.min(Math.nextDown(1.0), randomFraction.getAsDouble()));
        return window.start().plusSeconds((long) Math.floor(availableSeconds * fraction));
    }

    private Window windowAtOrAfter(Instant earliest, ZoneId zone, LocalTime start, LocalTime end) {
        ZonedDateTime local = earliest.atZone(zone);
        LocalDate date = local.toLocalDate();
        LocalTime time = local.toLocalTime();
        if (start.isBefore(end)) {
            if (time.isBefore(start)) return window(date, date, start, end, zone, earliest);
            if (time.isBefore(end)) return window(date, date, time, end, zone, earliest);
            return window(date.plusDays(1), date.plusDays(1), start, end, zone, earliest);
        }
        if (!time.isBefore(start)) {
            return window(date, date.plusDays(1), time, end, zone, earliest);
        }
        if (time.isBefore(end)) {
            return window(date.minusDays(1), date, time, end, zone, earliest);
        }
        return window(date, date.plusDays(1), start, end, zone, earliest);
    }

    private Window window(LocalDate startDate, LocalDate endDate, LocalTime start, LocalTime end,
                          ZoneId zone, Instant earliest) {
        Instant windowStart = ZonedDateTime.of(startDate, start, zone).toInstant();
        if (windowStart.isBefore(earliest)) windowStart = earliest;
        Instant windowEnd = ZonedDateTime.of(endDate, end, zone).toInstant();
        return new Window(windowStart, windowEnd);
    }

    private record Window(Instant start, Instant end) {
    }
}
