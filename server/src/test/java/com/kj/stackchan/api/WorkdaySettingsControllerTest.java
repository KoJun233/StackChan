package com.kj.stackchan.api;

import java.time.LocalTime;
import java.time.Instant;

import com.kj.stackchan.calendar.ICloudCalendarService;
import com.kj.stackchan.weather.WorkdayWeatherService;
import com.kj.stackchan.workday.WorkdayCompanionService;
import com.kj.stackchan.workday.WorkdayPilotService;
import com.kj.stackchan.workday.WorkdaySettingsService;
import com.kj.stackchan.workday.WorkdayRuntimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class WorkdaySettingsControllerTest {

    @Mock private WorkdaySettingsService settingsService;
    @Mock private WorkdayRuntimeService runtimeService;
    @Mock private ICloudCalendarService calendarService;
    @Mock private WorkdayWeatherService weatherService;
    @Mock private WorkdayCompanionService companionService;
    @Mock private WorkdayPilotService pilotService;

    @Test
    void mapsBoundedWorkdaySettings() {
        var command = new WorkdaySettingsController.WorkdaySettingsRequest(
                true, 31, LocalTime.of(9, 0), LocalTime.of(18, 0),
                50, 10, 10, 45, "上海", 31.2304, 121.4737, "Asia/Shanghai"
        ).toCommand();

        assertThat(command.enabled()).isTrue();
        assertThat(command.workDaysMask()).isEqualTo(31);
        assertThat(command.focusMinutes()).isEqualTo(50);
        assertThat(command.locationName()).isEqualTo("上海");
    }

    @Test
    void delegatesRuntimeAndBoundedMetricsReads() {
        var controller = new WorkdaySettingsController(settingsService, runtimeService, calendarService, weatherService);
        var deviceId = java.util.UUID.randomUUID();

        controller.runtime(deviceId);
        controller.metrics(deviceId, 90);

        org.mockito.Mockito.verify(runtimeService).get(deviceId);
        org.mockito.Mockito.verify(runtimeService).metrics(deviceId, 90);
    }

    @Test
    void delegatesCalendarConnectionOperations() {
        var controller = new WorkdaySettingsController(settingsService, runtimeService, calendarService, weatherService);
        var deviceId = java.util.UUID.randomUUID();
        var calendarId = java.util.UUID.randomUUID();
        var connection = new WorkdaySettingsController.ICloudCalendarConnectionRequest("me@icloud.com", "secret");
        var from = Instant.parse("2026-09-01T00:00:00Z");
        var to = Instant.parse("2026-09-08T00:00:00Z");

        controller.calendar(deviceId);
        controller.testCalendarConnection(deviceId, connection);
        controller.connectCalendar(deviceId, connection);
        controller.updateAllowedCalendars(
                deviceId,
                new WorkdaySettingsController.AllowedCalendarsRequest(java.util.Set.of(calendarId))
        );
        controller.syncCalendar(deviceId);
        controller.calendarEvents(deviceId, from, to);
        controller.disconnectCalendar(deviceId);

        org.mockito.Mockito.verify(calendarService).get(deviceId);
        org.mockito.Mockito.verify(calendarService).test(deviceId, connection.toCommand());
        org.mockito.Mockito.verify(calendarService).connect(deviceId, connection.toCommand());
        org.mockito.Mockito.verify(calendarService).updateAllowed(deviceId, java.util.Set.of(calendarId));
        org.mockito.Mockito.verify(calendarService).sync(deviceId);
        org.mockito.Mockito.verify(calendarService).cachedEvents(deviceId, from, to);
        org.mockito.Mockito.verify(calendarService).disconnect(deviceId);
    }

    @Test
    void delegatesWeatherTestSyncAndStatus() {
        var controller = new WorkdaySettingsController(settingsService, runtimeService, calendarService, weatherService);
        var deviceId = java.util.UUID.randomUUID();
        var location = new WorkdaySettingsController.WeatherLocationRequest(
                "上海办公室", 31.2304, 121.4737, "Asia/Shanghai"
        );

        controller.weather(deviceId);
        controller.testWeather(deviceId, location);
        controller.syncWeather(deviceId);

        org.mockito.Mockito.verify(weatherService).get(deviceId);
        org.mockito.Mockito.verify(weatherService).test(deviceId, location.toCommand());
        org.mockito.Mockito.verify(weatherService).sync(deviceId);
    }

    @Test
    void delegatesExplicitPilotLifecycleAndFalseTriggerCorrection() {
        var controller = new WorkdaySettingsController(
                settingsService, runtimeService, companionService, pilotService,
                calendarService, weatherService
        );
        var deviceId = java.util.UUID.randomUUID();

        controller.pilot(deviceId);
        controller.startPilot(deviceId);
        controller.restartPilot(deviceId);
        controller.markFalseTrigger(deviceId);
        controller.undoFalseTrigger(deviceId);

        org.mockito.Mockito.verify(pilotService).get(deviceId);
        org.mockito.Mockito.verify(pilotService).start(deviceId);
        org.mockito.Mockito.verify(pilotService).restart(deviceId);
        org.mockito.Mockito.verify(pilotService).markFalseTrigger(deviceId);
        org.mockito.Mockito.verify(pilotService).undoFalseTrigger(deviceId);
    }
}
