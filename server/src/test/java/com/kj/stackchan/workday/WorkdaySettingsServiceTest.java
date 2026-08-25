package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdaySettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T01:00:00Z");

    @Mock private DeviceWorkdaySettingsRepository repository;
    @Mock private DeviceRepository deviceRepository;

    @Test
    void createsSafeDisabledDefaults() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(repository.findById(deviceId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var settings = service().get(deviceId);

        assertThat(settings.enabled()).isFalse();
        assertThat(settings.workDaysMask()).isEqualTo(31);
        assertThat(settings.focusMinutes()).isEqualTo(50);
        assertThat(settings.restMinutes()).isEqualTo(10);
        assertThat(settings.absenceSuspendMinutes()).isEqualTo(10);
        assertThat(settings.rearrivalMinutes()).isEqualTo(45);
        assertThat(settings.weatherLocationConfigured()).isFalse();
    }

    @Test
    void evaluatesCrossMidnightWindowAgainstItsStartingDay() {
        var settings = new WorkdaySettingsService.WorkdaySettingsSnapshot(
                UUID.randomUUID(), true, 1,
                LocalTime.of(22, 0), LocalTime.of(2, 0), 50, 10, 10, 45,
                "上海", 31.2304, 121.4737, "Asia/Shanghai", NOW
        );

        assertThat(service().isInsideWorkWindow(settings, Instant.parse("2026-08-24T16:30:00Z"))).isTrue();
        assertThat(service().isInsideWorkWindow(settings, Instant.parse("2026-08-25T02:30:00Z"))).isFalse();
    }

    @Test
    void rejectsPartialLocationAndUnsafeRearrivalThreshold() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepository.existsById(deviceId)).thenReturn(true);

        var command = new WorkdaySettingsService.UpdateWorkdaySettingsCommand(
                true, 31, LocalTime.of(9, 0), LocalTime.of(18, 0), 50, 10,
                20, 10, "上海", 31.2304, null, "Asia/Shanghai"
        );

        assertThatThrownBy(() -> service().save(deviceId, command))
                .isInstanceOf(InvalidWorkdaySettingsException.class);
    }

    private WorkdaySettingsService service() {
        return new WorkdaySettingsService(repository, deviceRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
