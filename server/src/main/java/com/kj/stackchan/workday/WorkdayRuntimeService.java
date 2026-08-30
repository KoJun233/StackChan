package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkdayRuntimeService {

    private static final int MAX_METRIC_DAYS = 90;
    private static final Duration REST_SNOOZE = Duration.ofMinutes(10);

    private final DeviceWorkdayRuntimeRepository runtimeRepository;
    private final WorkdayBriefAttemptRepository briefRepository;
    private final WorkdayDailyMetricRepository metricRepository;
    private final WorkdaySettingsService settingsService;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public WorkdayRuntimeService(
            DeviceWorkdayRuntimeRepository runtimeRepository,
            WorkdayBriefAttemptRepository briefRepository,
            WorkdayDailyMetricRepository metricRepository,
            WorkdaySettingsService settingsService,
            DeviceRepository deviceRepository,
            Clock clock
    ) {
        this.runtimeRepository = runtimeRepository;
        this.briefRepository = briefRepository;
        this.metricRepository = metricRepository;
        this.settingsService = settingsService;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional
    public WorkdayRuntimeSnapshot get(UUID deviceId) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        Optional<DeviceWorkdayRuntimeEntity> existing = runtimeRepository.findForUpdate(deviceId);
        if (existing.isEmpty()) {
            return offSnapshot(deviceId, settings, now);
        }
        DeviceWorkdayRuntimeEntity runtime = existing.get();
        advance(runtime, settings, now);
        return snapshot(runtime, settings);
    }

    @Transactional
    public WorkdayRuntimeSnapshot start(UUID deviceId, boolean present) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        LocalDate workDate = settingsService.workDate(settings, now)
                .orElseThrow(() -> new InvalidWorkdayStateException("Workday mode is disabled or outside its window"));
        purgeExpired(workDate);
        DeviceWorkdayRuntimeEntity runtime = runtimeRepository.findForUpdate(deviceId)
                .orElseGet(() -> new DeviceWorkdayRuntimeEntity(deviceId, now));
        if (runtime.getState() != WorkdayRuntimeState.OFF && workDate.equals(runtime.getWorkDate())) {
            advance(runtime, settings, now);
            return snapshot(runtime, settings);
        }
        if (runtime.getState() != WorkdayRuntimeState.OFF && runtime.getWorkDate() != null) {
            metric(deviceId, runtime.getWorkDate(), now).sessionEnded(now);
        }
        runtime.start(workDate, present, now);
        metric(deviceId, workDate, now).sessionStarted(now);
        runtimeRepository.save(runtime);
        return snapshot(runtime, settings);
    }

    @Transactional
    public WorkdayRuntimeSnapshot updatePresence(UUID deviceId, boolean present) {
        return updatePresenceWithOutcome(deviceId, present).runtime();
    }

    @Transactional
    public PresenceUpdateSnapshot updatePresenceWithOutcome(UUID deviceId, boolean present) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        DeviceWorkdayRuntimeEntity runtime = activeRuntime(deviceId);
        advance(runtime, settings, now);
        if (runtime.getState() == WorkdayRuntimeState.OFF) {
            throw new InvalidWorkdayStateException("Workday mode is not active");
        }
        if (runtime.isPresent() == present) {
            return new PresenceUpdateSnapshot(snapshot(runtime, settings), false);
        }
        boolean rearrival = present && runtime.getState() == WorkdayRuntimeState.ACTIVE_ABSENT
                && runtime.getAbsenceStartedAt() != null
                && !now.isBefore(runtime.getAbsenceStartedAt().plus(
                Duration.ofMinutes(settings.rearrivalMinutes())));
        accrueFocus(runtime, now);
        runtime.markPresent(present, now);
        if (present && (runtime.getState() == WorkdayRuntimeState.STARTING
                || runtime.getState() == WorkdayRuntimeState.ACTIVE_ABSENT)) {
            runtime.transition(WorkdayRuntimeState.ACTIVE_PRESENT, now);
        }
        runtimeRepository.save(runtime);
        return new PresenceUpdateSnapshot(snapshot(runtime, settings), rearrival);
    }

    @Transactional(readOnly = true)
    public List<UUID> activeDeviceIds() {
        return runtimeRepository.findAllByStateNot(WorkdayRuntimeState.OFF).stream()
                .map(DeviceWorkdayRuntimeEntity::getDeviceId)
                .toList();
    }

    @Transactional
    public WorkdayRuntimeSnapshot tick(UUID deviceId) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        DeviceWorkdayRuntimeEntity runtime = activeRuntime(deviceId);
        advance(runtime, settings, now);
        runtimeRepository.save(runtime);
        return snapshot(runtime, settings);
    }

    @Transactional
    public WorkdayRuntimeSnapshot respondToRest(UUID deviceId, WorkdayRestAction action) {
        validateDevice(deviceId);
        if (action == null) {
            throw new InvalidWorkdayStateException("Rest action is required");
        }
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        DeviceWorkdayRuntimeEntity runtime = activeRuntime(deviceId);
        advance(runtime, settings, now);
        if (runtime.getState() != WorkdayRuntimeState.REST_PROMPTED || runtime.getWorkDate() == null) {
            throw new InvalidWorkdayStateException("No rest prompt is awaiting a response");
        }
        WorkdayDailyMetricEntity metric = metric(deviceId, runtime.getWorkDate(), now);
        switch (action) {
            case START_REST -> {
                runtime.beginRest(now.plus(Duration.ofMinutes(settings.restMinutes())), now);
                metric.restStarted(now);
            }
            case SNOOZE -> {
                runtime.snooze(now.plus(REST_SNOOZE), now);
                metric.restSnoozed(now);
            }
            case SKIP_FOR_DAY -> {
                runtime.skipForDay(now);
                metric.restSkipped(now);
            }
        }
        runtimeRepository.save(runtime);
        return snapshot(runtime, settings);
    }

    @Transactional
    public WorkdayRuntimeSnapshot stop(UUID deviceId) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        DeviceWorkdayRuntimeEntity runtime = runtimeRepository.findForUpdate(deviceId)
                .orElseGet(() -> new DeviceWorkdayRuntimeEntity(deviceId, now));
        if (runtime.getState() != WorkdayRuntimeState.OFF && runtime.getWorkDate() != null) {
            accrueFocus(runtime, now);
            cancelPendingBrief(runtime, now);
            metric(deviceId, runtime.getWorkDate(), now).sessionEnded(now);
            runtime.stop(now);
            runtimeRepository.save(runtime);
        }
        return snapshot(runtime, settings);
    }

    @Transactional
    public BriefClaimSnapshot claimBrief(UUID deviceId) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        DeviceWorkdayRuntimeEntity runtime = activeRuntime(deviceId);
        if (runtime.getWorkDate() == null) {
            throw new InvalidWorkdayStateException("Workday mode is not active");
        }
        purgeExpired(runtime.getWorkDate());
        UUID attemptId = UUID.randomUUID();
        boolean claimed = briefRepository.claim(attemptId, deviceId, runtime.getWorkDate(), now) == 1;
        WorkdayBriefAttemptEntity attempt = briefRepository
                .findByDeviceIdAndWorkDate(deviceId, runtime.getWorkDate())
                .orElseThrow(() -> new IllegalStateException("Claimed brief attempt was not found"));
        return briefSnapshot(attempt, claimed);
    }

    @Transactional
    public BriefClaimSnapshot completeBrief(UUID deviceId, LocalDate workDate, WorkdayBriefStatus status) {
        validateDevice(deviceId);
        if (workDate == null || status == null || status == WorkdayBriefStatus.PENDING) {
            throw new InvalidWorkdayStateException("Completed brief result is invalid");
        }
        Instant now = clock.instant();
        WorkdayBriefAttemptEntity attempt = briefRepository.findForUpdate(deviceId, workDate)
                .orElseThrow(() -> new InvalidWorkdayStateException("Brief was not claimed"));
        if (attempt.complete(status, now)) {
            metric(deviceId, workDate, now).briefCompleted(status, now);
        }
        return briefSnapshot(attempt, false);
    }

    @Transactional
    public WorkdayMetricsSnapshot metrics(UUID deviceId, int days) {
        validateDevice(deviceId);
        if (days < 1 || days > MAX_METRIC_DAYS) {
            throw new InvalidWorkdaySettingsException("Workday metric range is invalid");
        }
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        LocalDate to = clock.instant().atZone(ZoneId.of(settings.zoneId())).toLocalDate();
        LocalDate from = to.minusDays(days - 1L);
        purgeExpired(to);
        List<DailyMetricSnapshot> daily = metricRepository
                .findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(deviceId, from, to).stream()
                .map(this::dailySnapshot)
                .toList();
        return new WorkdayMetricsSnapshot(deviceId, from, to, days, summarize(daily), daily);
    }

    private void advance(
            DeviceWorkdayRuntimeEntity runtime,
            WorkdaySettingsService.WorkdaySettingsSnapshot settings,
            Instant now
    ) {
        if (runtime.getState() == WorkdayRuntimeState.OFF) {
            return;
        }
        Optional<LocalDate> currentWorkDate = settingsService.workDate(settings, now);
        if (currentWorkDate.isEmpty() || !currentWorkDate.get().equals(runtime.getWorkDate())) {
            accrueFocus(runtime, now);
            cancelPendingBrief(runtime, now);
            metric(runtime.getDeviceId(), runtime.getWorkDate(), now).sessionEnded(now);
            runtime.stop(now);
            runtimeRepository.save(runtime);
            return;
        }
        if (runtime.getState() == WorkdayRuntimeState.RESTING
                && runtime.getRestUntil() != null && !now.isBefore(runtime.getRestUntil())) {
            runtime.finishRest(now);
        }
        if (!runtime.isPresent() && runtime.getAbsenceStartedAt() != null
                && runtime.getState() == WorkdayRuntimeState.ACTIVE_PRESENT
                && !now.isBefore(runtime.getAbsenceStartedAt().plus(Duration.ofMinutes(settings.absenceSuspendMinutes())))) {
            runtime.transition(WorkdayRuntimeState.ACTIVE_ABSENT, now);
        }
        accrueFocus(runtime, now);
        boolean snoozeElapsed = runtime.getSnoozedUntil() == null || !now.isBefore(runtime.getSnoozedUntil());
        if (runtime.getState() == WorkdayRuntimeState.ACTIVE_PRESENT && snoozeElapsed
                && runtime.getFocusSeconds() >= settings.focusMinutes() * 60L) {
            runtime.transition(WorkdayRuntimeState.REST_PROMPTED, now);
        }
        runtimeRepository.save(runtime);
    }

    private void accrueFocus(DeviceWorkdayRuntimeEntity runtime, Instant now) {
        if (!runtime.isPresent() || runtime.getFocusUpdatedAt() == null
                || runtime.getWorkDate() == null
                || runtime.getState() != WorkdayRuntimeState.ACTIVE_PRESENT
                && runtime.getState() != WorkdayRuntimeState.SKIPPED_FOR_DAY) {
            return;
        }
        long seconds = Math.max(0, Duration.between(runtime.getFocusUpdatedAt(), now).getSeconds());
        if (seconds == 0) {
            return;
        }
        runtime.addFocusSeconds(seconds, now);
        metric(runtime.getDeviceId(), runtime.getWorkDate(), now).addFocusSeconds(seconds, now);
    }

    private DeviceWorkdayRuntimeEntity activeRuntime(UUID deviceId) {
        return runtimeRepository.findForUpdate(deviceId)
                .filter(runtime -> runtime.getState() != WorkdayRuntimeState.OFF)
                .orElseThrow(() -> new InvalidWorkdayStateException("Workday mode is not active"));
    }

    private WorkdayDailyMetricEntity metric(UUID deviceId, LocalDate workDate, Instant now) {
        return metricRepository.findForUpdate(deviceId, workDate)
                .orElseGet(() -> metricRepository.save(
                        new WorkdayDailyMetricEntity(UUID.randomUUID(), deviceId, workDate, now)
                ));
    }

    private void purgeExpired(LocalDate today) {
        LocalDate cutoff = today.minusDays(MAX_METRIC_DAYS - 1L);
        metricRepository.deleteByWorkDateBefore(cutoff);
        briefRepository.deleteByWorkDateBefore(cutoff);
    }

    private void cancelPendingBrief(DeviceWorkdayRuntimeEntity runtime, Instant now) {
        briefRepository.findForUpdate(runtime.getDeviceId(), runtime.getWorkDate())
                .filter(attempt -> attempt.getStatus() == WorkdayBriefStatus.PENDING)
                .ifPresent(attempt -> {
                    if (attempt.complete(WorkdayBriefStatus.CANCELLED, now)) {
                        metric(runtime.getDeviceId(), runtime.getWorkDate(), now)
                                .briefCompleted(WorkdayBriefStatus.CANCELLED, now);
                    }
                });
    }

    private void validateDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidWorkdaySettingsException("Workday device is invalid");
        }
    }

    private WorkdayRuntimeSnapshot offSnapshot(
            UUID deviceId,
            WorkdaySettingsService.WorkdaySettingsSnapshot settings,
            Instant now
    ) {
        return new WorkdayRuntimeSnapshot(
                deviceId, WorkdayRuntimeState.OFF, null, false, 0,
                settings.focusMinutes() * 60L, null, null, null, null,
                null, null, now
        );
    }

    private WorkdayRuntimeSnapshot snapshot(
            DeviceWorkdayRuntimeEntity runtime,
            WorkdaySettingsService.WorkdaySettingsSnapshot settings
    ) {
        WorkdayBriefStatus briefStatus = runtime.getWorkDate() == null ? null : briefRepository
                .findByDeviceIdAndWorkDate(runtime.getDeviceId(), runtime.getWorkDate())
                .map(WorkdayBriefAttemptEntity::getStatus)
                .orElse(null);
        return new WorkdayRuntimeSnapshot(
                runtime.getDeviceId(), runtime.getState(), runtime.getWorkDate(), runtime.isPresent(),
                runtime.getFocusSeconds(), settings.focusMinutes() * 60L, runtime.getStartedAt(),
                runtime.getStateChangedAt(), runtime.getAbsenceStartedAt(), runtime.getRestUntil(),
                runtime.getSnoozedUntil(), briefStatus, runtime.getUpdatedAt()
        );
    }

    private BriefClaimSnapshot briefSnapshot(WorkdayBriefAttemptEntity attempt, boolean claimed) {
        return new BriefClaimSnapshot(
                attempt.getId(), attempt.getDeviceId(), attempt.getWorkDate(), attempt.getStatus(), claimed,
                attempt.getClaimedAt(), attempt.getCompletedAt(), attempt.getUpdatedAt()
        );
    }

    private DailyMetricSnapshot dailySnapshot(WorkdayDailyMetricEntity metric) {
        return new DailyMetricSnapshot(
                metric.getWorkDate(), metric.getSessionStartCount(), metric.getSessionEndCount(),
                metric.getFocusSeconds(), metric.getBriefSuccessCount(), metric.getBriefPartialCount(),
                metric.getBriefFailedCount(), metric.getBriefCancelledCount(), metric.getRestStartedCount(),
                metric.getRestSnoozedCount(), metric.getRestSkippedCount()
        );
    }

    private MetricSummarySnapshot summarize(List<DailyMetricSnapshot> daily) {
        return new MetricSummarySnapshot(
                daily.stream().filter(item -> item.sessionStartCount() > 0).count(),
                daily.stream().mapToInt(DailyMetricSnapshot::sessionStartCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::sessionEndCount).sum(),
                daily.stream().mapToLong(DailyMetricSnapshot::focusSeconds).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::briefSuccessCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::briefPartialCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::briefFailedCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::briefCancelledCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::restStartedCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::restSnoozedCount).sum(),
                daily.stream().mapToInt(DailyMetricSnapshot::restSkippedCount).sum()
        );
    }

    public record WorkdayRuntimeSnapshot(
            UUID deviceId,
            WorkdayRuntimeState state,
            LocalDate workDate,
            boolean present,
            long focusSeconds,
            long focusTargetSeconds,
            Instant startedAt,
            Instant stateChangedAt,
            Instant absenceStartedAt,
            Instant restUntil,
            Instant snoozedUntil,
            WorkdayBriefStatus briefStatus,
            Instant updatedAt
    ) { }

    public record BriefClaimSnapshot(
            UUID id,
            UUID deviceId,
            LocalDate workDate,
            WorkdayBriefStatus status,
            boolean claimed,
            Instant claimedAt,
            Instant completedAt,
            Instant updatedAt
    ) { }

    public record PresenceUpdateSnapshot(WorkdayRuntimeSnapshot runtime, boolean rearrival) { }

    public record DailyMetricSnapshot(
            LocalDate workDate,
            int sessionStartCount,
            int sessionEndCount,
            long focusSeconds,
            int briefSuccessCount,
            int briefPartialCount,
            int briefFailedCount,
            int briefCancelledCount,
            int restStartedCount,
            int restSnoozedCount,
            int restSkippedCount
    ) { }

    public record MetricSummarySnapshot(
            long activeWorkdays,
            int sessionStartCount,
            int sessionEndCount,
            long focusSeconds,
            int briefSuccessCount,
            int briefPartialCount,
            int briefFailedCount,
            int briefCancelledCount,
            int restStartedCount,
            int restSnoozedCount,
            int restSkippedCount
    ) { }

    public record WorkdayMetricsSnapshot(
            UUID deviceId,
            LocalDate from,
            LocalDate to,
            int days,
            MetricSummarySnapshot summary,
            List<DailyMetricSnapshot> daily
    ) { }
}
