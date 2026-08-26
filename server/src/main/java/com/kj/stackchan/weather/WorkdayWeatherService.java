package com.kj.stackchan.weather;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkdayWeatherService {

    static final Duration CACHE_TTL = Duration.ofHours(1);

    private final WorkdayWeatherStateRepository stateRepository;
    private final WorkdayWeatherDailyForecastRepository dailyRepository;
    private final DeviceRepository deviceRepository;
    private final WorkdaySettingsService settingsService;
    private final OpenMeteoClient openMeteoClient;
    private final Clock clock;

    public WorkdayWeatherService(
            WorkdayWeatherStateRepository stateRepository,
            WorkdayWeatherDailyForecastRepository dailyRepository,
            DeviceRepository deviceRepository,
            WorkdaySettingsService settingsService,
            OpenMeteoClient openMeteoClient,
            Clock clock
    ) {
        this.stateRepository = stateRepository;
        this.dailyRepository = dailyRepository;
        this.deviceRepository = deviceRepository;
        this.settingsService = settingsService;
        this.openMeteoClient = openMeteoClient;
        this.clock = clock;
    }

    @Transactional
    public WeatherSnapshot get(UUID deviceId) {
        validateDevice(deviceId);
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        Optional<Location> location = location(settings);
        if (location.isEmpty()) {
            stateRepository.deleteById(deviceId);
            return WeatherSnapshot.notConfigured(deviceId, settings.zoneId());
        }
        WorkdayWeatherStateEntity state = stateRepository.findById(deviceId).orElse(null);
        if (state == null) {
            return WeatherSnapshot.notSynced(deviceId, location.get());
        }
        if (!matches(state, location.get())) {
            stateRepository.delete(state);
            return WeatherSnapshot.notSynced(deviceId, location.get());
        }
        return snapshot(state, dailyRepository.findAllByDeviceIdOrderByForecastDateAsc(deviceId), clock.instant());
    }

    public WeatherTestSnapshot test(UUID deviceId, LocationCommand command) {
        validateDevice(deviceId);
        Location location = validateLocation(command);
        OpenMeteoClient.Forecast forecast = openMeteoClient.fetch(
                location.latitude(), location.longitude(), location.zoneId()
        );
        return new WeatherTestSnapshot(
                true,
                location.locationName(),
                location.zoneId().getId(),
                forecast.current().observedAt(),
                summary(location.locationName(), forecast.current(), forecast.daily().getFirst()),
                toCurrent(forecast.current()),
                forecast.daily().stream().map(this::toDaily).toList()
        );
    }

    @Transactional(noRollbackFor = WorkdayWeatherUnavailableException.class)
    public WeatherSnapshot sync(UUID deviceId) {
        validateDevice(deviceId);
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        Location location = location(settings)
                .orElseThrow(() -> new InvalidWorkdayWeatherException("Weather location is not configured"));
        Instant now = clock.instant();
        WorkdayWeatherStateEntity state = stateRepository.findById(deviceId)
                .orElseGet(() -> new WorkdayWeatherStateEntity(
                        deviceId,
                        location.locationName(),
                        location.latitude(),
                        location.longitude(),
                        location.zoneId().getId(),
                        now
                ));
        try {
            OpenMeteoClient.Forecast forecast = openMeteoClient.fetch(
                    location.latitude(), location.longitude(), location.zoneId()
            );
            state.synced(
                    location.locationName(), location.latitude(), location.longitude(), location.zoneId().getId(),
                    forecast.current(), now, now.plus(CACHE_TTL)
            );
            stateRepository.saveAndFlush(state);
            dailyRepository.deleteAllByDeviceId(deviceId);
            dailyRepository.flush();
            dailyRepository.saveAll(forecast.daily().stream()
                    .map(item -> new WorkdayWeatherDailyForecastEntity(UUID.randomUUID(), deviceId, item))
                    .toList());
            return snapshot(state, dailyRepository.findAllByDeviceIdOrderByForecastDateAsc(deviceId), now);
        } catch (WorkdayWeatherUnavailableException exception) {
            boolean changedLocation = !matches(state, location);
            state.failed(
                    location.locationName(), location.latitude(), location.longitude(), location.zoneId().getId(),
                    exception.getFailureCode(), now
            );
            stateRepository.save(state);
            if (changedLocation) {
                dailyRepository.deleteAllByDeviceId(deviceId);
            }
            throw exception;
        }
    }

    @Transactional
    public void settingsChanged(WorkdaySettingsService.WorkdaySettingsSnapshot settings) {
        Optional<Location> location = location(settings);
        stateRepository.findById(settings.deviceId()).ifPresent(state -> {
            if (location.isEmpty() || !matches(state, location.get())) {
                stateRepository.delete(state);
            }
        });
    }

    boolean matches(WorkdayWeatherStateEntity state, Location location) {
        return state.matches(
                location.locationName(), location.latitude(), location.longitude(), location.zoneId().getId()
        );
    }

    Optional<Location> location(WorkdaySettingsService.WorkdaySettingsSnapshot settings) {
        if (settings.latitude() == null || settings.longitude() == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(
                settings.locationName().trim(), settings.latitude(), settings.longitude(), ZoneId.of(settings.zoneId())
        ));
    }

    private Location validateLocation(LocationCommand command) {
        if (command == null || command.locationName() == null
                || command.locationName().trim().length() > 120
                || command.latitude() == null || command.longitude() == null
                || !Double.isFinite(command.latitude()) || !Double.isFinite(command.longitude())
                || command.latitude() < -90 || command.latitude() > 90
                || command.longitude() < -180 || command.longitude() > 180) {
            throw new InvalidWorkdayWeatherException("Weather location is invalid");
        }
        try {
            return new Location(
                    command.locationName().trim(),
                    command.latitude(),
                    command.longitude(),
                    ZoneId.of(command.zoneId() == null ? "" : command.zoneId().trim())
            );
        } catch (ZoneRulesException | IllegalArgumentException exception) {
            throw new InvalidWorkdayWeatherException("Weather timezone is invalid", exception);
        }
    }

    private WeatherSnapshot snapshot(
            WorkdayWeatherStateEntity state,
            List<WorkdayWeatherDailyForecastEntity> daily,
            Instant now
    ) {
        boolean fresh = state.getLastSyncedAt() != null
                && state.getCacheExpiresAt() != null
                && state.getCacheExpiresAt().isAfter(now)
                && state.getCurrentObservedAt() != null
                && !daily.isEmpty();
        CurrentSnapshot current = state.getCurrentObservedAt() == null ? null : new CurrentSnapshot(
                state.getCurrentObservedAt(),
                round(state.getCurrentTemperature()),
                round(state.getCurrentApparentTemperature()),
                round(state.getCurrentPrecipitation()),
                state.getCurrentWeatherCode(),
                describe(state.getCurrentWeatherCode())
        );
        List<DailySnapshot> dailySnapshots = daily.stream().map(item -> new DailySnapshot(
                item.getForecastDate(),
                item.getWeatherCode(),
                describe(item.getWeatherCode()),
                round(item.getTemperatureMax()),
                round(item.getTemperatureMin()),
                round(item.getApparentTemperatureMax()),
                round(item.getApparentTemperatureMin()),
                item.getPrecipitationProbabilityMax(),
                round(item.getPrecipitationSum())
        )).toList();
        String summary = fresh && current != null
                ? summary(state.getLocationName(), current, dailySnapshots.getFirst())
                : null;
        return new WeatherSnapshot(
                state.getDeviceId(), true, fresh, state.getLocationName(), state.getZoneId(),
                state.getStatus(), state.getLastFailureCode(), state.getLastAttemptedAt(),
                state.getLastSyncedAt(), state.getCacheExpiresAt(), summary, current, dailySnapshots
        );
    }

    private CurrentSnapshot toCurrent(OpenMeteoClient.CurrentWeather current) {
        return new CurrentSnapshot(
                current.observedAt(), round(current.temperature()), round(current.apparentTemperature()),
                round(current.precipitation()), current.weatherCode(), describe(current.weatherCode())
        );
    }

    private DailySnapshot toDaily(OpenMeteoClient.DailyForecast daily) {
        return new DailySnapshot(
                daily.date(), daily.weatherCode(), describe(daily.weatherCode()),
                round(daily.temperatureMax()), round(daily.temperatureMin()),
                round(daily.apparentTemperatureMax()), round(daily.apparentTemperatureMin()),
                daily.precipitationProbabilityMax(), round(daily.precipitationSum())
        );
    }

    private String summary(
            String locationName,
            OpenMeteoClient.CurrentWeather current,
            OpenMeteoClient.DailyForecast today
    ) {
        return summary(locationName, toCurrent(current), toDaily(today));
    }

    private String summary(String locationName, CurrentSnapshot current, DailySnapshot today) {
        String prefix = locationName.isBlank() ? "固定地点" : locationName;
        String text = "%s当前%s，%s℃，体感%s℃；今天%s至%s℃".formatted(
                prefix,
                current.description(),
                display(current.temperature()),
                display(current.apparentTemperature()),
                display(today.temperatureMin()),
                display(today.temperatureMax())
        );
        if (today.precipitationProbabilityMax() >= 30 || today.precipitationSum() >= 0.1) {
            return text + "，最高降水概率" + today.precipitationProbabilityMax()
                    + "%、预计降水" + display(today.precipitationSum()) + "毫米。";
        }
        return text + "。";
    }

    static String describe(int code) {
        return switch (code) {
            case 0 -> "晴";
            case 1 -> "大致晴朗";
            case 2 -> "多云";
            case 3 -> "阴";
            case 45, 48 -> "有雾";
            case 51, 53, 55 -> "毛毛雨";
            case 56, 57 -> "冻毛毛雨";
            case 61, 63, 65 -> "下雨";
            case 66, 67 -> "冻雨";
            case 71, 73, 75 -> "下雪";
            case 77 -> "有雪粒";
            case 80, 81, 82 -> "阵雨";
            case 85, 86 -> "阵雪";
            case 95 -> "雷暴";
            case 96, 99 -> "雷暴伴冰雹";
            default -> "天气状况未知";
        };
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String display(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private void validateDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidWorkdayWeatherException("Weather device is invalid");
        }
    }

    public record LocationCommand(String locationName, Double latitude, Double longitude, String zoneId) { }

    record Location(String locationName, double latitude, double longitude, ZoneId zoneId) { }

    public record WeatherTestSnapshot(
            boolean ok,
            String locationName,
            String zoneId,
            Instant observedAt,
            String summary,
            CurrentSnapshot current,
            List<DailySnapshot> daily
    ) { }

    public record WeatherSnapshot(
            UUID deviceId,
            boolean configured,
            boolean fresh,
            String locationName,
            String zoneId,
            WorkdayWeatherStatus status,
            WorkdayWeatherFailureCode lastFailureCode,
            Instant lastAttemptedAt,
            Instant lastSyncedAt,
            Instant cacheExpiresAt,
            String summary,
            CurrentSnapshot current,
            List<DailySnapshot> daily
    ) {
        static WeatherSnapshot notConfigured(UUID deviceId, String zoneId) {
            return new WeatherSnapshot(
                    deviceId, false, false, "", zoneId, null, null,
                    null, null, null, null, null, List.of()
            );
        }

        static WeatherSnapshot notSynced(UUID deviceId, Location location) {
            return new WeatherSnapshot(
                    deviceId, true, false, location.locationName(), location.zoneId().getId(), null, null,
                    null, null, null, null, null, List.of()
            );
        }
    }

    public record CurrentSnapshot(
            Instant observedAt,
            double temperature,
            double apparentTemperature,
            double precipitation,
            int weatherCode,
            String description
    ) { }

    public record DailySnapshot(
            java.time.LocalDate date,
            int weatherCode,
            String description,
            double temperatureMax,
            double temperatureMin,
            double apparentTemperatureMax,
            double apparentTemperatureMin,
            int precipitationProbabilityMax,
            double precipitationSum
    ) { }
}
