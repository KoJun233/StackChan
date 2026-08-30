package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkdayPilotServiceTest {

    private static final UUID DEVICE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-30T04:00:00Z");
    private static final LocalDate STARTED_ON = LocalDate.of(2026, 8, 16);

    @Mock private WorkdayPilotObservationRepository observationRepository;
    @Mock private WorkdayDailyMetricRepository metricRepository;
    @Mock private WorkdaySettingsService settingsService;
    @Mock private DeviceRepository deviceRepository;

    private WorkdayPilotService service;

    @BeforeEach
    void setUp() {
        service = new WorkdayPilotService(
                observationRepository, metricRepository, settingsService, deviceRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(deviceRepository.existsById(DEVICE_ID)).thenReturn(true);
    }

    @Test
    void startsAnExplicitFourteenDayObservationWithSettingsSnapshot() {
        when(observationRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(settingsService.resolve(DEVICE_ID)).thenReturn(settings());
        when(observationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(metricRepository.findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(
                any(), any(), any())).thenReturn(List.of());

        WorkdayPilotService.PilotReportSnapshot report = service.start(DEVICE_ID);

        assertThat(report.started()).isTrue();
        assertThat(report.status()).isEqualTo(WorkdayPilotService.PilotStatus.COLLECTING);
        assertThat(report.startedOn()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(report.endsOn()).isEqualTo(LocalDate.of(2026, 9, 12));
        assertThat(report.zoneId()).isEqualTo("Asia/Shanghai");
        assertThat(report.workDaysMask()).isEqualTo(31);
    }

    @Test
    void passesCompletedWindowWhenAllFrozenGatesAreMet() {
        WorkdayPilotObservationEntity observation = observation();
        when(observationRepository.findById(DEVICE_ID)).thenReturn(Optional.of(observation));
        when(metricRepository.findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(
                DEVICE_ID, STARTED_ON, STARTED_ON.plusDays(13)))
                .thenReturn(activeMetrics(8));

        WorkdayPilotService.PilotReportSnapshot report = service.get(DEVICE_ID);

        assertThat(report.status()).isEqualTo(WorkdayPilotService.PilotStatus.PASS);
        assertThat(report.windowComplete()).isTrue();
        assertThat(report.activeWorkdays()).isEqualTo(8);
        assertThat(report.activeDaysPass()).isTrue();
        assertThat(report.motionSafetyPass()).isTrue();
        assertThat(report.stabilityPass()).isTrue();
    }

    @Test
    void failsCompletedWindowForExcessFalseTriggersMotionFailureOrRestart() {
        WorkdayPilotObservationEntity observation = observation();
        List<WorkdayDailyMetricEntity> metrics = activeMetrics(8);
        WorkdayDailyMetricEntity firstDay = metrics.get(0);
        firstDay.markFalseTrigger(NOW);
        firstDay.markFalseTrigger(NOW);
        firstDay.markFalseTrigger(NOW);
        firstDay.recordMotionFailed(1, NOW);
        firstDay.recordDeviceRestart(NOW);
        when(observationRepository.findById(DEVICE_ID)).thenReturn(Optional.of(observation));
        when(metricRepository.findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(
                DEVICE_ID, STARTED_ON, STARTED_ON.plusDays(13))).thenReturn(metrics);

        WorkdayPilotService.PilotReportSnapshot report = service.get(DEVICE_ID);

        assertThat(report.status()).isEqualTo(WorkdayPilotService.PilotStatus.FAIL);
        assertThat(report.falseTriggerPass()).isFalse();
        assertThat(report.motionSafetyPass()).isFalse();
        assertThat(report.stabilityPass()).isFalse();
    }

    @Test
    void keepsCollectingUntilTheFourteenthDayHasFullyEnded() {
        LocalDate startedOn = LocalDate.of(2026, 8, 17);
        WorkdayPilotObservationEntity observation = new WorkdayPilotObservationEntity(
                DEVICE_ID, startedOn, "Asia/Shanghai", 31,
                NOW.minusSeconds(13 * 86400L)
        );
        when(observationRepository.findById(DEVICE_ID)).thenReturn(Optional.of(observation));
        when(metricRepository.findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(
                DEVICE_ID, startedOn, startedOn.plusDays(13))).thenReturn(List.of());

        WorkdayPilotService.PilotReportSnapshot report = service.get(DEVICE_ID);

        assertThat(report.elapsedDays()).isEqualTo(14);
        assertThat(report.windowComplete()).isFalse();
        assertThat(report.status()).isEqualTo(WorkdayPilotService.PilotStatus.COLLECTING);
    }

    @Test
    void rejectsFalseTriggerChangesAfterTheObservationWindowEnds() {
        WorkdayPilotObservationEntity ended = new WorkdayPilotObservationEntity(
                DEVICE_ID, STARTED_ON.minusDays(1), "Asia/Shanghai", 31,
                NOW.minusSeconds(15 * 86400L)
        );
        when(observationRepository.findById(DEVICE_ID)).thenReturn(Optional.of(ended));

        assertThatThrownBy(() -> service.markFalseTrigger(DEVICE_ID))
                .isInstanceOf(InvalidWorkdayStateException.class)
                .hasMessageContaining("not active");
    }

    private List<WorkdayDailyMetricEntity> activeMetrics(int count) {
        List<WorkdayDailyMetricEntity> metrics = new ArrayList<>();
        LocalDate date = STARTED_ON;
        while (metrics.size() < count) {
            if (!WorkdaySettingsService.includesDay(31, date.getDayOfWeek())) {
                date = date.plusDays(1);
                continue;
            }
            WorkdayDailyMetricEntity metric = new WorkdayDailyMetricEntity(
                    UUID.randomUUID(), DEVICE_ID, date, NOW
            );
            metric.sessionStarted(NOW);
            metric.briefCompleted(WorkdayBriefStatus.SUCCESS, NOW);
            metrics.add(metric);
            date = date.plusDays(1);
        }
        return metrics;
    }

    private WorkdayPilotObservationEntity observation() {
        return new WorkdayPilotObservationEntity(
                DEVICE_ID, STARTED_ON, "Asia/Shanghai", 31, NOW.minusSeconds(13 * 86400L)
        );
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings() {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                DEVICE_ID, true, 31, LocalTime.of(9, 0), LocalTime.of(18, 0),
                50, 10, 10, 45, "上海", 31.2, 121.5, "Asia/Shanghai", NOW
        );
    }
}
