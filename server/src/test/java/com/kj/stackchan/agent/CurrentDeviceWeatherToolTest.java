package com.kj.stackchan.agent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.weather.WorkdayWeatherService;
import com.kj.stackchan.weather.WorkdayWeatherStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentDeviceWeatherToolTest {

    @Test
    void returnsOnlyFreshDeviceBoundWeather() throws Exception {
        UUID deviceId = UUID.randomUUID();
        WorkdayWeatherService weatherService = mock(WorkdayWeatherService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Instant synced = Instant.parse("2026-08-25T15:00:00Z");
        when(weatherService.get(deviceId)).thenReturn(new WorkdayWeatherService.WeatherSnapshot(
                deviceId, true, true, "上海办公室", "Asia/Shanghai", WorkdayWeatherStatus.READY,
                null, synced, synced, synced.plusSeconds(3600), "上海办公室当前多云。",
                new WorkdayWeatherService.CurrentSnapshot(synced, 30.2, 34.1, 0, 2, "多云"),
                List.of(new WorkdayWeatherService.DailySnapshot(
                        LocalDate.of(2026, 8, 25), 61, "下雨", 33, 26, 38, 29, 65, 4.2
                ))
        ));

        JsonNode result = objectMapper.readTree(
                new CurrentDeviceWeatherTool(deviceId, weatherService, objectMapper).currentWeather()
        );

        assertThat(result.path("available").asBoolean()).isTrue();
        assertThat(result.path("locationName").asText()).isEqualTo("上海办公室");
        assertThat(result.path("daily")).hasSize(1);
    }

    @Test
    void distinguishesMissingConfigurationFromWeatherFacts() throws Exception {
        UUID deviceId = UUID.randomUUID();
        WorkdayWeatherService weatherService = mock(WorkdayWeatherService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(weatherService.get(deviceId)).thenReturn(new WorkdayWeatherService.WeatherSnapshot(
                deviceId, false, false, "", "Asia/Shanghai", null, null,
                null, null, null, null, null, List.of()
        ));

        JsonNode result = objectMapper.readTree(
                new CurrentDeviceWeatherTool(deviceId, weatherService, objectMapper).currentWeather()
        );

        assertThat(result.path("available").asBoolean()).isFalse();
        assertThat(result.path("unavailableReason").asText()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.path("daily")).isEmpty();
    }
}
