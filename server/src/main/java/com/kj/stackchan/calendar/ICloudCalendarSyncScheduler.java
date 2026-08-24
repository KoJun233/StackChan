package com.kj.stackchan.calendar;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ICloudCalendarSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ICloudCalendarSyncScheduler.class);
    private static final Duration SYNC_INTERVAL = Duration.ofHours(1);

    private final ICloudCalendarConnectionRepository connectionRepository;
    private final ICloudCalendarRepository calendarRepository;
    private final ICloudCalendarService calendarService;
    private final Clock clock;

    public ICloudCalendarSyncScheduler(
            ICloudCalendarConnectionRepository connectionRepository,
            ICloudCalendarRepository calendarRepository,
            ICloudCalendarService calendarService,
            Clock clock
    ) {
        this.connectionRepository = connectionRepository;
        this.calendarRepository = calendarRepository;
        this.calendarService = calendarService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
    public void syncDueCalendars() {
        Instant dueBefore = clock.instant().minus(SYNC_INTERVAL);
        List<ICloudCalendarConnectionEntity> connections = connectionRepository.findAllByStatusIn(List.of(
                ICloudCalendarConnectionStatus.CONNECTED,
                ICloudCalendarConnectionStatus.ERROR
        ));
        for (ICloudCalendarConnectionEntity connection : connections) {
            if (!isDue(connection, dueBefore)
                    || !calendarRepository.existsByDeviceIdAndAllowedTrue(connection.getDeviceId())) {
                continue;
            }
            try {
                calendarService.sync(connection.getDeviceId());
            } catch (ICloudCalendarUnavailableException exception) {
                logger.warn(
                        "Scheduled iCloud calendar sync failed for device={} code={}",
                        connection.getDeviceId(),
                        exception.getFailureCode()
                );
            } catch (RuntimeException exception) {
                logger.warn("Scheduled iCloud calendar sync failed for device={}", connection.getDeviceId());
            }
        }
    }

    private boolean isDue(ICloudCalendarConnectionEntity connection, Instant dueBefore) {
        return connection.getLastSyncedAt() == null || !connection.getLastSyncedAt().isAfter(dueBefore);
    }
}
