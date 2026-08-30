package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkdayPilotService {

    private static final int PILOT_DAYS = 14;
    private static final int ACTIVE_WORKDAY_TARGET = 8;
    private static final int FALSE_TRIGGER_WEEKLY_LIMIT = 2;

    private final WorkdayPilotObservationRepository observationRepository;
    private final WorkdayDailyMetricRepository metricRepository;
    private final WorkdaySettingsService settingsService;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public WorkdayPilotService(
            WorkdayPilotObservationRepository observationRepository,
            WorkdayDailyMetricRepository metricRepository,
            WorkdaySettingsService settingsService,
            DeviceRepository deviceRepository,
            Clock clock
    ) {
        this.observationRepository = observationRepository;
        this.metricRepository = metricRepository;
        this.settingsService = settingsService;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional
    public PilotReportSnapshot start(UUID deviceId) {
        validateDevice(deviceId);
        WorkdayPilotObservationEntity observation = observationRepository.findById(deviceId)
                .orElseGet(() -> newObservation(deviceId));
        return report(observation);
    }

    @Transactional
    public PilotReportSnapshot restart(UUID deviceId) {
        validateDevice(deviceId);
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        ZoneId zone = ZoneId.of(settings.zoneId());
        WorkdayPilotObservationEntity observation = observationRepository.findById(deviceId)
                .orElseGet(() -> new WorkdayPilotObservationEntity(
                        deviceId, localToday(zone), zone.getId(), settings.workDaysMask(), clock.instant()
                ));
        observation.reset(localToday(zone), zone.getId(), settings.workDaysMask(), clock.instant());
        observationRepository.save(observation);
        return report(observation);
    }

    @Transactional(readOnly = true)
    public PilotReportSnapshot get(UUID deviceId) {
        validateDevice(deviceId);
        return observationRepository.findById(deviceId)
                .map(this::report)
                .orElseGet(() -> PilotReportSnapshot.notStarted(deviceId));
    }

    @Transactional
    public PilotReportSnapshot markFalseTrigger(UUID deviceId) {
        validateDevice(deviceId);
        WorkdayPilotObservationEntity observation = observation(deviceId);
        LocalDate today = activeObservationDate(observation);
        metric(deviceId, today).markFalseTrigger(clock.instant());
        return report(observation);
    }

    @Transactional
    public PilotReportSnapshot undoFalseTrigger(UUID deviceId) {
        validateDevice(deviceId);
        WorkdayPilotObservationEntity observation = observation(deviceId);
        LocalDate today = activeObservationDate(observation);
        metricRepository.findForUpdate(deviceId, today)
                .ifPresent(metric -> metric.undoFalseTrigger(clock.instant()));
        return report(observation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBriefDegradation(
            UUID deviceId,
            LocalDate workDate,
            String calendarFailureCode,
            String weatherFailureCode
    ) {
        if (workDate == null || calendarFailureCode == null && weatherFailureCode == null) return;
        WorkdayDailyMetricEntity metric = metric(deviceId, workDate);
        if (calendarFailureCode != null) metric.recordCalendarFailure(calendarFailureCode, clock.instant());
        if (weatherFailureCode != null) metric.recordWeatherFailure(weatherFailureCode, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeviceRestart(UUID deviceId) {
        WorkdayPilotObservationEntity observation = observationRepository.findById(deviceId).orElse(null);
        metric(deviceId, localToday(metricZone(deviceId, observation)))
                .recordDeviceRestart(clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMotionObservation(UUID deviceId, String failureCode, long count) {
        if (failureCode == null || count <= 0) return;
        WorkdayPilotObservationEntity observation = observationRepository.findById(deviceId).orElse(null);
        WorkdayDailyMetricEntity metric = metric(
                deviceId, localToday(metricZone(deviceId, observation))
        );
        if (isMotionRejection(failureCode)) {
            metric.recordMotionRejected(count, clock.instant());
        } else if (isMotionFailure(failureCode)) {
            metric.recordMotionFailed(count, clock.instant());
        }
    }

    private WorkdayPilotObservationEntity newObservation(UUID deviceId) {
        WorkdaySettingsService.WorkdaySettingsSnapshot settings = settingsService.resolve(deviceId);
        ZoneId zone = ZoneId.of(settings.zoneId());
        return observationRepository.save(new WorkdayPilotObservationEntity(
                deviceId, localToday(zone), zone.getId(), settings.workDaysMask(), clock.instant()
        ));
    }

    private WorkdayPilotObservationEntity observation(UUID deviceId) {
        return observationRepository.findById(deviceId)
                .orElseThrow(() -> new InvalidWorkdayStateException("Workday pilot has not started"));
    }

    private WorkdayDailyMetricEntity metric(UUID deviceId, LocalDate workDate) {
        return metricRepository.findForUpdate(deviceId, workDate)
                .orElseGet(() -> metricRepository.save(new WorkdayDailyMetricEntity(
                        UUID.randomUUID(), deviceId, workDate, clock.instant()
                )));
    }

    private LocalDate activeObservationDate(WorkdayPilotObservationEntity observation) {
        LocalDate today = localToday(ZoneId.of(observation.getZoneId()));
        if (today.isBefore(observation.getStartedOn()) || today.isAfter(observation.getEndsOn())) {
            throw new InvalidWorkdayStateException("Workday pilot window is not active");
        }
        return today;
    }

    private PilotReportSnapshot report(WorkdayPilotObservationEntity observation) {
        LocalDate today = localToday(ZoneId.of(observation.getZoneId()));
        LocalDate observedTo = today.isBefore(observation.getEndsOn()) ? today : observation.getEndsOn();
        int elapsedDays = today.isBefore(observation.getStartedOn()) ? 0
                : (int) Math.min(PILOT_DAYS,
                ChronoUnit.DAYS.between(observation.getStartedOn(), today) + 1);
        List<WorkdayDailyMetricEntity> daily = metricRepository
                .findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(
                        observation.getDeviceId(), observation.getStartedOn(), observation.getEndsOn()
                );
        long activeWorkdays = daily.stream().filter(metric ->
                metric.getSessionStartCount() > 0
                        && WorkdaySettingsService.includesDay(
                        observation.getWorkDaysMask(), metric.getWorkDate().getDayOfWeek()
                )).count();
        int maximumDailyBriefs = daily.stream().mapToInt(this::briefCount).max().orElse(0);
        int firstWeekFalseTriggers = sumFalseTriggers(
                daily, observation.getStartedOn(), observation.getStartedOn().plusDays(6)
        );
        int secondWeekFalseTriggers = sumFalseTriggers(
                daily, observation.getStartedOn().plusDays(7), observation.getEndsOn()
        );
        int motionRejected = daily.stream().mapToInt(WorkdayDailyMetricEntity::getMotionRejectedCount).sum();
        int motionFailed = daily.stream().mapToInt(WorkdayDailyMetricEntity::getMotionFailedCount).sum();
        int deviceRestarts = daily.stream().mapToInt(WorkdayDailyMetricEntity::getDeviceRestartCount).sum();
        int calendarFailures = daily.stream().mapToInt(WorkdayDailyMetricEntity::getCalendarFailureCount).sum();
        int weatherFailures = daily.stream().mapToInt(WorkdayDailyMetricEntity::getWeatherFailureCount).sum();
        boolean attributionComplete = daily.stream().allMatch(metric ->
                metric.getCalendarFailureCount() == 0 || metric.getCalendarLastFailureCode() != null)
                && daily.stream().allMatch(metric ->
                metric.getWeatherFailureCount() == 0 || metric.getWeatherLastFailureCode() != null);
        int plannedWorkdays = countPlannedWorkdays(
                observation.getStartedOn(), observation.getEndsOn(), observation.getWorkDaysMask()
        );
        int elapsedPlannedWorkdays = elapsedDays == 0 ? 0 : countPlannedWorkdays(
                observation.getStartedOn(), observedTo, observation.getWorkDaysMask()
        );
        boolean windowComplete = today.isAfter(observation.getEndsOn());
        boolean activeDaysPass = activeWorkdays >= ACTIVE_WORKDAY_TARGET;
        boolean briefFrequencyPass = maximumDailyBriefs <= 1;
        boolean falseTriggerPass = firstWeekFalseTriggers <= FALSE_TRIGGER_WEEKLY_LIMIT
                && secondWeekFalseTriggers <= FALSE_TRIGGER_WEEKLY_LIMIT;
        boolean motionSafetyPass = motionFailed == 0;
        boolean stabilityPass = deviceRestarts == 0;
        PilotStatus status = !windowComplete ? PilotStatus.COLLECTING
                : activeDaysPass && briefFrequencyPass && falseTriggerPass
                && attributionComplete && motionSafetyPass && stabilityPass
                ? PilotStatus.PASS : PilotStatus.FAIL;
        return new PilotReportSnapshot(
                observation.getDeviceId(), true, status, observation.getStartedOn(),
                observation.getEndsOn(), observation.getZoneId(), observation.getWorkDaysMask(),
                elapsedDays, plannedWorkdays, elapsedPlannedWorkdays, activeWorkdays,
                ACTIVE_WORKDAY_TARGET, maximumDailyBriefs, firstWeekFalseTriggers,
                secondWeekFalseTriggers, FALSE_TRIGGER_WEEKLY_LIMIT, calendarFailures,
                weatherFailures, attributionComplete, motionRejected, motionFailed,
                deviceRestarts, windowComplete, activeDaysPass, briefFrequencyPass,
                falseTriggerPass, motionSafetyPass, stabilityPass, observation.getUpdatedAt()
        );
    }

    private int briefCount(WorkdayDailyMetricEntity metric) {
        return metric.getBriefSuccessCount() + metric.getBriefPartialCount()
                + metric.getBriefFailedCount() + metric.getBriefCancelledCount();
    }

    private int sumFalseTriggers(List<WorkdayDailyMetricEntity> daily, LocalDate from, LocalDate to) {
        return daily.stream().filter(metric -> !metric.getWorkDate().isBefore(from)
                        && !metric.getWorkDate().isAfter(to))
                .mapToInt(WorkdayDailyMetricEntity::getFalseTriggerCount).sum();
    }

    private int countPlannedWorkdays(LocalDate from, LocalDate to, int mask) {
        if (to.isBefore(from)) return 0;
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (WorkdaySettingsService.includesDay(mask, date.getDayOfWeek())) count++;
        }
        return count;
    }

    private boolean isMotionRejection(String code) {
        return switch (code) {
            case "CAPABILITY_MISSING", "NOT_CALIBRATED", "ADMIN_DISABLED",
                    "AUDIO_BUSY", "OFFLINE", "UPDATING",
                    "DEVICE_ERROR", "BUSY", "SOFT_LIMIT" -> true;
            default -> false;
        };
    }

    private boolean isMotionFailure(String code) {
        return switch (code) {
            case "TIMEOUT", "FEEDBACK_FAULT", "HARDWARE_FAILURE" -> true;
            default -> false;
        };
    }

    private LocalDate localToday(ZoneId zone) {
        return clock.instant().atZone(zone).toLocalDate();
    }

    private ZoneId metricZone(UUID deviceId, WorkdayPilotObservationEntity observation) {
        return observation != null
                ? ZoneId.of(observation.getZoneId())
                : ZoneId.of(settingsService.resolve(deviceId).zoneId());
    }

    private void validateDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidWorkdaySettingsException("Workday device is invalid");
        }
    }

    public enum PilotStatus { NOT_STARTED, COLLECTING, PASS, FAIL }

    public record PilotReportSnapshot(
            UUID deviceId,
            boolean started,
            PilotStatus status,
            LocalDate startedOn,
            LocalDate endsOn,
            String zoneId,
            Integer workDaysMask,
            int elapsedDays,
            int plannedWorkdays,
            int elapsedPlannedWorkdays,
            long activeWorkdays,
            int activeWorkdayTarget,
            int maximumDailyBriefs,
            int firstWeekFalseTriggers,
            int secondWeekFalseTriggers,
            int falseTriggerWeeklyLimit,
            int calendarFailureCount,
            int weatherFailureCount,
            boolean externalFailureAttributionComplete,
            int motionRejectedCount,
            int motionFailedCount,
            int deviceRestartCount,
            boolean windowComplete,
            boolean activeDaysPass,
            boolean briefFrequencyPass,
            boolean falseTriggerPass,
            boolean motionSafetyPass,
            boolean stabilityPass,
            java.time.Instant updatedAt
    ) {
        static PilotReportSnapshot notStarted(UUID deviceId) {
            return new PilotReportSnapshot(
                    deviceId, false, PilotStatus.NOT_STARTED, null, null, null, null,
                    0, 0, 0, 0, ACTIVE_WORKDAY_TARGET, 0, 0, 0,
                    FALSE_TRIGGER_WEEKLY_LIMIT, 0, 0, true, 0, 0, 0,
                    false, false, true, true, true, true, null
            );
        }
    }
}
