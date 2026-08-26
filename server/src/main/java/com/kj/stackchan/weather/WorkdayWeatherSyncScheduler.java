package com.kj.stackchan.weather;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.kj.stackchan.workday.DeviceWorkdaySettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkdayWeatherSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WorkdayWeatherSyncScheduler.class);
    static final Duration REFRESH_AHEAD = Duration.ofMinutes(10);
    static final Duration RETRY_DELAY = Duration.ofMinutes(10);

    private final DeviceWorkdaySettingsRepository settingsRepository;
    private final WorkdayWeatherStateRepository stateRepository;
    private final WorkdayWeatherService weatherService;
    private final Clock clock;

    public WorkdayWeatherSyncScheduler(
            DeviceWorkdaySettingsRepository settingsRepository,
            WorkdayWeatherStateRepository stateRepository,
            WorkdayWeatherService weatherService,
            Clock clock
    ) {
        this.settingsRepository = settingsRepository;
        this.stateRepository = stateRepository;
        this.weatherService = weatherService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT10S")
    public void syncDueWeather() {
        Instant now = clock.instant();
        settingsRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNull().forEach(settings -> {
            WorkdayWeatherStateEntity state = stateRepository.findById(settings.getDeviceId()).orElse(null);
            boolean due = state == null
                    || state.getLastAttemptedAt() == null
                    || !state.matches(
                            settings.getLocationName().trim(), settings.getLatitude(), settings.getLongitude(),
                            settings.getZoneId()
                    )
                    || state.getStatus() == WorkdayWeatherStatus.READY
                    && (state.getCacheExpiresAt() == null
                            || !state.getCacheExpiresAt().isAfter(now.plus(REFRESH_AHEAD)))
                    || state.getStatus() != WorkdayWeatherStatus.READY
                    && !state.getLastAttemptedAt().isAfter(now.minus(RETRY_DELAY));
            if (!due) {
                return;
            }
            try {
                weatherService.sync(settings.getDeviceId());
            } catch (WorkdayWeatherUnavailableException exception) {
                logger.warn(
                        "Scheduled Open-Meteo sync failed for device={} code={}",
                        settings.getDeviceId(),
                        exception.getFailureCode()
                );
            } catch (RuntimeException exception) {
                logger.warn("Scheduled Open-Meteo sync failed for device={}", settings.getDeviceId());
            }
        });
    }
}
