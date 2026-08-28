package com.kj.stackchan.api;

import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.kj.stackchan.calendar.ICloudCalendarService;
import com.kj.stackchan.weather.WorkdayWeatherService;
import com.kj.stackchan.workday.WorkdaySettingsService;
import com.kj.stackchan.workday.WorkdayRuntimeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/workday", produces = MediaType.APPLICATION_JSON_VALUE)
public class WorkdaySettingsController {

    private final WorkdaySettingsService settingsService;
    private final WorkdayRuntimeService runtimeService;
    private final ICloudCalendarService calendarService;
    private final WorkdayWeatherService weatherService;

    public WorkdaySettingsController(
            WorkdaySettingsService settingsService,
            WorkdayRuntimeService runtimeService,
            ICloudCalendarService calendarService,
            WorkdayWeatherService weatherService
    ) {
        this.settingsService = settingsService;
        this.runtimeService = runtimeService;
        this.calendarService = calendarService;
        this.weatherService = weatherService;
    }

    @GetMapping("/{deviceId}/settings")
    public WorkdaySettingsService.WorkdaySettingsSnapshot get(@PathVariable UUID deviceId) {
        return settingsService.get(deviceId);
    }

    @PutMapping(path = "/{deviceId}/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkdaySettingsService.WorkdaySettingsSnapshot save(
            @PathVariable UUID deviceId,
            @Valid @RequestBody WorkdaySettingsRequest request
    ) {
        WorkdaySettingsService.WorkdaySettingsSnapshot saved = settingsService.save(deviceId, request.toCommand());
        weatherService.settingsChanged(saved);
        return saved;
    }

    @GetMapping("/{deviceId}/runtime")
    public WorkdayRuntimeService.WorkdayRuntimeSnapshot runtime(@PathVariable UUID deviceId) {
        return runtimeService.get(deviceId);
    }

    @GetMapping("/{deviceId}/metrics")
    public WorkdayRuntimeService.WorkdayMetricsSnapshot metrics(
            @PathVariable UUID deviceId,
            @RequestParam(defaultValue = "90") @Min(1) @Max(90) int days
    ) {
        return runtimeService.metrics(deviceId, days);
    }

    @GetMapping("/{deviceId}/calendar")
    public ICloudCalendarService.ConnectionSnapshot calendar(@PathVariable UUID deviceId) {
        return calendarService.get(deviceId);
    }

    @PostMapping(path = "/{deviceId}/calendar/connection:test", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ICloudCalendarService.ConnectionTestSnapshot testCalendarConnection(
            @PathVariable UUID deviceId,
            @Valid @RequestBody ICloudCalendarConnectionRequest request
    ) {
        return calendarService.test(deviceId, request.toCommand());
    }

    @PutMapping(path = "/{deviceId}/calendar/connection", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ICloudCalendarService.ConnectionSnapshot connectCalendar(
            @PathVariable UUID deviceId,
            @Valid @RequestBody ICloudCalendarConnectionRequest request
    ) {
        return calendarService.connect(deviceId, request.toCommand());
    }

    @PutMapping(path = "/{deviceId}/calendar/calendars", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ICloudCalendarService.ConnectionSnapshot updateAllowedCalendars(
            @PathVariable UUID deviceId,
            @Valid @RequestBody AllowedCalendarsRequest request
    ) {
        return calendarService.updateAllowed(deviceId, request.allowedCalendarIds());
    }

    @PostMapping("/{deviceId}/calendar/sync")
    public ICloudCalendarService.ConnectionSnapshot syncCalendar(@PathVariable UUID deviceId) {
        return calendarService.sync(deviceId);
    }

    @DeleteMapping("/{deviceId}/calendar/connection")
    public void disconnectCalendar(@PathVariable UUID deviceId) {
        calendarService.disconnect(deviceId);
    }

    @GetMapping("/{deviceId}/weather")
    public WorkdayWeatherService.WeatherSnapshot weather(@PathVariable UUID deviceId) {
        return weatherService.get(deviceId);
    }

    @PostMapping(path = "/{deviceId}/weather/connection:test", consumes = MediaType.APPLICATION_JSON_VALUE)
    public WorkdayWeatherService.WeatherTestSnapshot testWeather(
            @PathVariable UUID deviceId,
            @Valid @RequestBody WeatherLocationRequest request
    ) {
        return weatherService.test(deviceId, request.toCommand());
    }

    @PostMapping("/{deviceId}/weather/sync")
    public WorkdayWeatherService.WeatherSnapshot syncWeather(@PathVariable UUID deviceId) {
        return weatherService.sync(deviceId);
    }

    public record WorkdaySettingsRequest(
            boolean enabled,
            @Min(1) @Max(127) int workDaysMask,
            @NotNull LocalTime workStart,
            @NotNull LocalTime workEnd,
            @Min(15) @Max(180) int focusMinutes,
            @Min(5) @Max(60) int restMinutes,
            @Min(1) @Max(60) int absenceSuspendMinutes,
            @Min(5) @Max(240) int rearrivalMinutes,
            @NotNull @Size(max = 120) String locationName,
            Double latitude,
            Double longitude,
            @NotBlank @Size(max = 80) String zoneId
    ) {
        WorkdaySettingsService.UpdateWorkdaySettingsCommand toCommand() {
            return new WorkdaySettingsService.UpdateWorkdaySettingsCommand(
                    enabled, workDaysMask, workStart, workEnd, focusMinutes, restMinutes,
                    absenceSuspendMinutes, rearrivalMinutes, locationName, latitude, longitude, zoneId
            );
        }
    }

    public record ICloudCalendarConnectionRequest(
            @Size(max = 320) String accountEmail,
            @Size(max = 256) String appSpecificPassword
    ) {
        ICloudCalendarService.ConnectionCommand toCommand() {
            return new ICloudCalendarService.ConnectionCommand(accountEmail, appSpecificPassword);
        }
    }

    public record AllowedCalendarsRequest(@NotNull Set<UUID> allowedCalendarIds) { }

    public record WeatherLocationRequest(
            @NotNull @Size(max = 120) String locationName,
            @NotNull @Min(-90) @Max(90) Double latitude,
            @NotNull @Min(-180) @Max(180) Double longitude,
            @NotBlank @Size(max = 80) String zoneId
    ) {
        WorkdayWeatherService.LocationCommand toCommand() {
            return new WorkdayWeatherService.LocationCommand(locationName, latitude, longitude, zoneId);
        }
    }
}
