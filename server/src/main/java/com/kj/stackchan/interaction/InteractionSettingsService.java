package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionSettingsService {

    private final DeviceInteractionSettingsRepository repository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public InteractionSettingsService(
            DeviceInteractionSettingsRepository repository,
            DeviceRepository deviceRepository,
            Clock clock
    ) {
        this.repository = repository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional
    public InteractionSettingsSnapshot get(UUID deviceId) {
        validateDevice(deviceId);
        return snapshot(repository.findById(deviceId)
                .orElseGet(() -> repository.save(new DeviceInteractionSettingsEntity(deviceId, clock.instant()))));
    }

    @Transactional
    public InteractionSettingsSnapshot save(UUID deviceId, UpdateInteractionSettingsCommand command) {
        validateDevice(deviceId);
        ZoneId zoneId = parseZone(command.zoneId());
        validateCommand(command);
        DeviceInteractionSettingsEntity settings = repository.findById(deviceId)
                .orElseGet(() -> new DeviceInteractionSettingsEntity(deviceId, clock.instant()));
        settings.update(
                command.volumePercent(), command.nightMode(), command.continuousConversationEnabled(),
                command.followUpWindowSeconds(), command.dndEnabled(),
                command.dndStart(), command.dndEnd(), zoneId.getId(), command.missedReminderPolicy(),
                command.missedSnoozeMinutes(), command.proactiveEnabled(), command.proactiveStart(),
                command.proactiveEnd(), command.proactiveMinIntervalMinutes(), command.proactiveDailyLimit(),
                command.proactiveContent().trim(), command.proactivePersonalizationEnabled(), clock.instant()
        );
        return snapshot(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public InteractionSettingsSnapshot resolve(UUID deviceId) {
        return repository.findById(deviceId)
                .map(this::snapshot)
                .orElseGet(() -> snapshot(new DeviceInteractionSettingsEntity(deviceId, clock.instant())));
    }

    @Transactional
    public InteractionSettingsSnapshot setVolume(UUID deviceId, int volumePercent) {
        validateDevice(deviceId);
        if (volumePercent < 0 || volumePercent > 100) {
            throw new InvalidInteractionSettingsException("Interaction volume is invalid");
        }
        DeviceInteractionSettingsEntity settings = repository.findById(deviceId)
                .orElseGet(() -> new DeviceInteractionSettingsEntity(deviceId, clock.instant()));
        settings.setVolume(volumePercent, clock.instant());
        return snapshot(repository.save(settings));
    }

    @Transactional
    public InteractionSettingsSnapshot setTemporaryDndUntil(UUID deviceId, Instant until) {
        validateDevice(deviceId);
        Instant now = clock.instant();
        if (until == null || !until.isAfter(now) || until.isAfter(now.plus(Duration.ofHours(24)))) {
            throw new InvalidInteractionSettingsException("Temporary DND duration is invalid");
        }
        DeviceInteractionSettingsEntity settings = repository.findById(deviceId)
                .orElseGet(() -> new DeviceInteractionSettingsEntity(deviceId, now));
        settings.setTemporaryDndUntil(until, now);
        return snapshot(repository.save(settings));
    }

    @Transactional(readOnly = true)
    public List<InteractionSettingsSnapshot> proactiveCandidates() {
        return repository.findAll().stream()
                .filter(DeviceInteractionSettingsEntity::isProactiveEnabled)
                .map(this::snapshot)
                .toList();
    }

    @Transactional
    public boolean recordProactiveIfEligible(UUID deviceId, Instant now) {
        DeviceInteractionSettingsEntity entity = repository.findLockedByDeviceId(deviceId).orElse(null);
        if (entity == null || !isProactiveEligible(snapshot(entity), now)) {
            return false;
        }
        LocalDate date = now.atZone(ZoneId.of(entity.getZoneId())).toLocalDate();
        entity.recordProactive(date, now);
        return true;
    }

    public boolean isDnd(InteractionSettingsSnapshot settings, Instant instant) {
        if (settings.temporaryDndUntil() != null && settings.temporaryDndUntil().isAfter(instant)) {
            return true;
        }
        if (!settings.dndEnabled()) {
            return false;
        }
        LocalTime time = instant.atZone(ZoneId.of(settings.zoneId())).toLocalTime();
        return inWindow(time, settings.dndStart(), settings.dndEnd());
    }

    public Instant nextDndEnd(InteractionSettingsSnapshot settings, Instant instant) {
        Instant temporaryEnd = settings.temporaryDndUntil() != null
                && settings.temporaryDndUntil().isAfter(instant) ? settings.temporaryDndUntil() : null;
        ZoneId zone = ZoneId.of(settings.zoneId());
        ZonedDateTime now = instant.atZone(zone);
        boolean recurringActive = settings.dndEnabled()
                && inWindow(now.toLocalTime(), settings.dndStart(), settings.dndEnd());
        if (!recurringActive && temporaryEnd != null) {
            return temporaryEnd;
        }
        LocalDate endDate = now.toLocalDate();
        if (settings.dndStart().isAfter(settings.dndEnd()) && !now.toLocalTime().isBefore(settings.dndStart())) {
            endDate = endDate.plusDays(1);
        }
        LocalDateTime end = LocalDateTime.of(endDate, settings.dndEnd());
        ZonedDateTime zonedEnd = end.atZone(zone);
        if (!zonedEnd.toInstant().isAfter(instant)) {
            zonedEnd = end.plusDays(1).atZone(zone);
        }
        Instant recurringEnd = zonedEnd.toInstant();
        return temporaryEnd != null && temporaryEnd.isAfter(recurringEnd) ? temporaryEnd : recurringEnd;
    }

    public boolean isProactiveEligible(InteractionSettingsSnapshot settings, Instant now) {
        if (!settings.proactiveEnabled() || isDnd(settings, now)) {
            return false;
        }
        ZonedDateTime localNow = now.atZone(ZoneId.of(settings.zoneId()));
        if (!inWindow(localNow.toLocalTime(), settings.proactiveStart(), settings.proactiveEnd())) {
            return false;
        }
        int count = localNow.toLocalDate().equals(settings.proactiveCounterDate())
                ? settings.proactiveCounter() : 0;
        if (count >= settings.proactiveDailyLimit()) {
            return false;
        }
        return settings.proactiveLastAt() == null
                || !settings.proactiveLastAt().plus(Duration.ofMinutes(settings.proactiveMinIntervalMinutes())).isAfter(now);
    }

    private boolean inWindow(LocalTime time, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            return !time.isBefore(start) && time.isBefore(end);
        }
        return !time.isBefore(start) || time.isBefore(end);
    }

    private void validateCommand(UpdateInteractionSettingsCommand command) {
        if (command == null || command.volumePercent() < 0 || command.volumePercent() > 100
                || command.followUpWindowSeconds() < 3 || command.followUpWindowSeconds() > 8
                || command.dndStart() == null || command.dndEnd() == null
                || command.dndStart().equals(command.dndEnd()) || command.missedReminderPolicy() == null
                || command.missedSnoozeMinutes() < 1 || command.missedSnoozeMinutes() > 1440
                || command.proactiveStart() == null || command.proactiveEnd() == null
                || command.proactiveStart().equals(command.proactiveEnd())
                || command.proactiveMinIntervalMinutes() < 30 || command.proactiveMinIntervalMinutes() > 1440
                || command.proactiveDailyLimit() < 1 || command.proactiveDailyLimit() > 10
                || command.proactiveContent() == null || command.proactiveContent().trim().isBlank()
                || command.proactiveContent().trim().length() > 500) {
            throw new InvalidInteractionSettingsException("Interaction settings are invalid");
        }
    }

    private void validateDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidInteractionSettingsException("Interaction settings device is invalid");
        }
    }

    private ZoneId parseZone(String value) {
        try {
            return ZoneId.of(value == null ? "" : value.trim());
        } catch (ZoneRulesException | IllegalArgumentException exception) {
            throw new InvalidInteractionSettingsException("Interaction settings zone is invalid", exception);
        }
    }

    private InteractionSettingsSnapshot snapshot(DeviceInteractionSettingsEntity entity) {
        return new InteractionSettingsSnapshot(
                entity.getDeviceId(), entity.getVolumePercent(), entity.isNightMode(),
                entity.isContinuousConversationEnabled(), entity.getFollowUpWindowSeconds(), entity.isDndEnabled(),
                entity.getDndStart(), entity.getDndEnd(), entity.getZoneId(), entity.getMissedReminderPolicy(),
                entity.getMissedSnoozeMinutes(), entity.isProactiveEnabled(), entity.getProactiveStart(),
                entity.getProactiveEnd(), entity.getProactiveMinIntervalMinutes(), entity.getProactiveDailyLimit(),
                entity.getProactiveContent(), entity.getProactiveLastAt(), entity.getProactiveCounterDate(),
                entity.getProactiveCounter(), entity.getUpdatedAt(), entity.getTemporaryDndUntil(),
                entity.isProactivePersonalizationEnabled()
        );
    }

    public record UpdateInteractionSettingsCommand(
            int volumePercent,
            boolean nightMode,
            boolean continuousConversationEnabled,
            int followUpWindowSeconds,
            boolean dndEnabled,
            LocalTime dndStart,
            LocalTime dndEnd,
            String zoneId,
            MissedReminderPolicy missedReminderPolicy,
            int missedSnoozeMinutes,
            boolean proactiveEnabled,
            LocalTime proactiveStart,
            LocalTime proactiveEnd,
            int proactiveMinIntervalMinutes,
            int proactiveDailyLimit,
            String proactiveContent,
            boolean proactivePersonalizationEnabled
    ) {
        public UpdateInteractionSettingsCommand(
                int volumePercent, boolean nightMode, boolean continuousConversationEnabled,
                int followUpWindowSeconds, boolean dndEnabled, LocalTime dndStart, LocalTime dndEnd,
                String zoneId, MissedReminderPolicy missedReminderPolicy, int missedSnoozeMinutes,
                boolean proactiveEnabled, LocalTime proactiveStart, LocalTime proactiveEnd,
                int proactiveMinIntervalMinutes, int proactiveDailyLimit, String proactiveContent
        ) {
            this(volumePercent, nightMode, continuousConversationEnabled, followUpWindowSeconds,
                    dndEnabled, dndStart, dndEnd, zoneId, missedReminderPolicy, missedSnoozeMinutes,
                    proactiveEnabled, proactiveStart, proactiveEnd, proactiveMinIntervalMinutes,
                    proactiveDailyLimit, proactiveContent, false);
        }
    }

    public record InteractionSettingsSnapshot(
            UUID deviceId,
            int volumePercent,
            boolean nightMode,
            boolean continuousConversationEnabled,
            int followUpWindowSeconds,
            boolean dndEnabled,
            LocalTime dndStart,
            LocalTime dndEnd,
            String zoneId,
            MissedReminderPolicy missedReminderPolicy,
            int missedSnoozeMinutes,
            boolean proactiveEnabled,
            LocalTime proactiveStart,
            LocalTime proactiveEnd,
            int proactiveMinIntervalMinutes,
            int proactiveDailyLimit,
            String proactiveContent,
            Instant proactiveLastAt,
            LocalDate proactiveCounterDate,
            int proactiveCounter,
            Instant updatedAt,
            Instant temporaryDndUntil,
            boolean proactivePersonalizationEnabled
    ) {
        public InteractionSettingsSnapshot(
                UUID deviceId, int volumePercent, boolean nightMode,
                boolean continuousConversationEnabled, int followUpWindowSeconds, boolean dndEnabled,
                LocalTime dndStart, LocalTime dndEnd, String zoneId, MissedReminderPolicy missedReminderPolicy,
                int missedSnoozeMinutes, boolean proactiveEnabled, LocalTime proactiveStart,
                LocalTime proactiveEnd, int proactiveMinIntervalMinutes, int proactiveDailyLimit,
                String proactiveContent, Instant proactiveLastAt, LocalDate proactiveCounterDate,
                int proactiveCounter, Instant updatedAt
        ) {
            this(deviceId, volumePercent, nightMode, continuousConversationEnabled, followUpWindowSeconds,
                    dndEnabled, dndStart, dndEnd, zoneId, missedReminderPolicy, missedSnoozeMinutes,
                    proactiveEnabled, proactiveStart, proactiveEnd, proactiveMinIntervalMinutes,
                    proactiveDailyLimit, proactiveContent, proactiveLastAt, proactiveCounterDate,
                    proactiveCounter, updatedAt, null, false);
        }

        public InteractionSettingsSnapshot(
                UUID deviceId, int volumePercent, boolean nightMode,
                boolean continuousConversationEnabled, int followUpWindowSeconds, boolean dndEnabled,
                LocalTime dndStart, LocalTime dndEnd, String zoneId, MissedReminderPolicy missedReminderPolicy,
                int missedSnoozeMinutes, boolean proactiveEnabled, LocalTime proactiveStart,
                LocalTime proactiveEnd, int proactiveMinIntervalMinutes, int proactiveDailyLimit,
                String proactiveContent, Instant proactiveLastAt, LocalDate proactiveCounterDate,
                int proactiveCounter, Instant updatedAt, Instant temporaryDndUntil
        ) {
            this(deviceId, volumePercent, nightMode, continuousConversationEnabled, followUpWindowSeconds,
                    dndEnabled, dndStart, dndEnd, zoneId, missedReminderPolicy, missedSnoozeMinutes,
                    proactiveEnabled, proactiveStart, proactiveEnd, proactiveMinIntervalMinutes,
                    proactiveDailyLimit, proactiveContent, proactiveLastAt, proactiveCounterDate,
                    proactiveCounter, updatedAt, temporaryDndUntil, false);
        }
    }
}
