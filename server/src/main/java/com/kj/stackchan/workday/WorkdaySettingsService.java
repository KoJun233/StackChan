package com.kj.stackchan.workday;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kj.stackchan.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkdaySettingsService {

    private final DeviceWorkdaySettingsRepository repository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public WorkdaySettingsService(
            DeviceWorkdaySettingsRepository repository,
            DeviceRepository deviceRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional
    public WorkdaySettingsSnapshot get(UUID deviceId) {
        validateDevice(deviceId);
        return snapshot(repository.findById(deviceId)
                .orElseGet(() -> repository.save(new DeviceWorkdaySettingsEntity(deviceId, clock.instant()))));
    }

    @Transactional
    public WorkdaySettingsSnapshot save(UUID deviceId, UpdateWorkdaySettingsCommand command) {
        validateDevice(deviceId);
        ZoneId zoneId = validateCommand(command);
        DeviceWorkdaySettingsEntity entity = repository.findById(deviceId)
                .orElseGet(() -> new DeviceWorkdaySettingsEntity(deviceId, clock.instant()));
        entity.update(
                command.enabled(), command.workDaysMask(), command.workStart(), command.workEnd(),
                command.focusMinutes(), command.restMinutes(), command.absenceSuspendMinutes(),
                command.rearrivalMinutes(), command.locationName().trim(), command.latitude(), command.longitude(),
                zoneId.getId(), clock.instant()
        );
        return snapshot(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public WorkdaySettingsSnapshot resolve(UUID deviceId) {
        return repository.findById(deviceId)
                .map(this::snapshot)
                .orElseGet(() -> snapshot(new DeviceWorkdaySettingsEntity(deviceId, clock.instant())));
    }

    public boolean isInsideWorkWindow(WorkdaySettingsSnapshot settings, Instant instant) {
        return workDate(settings, instant).isPresent();
    }

    public Optional<LocalDate> workDate(WorkdaySettingsSnapshot settings, Instant instant) {
        if (!settings.enabled()) {
            return Optional.empty();
        }
        ZonedDateTime local = instant.atZone(ZoneId.of(settings.zoneId()));
        LocalTime time = local.toLocalTime();
        boolean crossesMidnight = settings.workStart().isAfter(settings.workEnd());
        boolean inside = crossesMidnight
                ? !time.isBefore(settings.workStart()) || time.isBefore(settings.workEnd())
                : !time.isBefore(settings.workStart()) && time.isBefore(settings.workEnd());
        if (!inside) {
            return Optional.empty();
        }
        LocalDate scheduleDate = crossesMidnight && time.isBefore(settings.workEnd())
                ? local.toLocalDate().minusDays(1)
                : local.toLocalDate();
        return includesDay(settings.workDaysMask(), scheduleDate.getDayOfWeek())
                ? Optional.of(scheduleDate)
                : Optional.empty();
    }

    static boolean includesDay(int mask, DayOfWeek day) {
        return (mask & (1 << (day.getValue() - 1))) != 0;
    }

    private ZoneId validateCommand(UpdateWorkdaySettingsCommand command) {
        if (command == null || command.workDaysMask() < 1 || command.workDaysMask() > 127
                || command.workStart() == null || command.workEnd() == null
                || command.workStart().equals(command.workEnd())
                || command.focusMinutes() < 15 || command.focusMinutes() > 180
                || command.restMinutes() < 5 || command.restMinutes() > 60
                || command.absenceSuspendMinutes() < 1 || command.absenceSuspendMinutes() > 60
                || command.rearrivalMinutes() < command.absenceSuspendMinutes()
                || command.rearrivalMinutes() > 240
                || command.locationName() == null || command.locationName().trim().length() > 120
                || (command.latitude() == null) != (command.longitude() == null)
                || command.latitude() != null && (command.latitude() < -90 || command.latitude() > 90)
                || command.longitude() != null && (command.longitude() < -180 || command.longitude() > 180)) {
            throw new InvalidWorkdaySettingsException("Workday settings are invalid");
        }
        try {
            return ZoneId.of(command.zoneId() == null ? "" : command.zoneId().trim());
        } catch (ZoneRulesException | IllegalArgumentException exception) {
            throw new InvalidWorkdaySettingsException("Workday timezone is invalid", exception);
        }
    }

    private void validateDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidWorkdaySettingsException("Workday settings device is invalid");
        }
    }

    private WorkdaySettingsSnapshot snapshot(DeviceWorkdaySettingsEntity entity) {
        return new WorkdaySettingsSnapshot(
                entity.getDeviceId(), entity.isEnabled(), entity.getWorkDaysMask(), entity.getWorkStart(),
                entity.getWorkEnd(), entity.getFocusMinutes(), entity.getRestMinutes(),
                entity.getAbsenceSuspendMinutes(), entity.getRearrivalMinutes(), entity.getLocationName(),
                entity.getLatitude(), entity.getLongitude(), entity.getZoneId(), entity.getUpdatedAt()
        );
    }

    public record UpdateWorkdaySettingsCommand(
            boolean enabled,
            int workDaysMask,
            LocalTime workStart,
            LocalTime workEnd,
            int focusMinutes,
            int restMinutes,
            int absenceSuspendMinutes,
            int rearrivalMinutes,
            String locationName,
            Double latitude,
            Double longitude,
            String zoneId
    ) {
    }

    public record WorkdaySettingsSnapshot(
            UUID deviceId,
            boolean enabled,
            int workDaysMask,
            LocalTime workStart,
            LocalTime workEnd,
            int focusMinutes,
            int restMinutes,
            int absenceSuspendMinutes,
            int rearrivalMinutes,
            String locationName,
            Double latitude,
            Double longitude,
            String zoneId,
            Instant updatedAt
    ) {
        @JsonProperty("weatherLocationConfigured")
        public boolean weatherLocationConfigured() {
            return latitude != null && longitude != null;
        }
    }
}
