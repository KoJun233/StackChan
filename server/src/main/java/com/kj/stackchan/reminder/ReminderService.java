package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {

    private final ReminderRepository reminderRepository;
    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private final ReminderScheduleCalculator scheduleCalculator;

    public ReminderService(
            ReminderRepository reminderRepository,
            DeviceRepository deviceRepository,
            Clock clock,
            ReminderScheduleCalculator scheduleCalculator
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
        this.scheduleCalculator = scheduleCalculator;
    }

    @Transactional(readOnly = true)
    public ReminderPage list(String content, ReminderStatus status, int from, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int page = Math.max(from, 0) / safeLimit;
        Specification<ReminderEntity> specification = (root, query, builder) -> builder.conjunction();
        if (content != null && !content.isBlank()) {
            String pattern = "%" + content.trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("content")), pattern));
        }
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        Page<ReminderEntity> result = reminderRepository.findAll(
                specification,
                PageRequest.of(page, safeLimit, Sort.by(Sort.Direction.DESC, "scheduledAt", "id"))
        );
        return new ReminderPage(result.getContent().stream().map(this::toSnapshot).toList(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ReminderSnapshot get(UUID id) {
        return toSnapshot(reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new));
    }

    @Transactional
    public ReminderSnapshot create(ReminderCommand command) {
        ValidatedCommand validated = validate(command);
        ReminderEntity reminder = new ReminderEntity(
                validated.deviceId(),
                validated.content(),
                validated.scheduledAt(),
                validated.zoneId(),
                validated.recurrenceType(),
                validated.recurrenceInterval(),
                validated.recurrenceAnchorLocal(),
                ReminderSource.USER,
                clock.instant()
        );
        return toSnapshot(reminderRepository.save(reminder));
    }

    @Transactional
    public ReminderSnapshot update(UUID id, ReminderCommand command) {
        ValidatedCommand validated = validate(command);
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        reminder.update(
                validated.deviceId(),
                validated.content(),
                validated.scheduledAt(),
                validated.zoneId(),
                validated.recurrenceType(),
                validated.recurrenceInterval(),
                validated.recurrenceAnchorLocal(),
                clock.instant()
        );
        return toSnapshot(reminder);
    }

    @Transactional
    public void delete(UUID id) {
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        reminderRepository.delete(reminder);
    }

    @Transactional
    public ReminderSnapshot snooze(UUID id, int minutes) {
        if (minutes < 1 || minutes > 1440) {
            throw new InvalidReminderException("Reminder snooze duration is invalid");
        }
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new InvalidReminderException("Only pending reminders can be snoozed");
        }
        Instant now = clock.instant();
        reminder.deferUntil(now.plusSeconds(minutes * 60L), now);
        return toSnapshot(reminder);
    }

    @Transactional(readOnly = true)
    public ReminderSnapshot nextPending(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidReminderException("Reminder device is invalid");
        }
        return reminderRepository.findFirstByDeviceIdAndStatusOrderByScheduledAtAscIdAsc(
                        deviceId, ReminderStatus.PENDING)
                .map(this::toSnapshot)
                .orElse(null);
    }

    @Transactional
    public ReminderSnapshot snoozeNext(UUID deviceId, int minutes) {
        ReminderSnapshot next = nextPending(deviceId);
        if (next == null) {
            throw new InvalidReminderException("No pending reminder exists");
        }
        return snooze(next.id(), minutes);
    }

    @Transactional
    public ReminderSnapshot skipNextPending(UUID deviceId) {
        ReminderSnapshot next = nextPending(deviceId);
        if (next == null) {
            throw new InvalidReminderException("No pending reminder exists");
        }
        return skipNext(next.id());
    }

    @Transactional
    public ReminderSnapshot skipNext(UUID id) {
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        if (reminder.getStatus() != ReminderStatus.PENDING) {
            throw new InvalidReminderException("Only pending reminders can be skipped");
        }
        Instant now = clock.instant();
        Instant after = reminder.getScheduledAt().isAfter(now) ? reminder.getScheduledAt() : now;
        Instant next = scheduleCalculator.nextAfter(reminder, after);
        reminder.completeOccurrence(ReminderStatus.SKIPPED, next, now);
        return toSnapshot(reminder);
    }

    private ValidatedCommand validate(ReminderCommand command) {
        if (command.deviceId() == null || !deviceRepository.existsById(command.deviceId())) {
            throw new InvalidReminderException("Reminder device is invalid");
        }
        String content = command.content() == null ? "" : command.content().trim();
        if (content.isBlank() || content.length() > 1000 || command.scheduledAt() == null) {
            throw new InvalidReminderException("Reminder content or time is invalid");
        }
        String zoneId = command.zoneId() == null ? "" : command.zoneId().trim();
        try {
            ZoneId.of(zoneId);
        } catch (ZoneRulesException | IllegalArgumentException exception) {
            throw new InvalidReminderException("Reminder zone is invalid", exception);
        }
        ReminderRecurrence recurrence = command.recurrenceType() == null
                ? ReminderRecurrence.NONE : command.recurrenceType();
        int interval = command.recurrenceInterval() == null ? 1 : command.recurrenceInterval();
        if (interval < 1 || interval > 365) {
            throw new InvalidReminderException("Reminder recurrence interval is invalid");
        }
        LocalDateTime anchor = recurrence == ReminderRecurrence.NONE
                ? null : command.scheduledAt().atZone(ZoneId.of(zoneId)).toLocalDateTime();
        return new ValidatedCommand(command.deviceId(), content, command.scheduledAt(), zoneId, recurrence, interval, anchor);
    }

    private ReminderSnapshot toSnapshot(ReminderEntity reminder) {
        return new ReminderSnapshot(
                reminder.getId(),
                reminder.getDeviceId(),
                reminder.getContent(),
                reminder.getScheduledAt(),
                reminder.getZoneId(),
                reminder.getStatus(),
                reminder.getRecurrenceType(),
                reminder.getRecurrenceInterval(),
                reminder.getSource(),
                reminder.getProactiveTopicKey(),
                reminder.getProactiveGenerationStatus(),
                reminder.getLastOutcome(),
                reminder.getLastCompletedAt(),
                reminder.getAttemptCount(),
                reminder.getFailureCode(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }

    private record ValidatedCommand(
            UUID deviceId,
            String content,
            Instant scheduledAt,
            String zoneId,
            ReminderRecurrence recurrenceType,
            int recurrenceInterval,
            LocalDateTime recurrenceAnchorLocal
    ) {
    }

    public record ReminderCommand(
            UUID deviceId,
            String content,
            Instant scheduledAt,
            String zoneId,
            ReminderRecurrence recurrenceType,
            Integer recurrenceInterval
    ) {
        public ReminderCommand(UUID deviceId, String content, Instant scheduledAt, String zoneId) {
            this(deviceId, content, scheduledAt, zoneId, ReminderRecurrence.NONE, 1);
        }
    }

    public record ReminderSnapshot(
            UUID id,
            UUID deviceId,
            String content,
            Instant scheduledAt,
            String zoneId,
            ReminderStatus status,
            ReminderRecurrence recurrenceType,
            int recurrenceInterval,
            ReminderSource source,
            String proactiveTopicKey,
            ProactiveGenerationStatus proactiveGenerationStatus,
            ReminderStatus lastOutcome,
            Instant lastCompletedAt,
            int attemptCount,
            String failureCode,
            Instant createdAt,
            Instant updatedAt
    ) {
        public ReminderSnapshot(
                UUID id,
                UUID deviceId,
                String content,
                Instant scheduledAt,
                String zoneId,
                ReminderStatus status,
                int attemptCount,
                String failureCode,
                Instant createdAt,
                Instant updatedAt
        ) {
            this(
                    id, deviceId, content, scheduledAt, zoneId, status,
                    ReminderRecurrence.NONE, 1, ReminderSource.USER, null, null, null, null,
                    attemptCount, failureCode, createdAt, updatedAt
            );
        }

        public ReminderSnapshot(
                UUID id, UUID deviceId, String content, Instant scheduledAt, String zoneId,
                ReminderStatus status, ReminderRecurrence recurrenceType, int recurrenceInterval,
                ReminderSource source, ReminderStatus lastOutcome, Instant lastCompletedAt,
                int attemptCount, String failureCode, Instant createdAt, Instant updatedAt
        ) {
            this(id, deviceId, content, scheduledAt, zoneId, status, recurrenceType,
                    recurrenceInterval, source, null, null, lastOutcome, lastCompletedAt,
                    attemptCount, failureCode, createdAt, updatedAt);
        }
    }

    public record ReminderPage(List<ReminderSnapshot> list, long total) {
    }
}
