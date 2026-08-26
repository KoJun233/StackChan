package com.kj.stackchan.agent;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.weather.WorkdayWeatherService;
import org.springframework.ai.tool.annotation.Tool;

public class CurrentDeviceWeatherTool {

    public static final String ID = "current_device_weather";

    private final UUID deviceId;
    private final WorkdayWeatherService weatherService;
    private final ObjectMapper objectMapper;

    public CurrentDeviceWeatherTool(UUID deviceId, WorkdayWeatherService weatherService, ObjectMapper objectMapper) {
        this.deviceId = deviceId;
        this.weatherService = weatherService;
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = ID,
            description = "返回当前认证设备固定地点的只读天气缓存，包含当前状况和今天、明天预报；"
                    + "不接受模型指定设备或位置。"
    )
    public String currentWeather() {
        WorkdayWeatherService.WeatherSnapshot snapshot = weatherService.get(deviceId);
        String unavailableReason = null;
        if (!snapshot.configured()) {
            unavailableReason = "NOT_CONFIGURED";
        } else if (snapshot.lastSyncedAt() == null) {
            unavailableReason = snapshot.lastFailureCode() == null ? "NOT_SYNCED" : "LAST_SYNC_FAILED";
        } else if (!snapshot.fresh()) {
            unavailableReason = "CACHE_EXPIRED";
        }
        Result result = new Result(
                unavailableReason == null,
                unavailableReason,
                snapshot.locationName(),
                snapshot.zoneId(),
                snapshot.lastFailureCode() == null ? null : snapshot.lastFailureCode().name(),
                snapshot.lastSyncedAt() == null ? null : snapshot.lastSyncedAt().toString(),
                snapshot.cacheExpiresAt() == null ? null : snapshot.cacheExpiresAt().toString(),
                snapshot.summary(),
                unavailableReason == null ? snapshot.current() : null,
                unavailableReason == null ? snapshot.daily() : List.of()
        );
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize weather cache", exception);
        }
    }

    private record Result(
            boolean available,
            String unavailableReason,
            String locationName,
            String zoneId,
            String lastFailureCode,
            String lastSyncedAt,
            String cacheExpiresAt,
            String summary,
            WorkdayWeatherService.CurrentSnapshot current,
            List<WorkdayWeatherService.DailySnapshot> daily
    ) { }
}
