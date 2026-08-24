package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.kj.stackchan.device.DeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkdayRuntimeServiceTest {

    private static final UUID DEVICE_ID = UUID.fromString("915c071f-84d2-453a-bb62-a5714902f256");
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 24);

    @Mock private DeviceWorkdayRuntimeRepository runtimeRepository;
    @Mock private WorkdayBriefAttemptRepository briefRepository;
    @Mock private WorkdayDailyMetricRepository metricRepository;
    @Mock private WorkdaySettingsService settingsService;
    @Mock private DeviceRepository deviceRepository;

    private final AtomicReference<DeviceWorkdayRuntimeEntity> runtime = new AtomicReference<>();
    private final AtomicReference<WorkdayBriefAttemptEntity> brief = new AtomicReference<>();
    private final Map<LocalDate, WorkdayDailyMetricEntity> metrics = new HashMap<>();

    @BeforeEach
    void configureRepositories() {
        when(deviceRepository.existsById(DEVICE_ID)).thenReturn(true);
        when(settingsService.resolve(DEVICE_ID)).thenReturn(settings());
        when(settingsService.workDate(eq(settings()), any())).thenReturn(Optional.of(WORK_DATE));
        when(runtimeRepository.findForUpdate(DEVICE_ID)).thenAnswer(invocation -> Optional.ofNullable(runtime.get()));
        when(runtimeRepository.save(any())).thenAnswer(invocation -> {
            DeviceWorkdayRuntimeEntity saved = invocation.getArgument(0);
            runtime.set(saved);
            return saved;
        });
        when(metricRepository.findForUpdate(eq(DEVICE_ID), any())).thenAnswer(invocation ->
                Optional.ofNullable(metrics.get(invocation.getArgument(1))));
        when(metricRepository.save(any())).thenAnswer(invocation -> {
            WorkdayDailyMetricEntity saved = invocation.getArgument(0);
            metrics.put(saved.getWorkDate(), saved);
            return saved;
        });
        when(briefRepository.findByDeviceIdAndWorkDate(eq(DEVICE_ID), any())).thenAnswer(invocation ->
                Optional.ofNullable(brief.get()));
        when(briefRepository.findForUpdate(eq(DEVICE_ID), any())).thenAnswer(invocation ->
                Optional.ofNullable(brief.get()));
        when(briefRepository.claim(any(), eq(DEVICE_ID), eq(WORK_DATE), any())).thenAnswer(invocation -> {
            if (brief.get() != null) {
                return 0;
            }
            brief.set(new WorkdayBriefAttemptEntity(
                    invocation.getArgument(0), DEVICE_ID, WORK_DATE, invocation.getArgument(3)
            ));
            return 1;
        });
    }

    @Test
    void restoresPersistedFocusAndPromptsAfterTheConfiguredThreshold() {
        var started = serviceAt("2026-08-24T01:00:00Z").start(DEVICE_ID, true);

        var restored = serviceAt("2026-08-24T01:16:00Z").tick(DEVICE_ID);

        assertThat(started.state()).isEqualTo(WorkdayRuntimeState.ACTIVE_PRESENT);
        assertThat(restored.state()).isEqualTo(WorkdayRuntimeState.REST_PROMPTED);
        assertThat(restored.focusSeconds()).isEqualTo(16 * 60);
        assertThat(metrics.get(WORK_DATE).getSessionStartCount()).isEqualTo(1);
        assertThat(metrics.get(WORK_DATE).getFocusSeconds()).isEqualTo(16 * 60);
    }

    @Test
    void stopsAccruingImmediatelyWhenAbsentAndTransitionsAfterTheGracePeriod() {
        serviceAt("2026-08-24T01:00:00Z").start(DEVICE_ID, true);
        serviceAt("2026-08-24T01:05:00Z").updatePresence(DEVICE_ID, false);

        var beforeThreshold = serviceAt("2026-08-24T01:14:59Z").tick(DEVICE_ID);
        var afterThreshold = serviceAt("2026-08-24T01:15:00Z").tick(DEVICE_ID);

        assertThat(beforeThreshold.state()).isEqualTo(WorkdayRuntimeState.ACTIVE_PRESENT);
        assertThat(afterThreshold.state()).isEqualTo(WorkdayRuntimeState.ACTIVE_ABSENT);
        assertThat(afterThreshold.focusSeconds()).isEqualTo(5 * 60);
    }

    @Test
    void claimsTheFirstBriefOnceAndCountsItsTerminalResultOnce() {
        serviceAt("2026-08-24T01:00:00Z").start(DEVICE_ID, true);

        var first = serviceAt("2026-08-24T01:01:00Z").claimBrief(DEVICE_ID);
        var replay = serviceAt("2026-08-24T01:02:00Z").claimBrief(DEVICE_ID);
        serviceAt("2026-08-24T01:03:00Z").completeBrief(DEVICE_ID, WORK_DATE, WorkdayBriefStatus.PARTIAL);
        serviceAt("2026-08-24T01:04:00Z").completeBrief(DEVICE_ID, WORK_DATE, WorkdayBriefStatus.PARTIAL);

        assertThat(first.claimed()).isTrue();
        assertThat(replay.claimed()).isFalse();
        assertThat(brief.get().getStatus()).isEqualTo(WorkdayBriefStatus.PARTIAL);
        assertThat(metrics.get(WORK_DATE).getBriefPartialCount()).isEqualTo(1);
    }

    private WorkdayRuntimeService serviceAt(String instant) {
        return new WorkdayRuntimeService(
                runtimeRepository, briefRepository, metricRepository, settingsService, deviceRepository,
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings() {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                DEVICE_ID, true, 127, LocalTime.of(9, 0), LocalTime.of(18, 0),
                15, 10, 10, 45, "上海", 31.2304, 121.4737,
                "Asia/Shanghai", Instant.parse("2026-08-24T00:00:00Z")
        );
    }
}
