package com.kj.stackchan.weather;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayWeatherServiceTest {

    @Mock private WorkdayWeatherStateRepository stateRepository;
    @Mock private WorkdayWeatherDailyForecastRepository dailyRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private WorkdaySettingsService settingsService;
    @Mock private OpenMeteoClient openMeteoClient;

    private final Instant now = Instant.parse("2026-08-25T15:00:00Z");

    @Test
    void testsCurrentFormLocationWithoutPersistingIt() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(openMeteoClient.fetch(31.2304, 121.4737, java.time.ZoneId.of("Asia/Shanghai")))
                .thenReturn(forecast());
        WorkdayWeatherService service = service();

        var result = service.test(deviceId, new WorkdayWeatherService.LocationCommand(
                "上海办公室", 31.2304, 121.4737, "Asia/Shanghai"
        ));

        assertThat(result.ok()).isTrue();
        assertThat(result.daily()).hasSize(2);
        assertThat(result.summary()).contains("上海办公室当前多云").contains("最高降水概率65%");
        verifyNoInteractions(stateRepository, dailyRepository, settingsService);
    }

    @Test
    void synchronizesConfiguredLocationIntoOneHourCache() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(settingsService.resolve(deviceId)).thenReturn(settings(deviceId));
        when(stateRepository.findById(deviceId)).thenReturn(Optional.empty());
        when(openMeteoClient.fetch(31.2304, 121.4737, java.time.ZoneId.of("Asia/Shanghai")))
                .thenReturn(forecast());
        var firstDaily = new WorkdayWeatherDailyForecastEntity(UUID.randomUUID(), deviceId, forecast().daily().getFirst());
        when(dailyRepository.findAllByDeviceIdOrderByForecastDateAsc(deviceId)).thenReturn(List.of(firstDaily));
        WorkdayWeatherService service = service();

        var result = service.sync(deviceId);

        ArgumentCaptor<WorkdayWeatherStateEntity> state = ArgumentCaptor.forClass(WorkdayWeatherStateEntity.class);
        verify(stateRepository).saveAndFlush(state.capture());
        assertThat(state.getValue().getStatus()).isEqualTo(WorkdayWeatherStatus.READY);
        assertThat(state.getValue().getCacheExpiresAt()).isEqualTo(now.plusSeconds(3600));
        verify(dailyRepository).deleteAllByDeviceId(deviceId);
        verify(dailyRepository).flush();
        verify(dailyRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
        assertThat(result.fresh()).isTrue();
        assertThat(result.summary()).contains("预计降水4.2毫米");
    }

    @Test
    void recordsOnlySafeFailureCodeWhenProviderFails() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(settingsService.resolve(deviceId)).thenReturn(settings(deviceId));
        when(stateRepository.findById(deviceId)).thenReturn(Optional.empty());
        when(openMeteoClient.fetch(31.2304, 121.4737, java.time.ZoneId.of("Asia/Shanghai")))
                .thenThrow(new WorkdayWeatherUnavailableException(
                        WorkdayWeatherFailureCode.REQUEST_FAILED, "provider payload must not escape"
                ));
        WorkdayWeatherService service = service();

        assertThatThrownBy(() -> service.sync(deviceId))
                .isInstanceOf(WorkdayWeatherUnavailableException.class);

        ArgumentCaptor<WorkdayWeatherStateEntity> state = ArgumentCaptor.forClass(WorkdayWeatherStateEntity.class);
        verify(stateRepository).save(state.capture());
        assertThat(state.getValue().getStatus()).isEqualTo(WorkdayWeatherStatus.ERROR);
        assertThat(state.getValue().getLastFailureCode()).isEqualTo(WorkdayWeatherFailureCode.REQUEST_FAILED);
        assertThat(state.getValue().toString()).doesNotContain("provider payload");
    }

    private WorkdayWeatherService service() {
        return new WorkdayWeatherService(
                stateRepository, dailyRepository, deviceRepository, settingsService,
                openMeteoClient, Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings(UUID deviceId) {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                deviceId, false, 31, LocalTime.of(9, 0), LocalTime.of(18, 0),
                50, 10, 10, 45, "上海办公室", 31.2304, 121.4737,
                "Asia/Shanghai", now
        );
    }

    private OpenMeteoClient.Forecast forecast() {
        return new OpenMeteoClient.Forecast(
                new OpenMeteoClient.CurrentWeather(now, 30.2, 34.1, 0, 2),
                List.of(
                        new OpenMeteoClient.DailyForecast(
                                LocalDate.of(2026, 8, 25), 61, 33, 26, 38, 29, 65, 4.2
                        ),
                        new OpenMeteoClient.DailyForecast(
                                LocalDate.of(2026, 8, 26), 2, 32, 25, 36, 28, 20, 0
                        )
                )
        );
    }
}
