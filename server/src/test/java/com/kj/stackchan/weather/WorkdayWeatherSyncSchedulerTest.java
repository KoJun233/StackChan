package com.kj.stackchan.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.workday.DeviceWorkdaySettingsEntity;
import com.kj.stackchan.workday.DeviceWorkdaySettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayWeatherSyncSchedulerTest {

    @Mock private DeviceWorkdaySettingsRepository settingsRepository;
    @Mock private WorkdayWeatherStateRepository stateRepository;
    @Mock private WorkdayWeatherService weatherService;

    @Test
    void synchronizesOnlyDueConfiguredLocationsAndIsolatesFailures() {
        Instant now = Instant.parse("2026-08-25T15:00:00Z");
        DeviceWorkdaySettingsEntity due = configuredSettings(UUID.randomUUID(), now);
        DeviceWorkdaySettingsEntity fresh = configuredSettings(UUID.randomUUID(), now);
        when(settingsRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNull())
                .thenReturn(List.of(due, fresh));
        when(stateRepository.findById(due.getDeviceId())).thenReturn(Optional.empty());
        WorkdayWeatherStateEntity freshState = new WorkdayWeatherStateEntity(
                fresh.getDeviceId(), "上海办公室", 31.2304, 121.4737, "Asia/Shanghai", now.minusSeconds(60)
        );
        freshState.synced(
                "上海办公室", 31.2304, 121.4737, "Asia/Shanghai",
                new OpenMeteoClient.CurrentWeather(now, 30, 32, 0, 2),
                now.minusSeconds(60), now.plusSeconds(3540)
        );
        when(stateRepository.findById(fresh.getDeviceId())).thenReturn(Optional.of(freshState));
        when(weatherService.sync(due.getDeviceId())).thenThrow(new WorkdayWeatherUnavailableException(
                WorkdayWeatherFailureCode.REQUEST_FAILED, "unavailable"
        ));
        WorkdayWeatherSyncScheduler scheduler = new WorkdayWeatherSyncScheduler(
                settingsRepository, stateRepository, weatherService, Clock.fixed(now, ZoneOffset.UTC)
        );

        scheduler.syncDueWeather();

        verify(weatherService).sync(due.getDeviceId());
        verifyNoMoreInteractions(weatherService);
    }

    @Test
    void refreshesBeforeExpiryAndRetriesFailuresAfterBackoff() {
        Instant now = Instant.parse("2026-08-25T15:00:00Z");
        DeviceWorkdaySettingsEntity expiring = configuredSettings(UUID.randomUUID(), now);
        DeviceWorkdaySettingsEntity retryable = configuredSettings(UUID.randomUUID(), now);
        DeviceWorkdaySettingsEntity recentFailure = configuredSettings(UUID.randomUUID(), now);
        when(settingsRepository.findAllByLatitudeIsNotNullAndLongitudeIsNotNull())
                .thenReturn(List.of(expiring, retryable, recentFailure));
        when(stateRepository.findById(expiring.getDeviceId()))
                .thenReturn(Optional.of(readyState(expiring.getDeviceId(), now, now.plusSeconds(300))));
        when(stateRepository.findById(retryable.getDeviceId()))
                .thenReturn(Optional.of(errorState(retryable.getDeviceId(), now.minusSeconds(601))));
        when(stateRepository.findById(recentFailure.getDeviceId()))
                .thenReturn(Optional.of(errorState(recentFailure.getDeviceId(), now.minusSeconds(599))));
        WorkdayWeatherSyncScheduler scheduler = new WorkdayWeatherSyncScheduler(
                settingsRepository, stateRepository, weatherService, Clock.fixed(now, ZoneOffset.UTC)
        );

        scheduler.syncDueWeather();

        verify(weatherService).sync(expiring.getDeviceId());
        verify(weatherService).sync(retryable.getDeviceId());
        verifyNoMoreInteractions(weatherService);
    }

    private WorkdayWeatherStateEntity readyState(UUID deviceId, Instant now, Instant expiresAt) {
        WorkdayWeatherStateEntity state = errorState(deviceId, now.minusSeconds(60));
        state.synced(
                "上海办公室", 31.2304, 121.4737, "Asia/Shanghai",
                new OpenMeteoClient.CurrentWeather(now, 30, 32, 0, 2), now.minusSeconds(60), expiresAt
        );
        return state;
    }

    private WorkdayWeatherStateEntity errorState(UUID deviceId, Instant attemptedAt) {
        return new WorkdayWeatherStateEntity(
                deviceId, "上海办公室", 31.2304, 121.4737, "Asia/Shanghai", attemptedAt
        );
    }

    private DeviceWorkdaySettingsEntity configuredSettings(UUID deviceId, Instant now) {
        DeviceWorkdaySettingsEntity entity = new DeviceWorkdaySettingsEntity(deviceId, now);
        entity.update(
                false, 31, java.time.LocalTime.of(9, 0), java.time.LocalTime.of(18, 0),
                50, 10, 10, 45, "上海办公室", 31.2304, 121.4737, "Asia/Shanghai", now
        );
        return entity;
    }
}
