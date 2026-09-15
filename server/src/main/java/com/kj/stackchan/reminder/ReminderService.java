package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
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
    private final CompanionRoleRepository roleRepository;

    public ReminderService(
            ReminderRepository reminderRepository,
            DeviceRepository deviceRepository,
            Clock clock,
            ReminderScheduleCalculator scheduleCalculator,
            CompanionRoleRepository roleRepository
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
        this.scheduleCalculator = scheduleCalculator;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public ReminderPage list(String content, ReminderStatus status, int from, int limit) {
        return list(CompanionRoleEntity.DEFAULT_ROLE_ID, content, status, from, limit);
    }

    @Transactional(readOnly = true)
    public ReminderPage list(UUID roleId, String content, ReminderStatus status, int from, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int page = Math.max(from, 0) / safeLimit;
        UUID resolvedRoleId = requireRole(roleId);
        Specification<ReminderEntity> specification = (root, query, builder) ->
                builder.equal(root.get("roleId"), resolvedRoleId);
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
        return create(CompanionRoleEntity.DEFAULT_ROLE_ID, command);
    }

    @Transactional
    public ReminderSnapshot create(UUID roleId, ReminderCommand command) {
        ValidatedCommand validated = validate(command);
        ReminderEntity reminder = new ReminderEntity(
                requireRole(roleId),
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
        if (reminder.getDeliveryGroupId() != null) {
            throw new InvalidReminderException("A reminder being delivered cannot be deleted");
        }
        reminderRepository.delete(reminder);
    }

    @Transactional
    public void cancelLinked(UUID id) {
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        if (reminder.getStatus() == ReminderStatus.PENDING || reminder.getStatus() == ReminderStatus.DISPATCHED) {
            reminder.markCancelled(clock.instant());
        }
    }

    @Transactional
    public ReminderSnapshot snooze(UUID id, int minutes) {
        if (minutes < 1 || minutes > 1440) {
            throw new InvalidReminderException("Reminder snooze duration is invalid");
        }
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        if (reminder.getStatus() != ReminderStatus.PENDING || reminder.getDeliveryGroupId() != null) {
            throw new InvalidReminderException("Only pending reminders can be snoozed");
        }
        Instant now = clock.instant();
        reminder.deferUntil(now.plusSeconds(minutes * 60L), now);
        return toSnapshot(reminder);
    }

    @Transactional(readOnly = true)
    public ReminderSnapshot nextPending(UUID deviceId) {
        return nextPending(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID);
    }

    @Transactional(readOnly = true)
    public ReminderSnapshot nextPending(UUID deviceId, UUID roleId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidReminderException("Reminder device is invalid");
        }
        return reminderRepository.findFirstByDeviceIdAndRoleIdAndStatusAndDeliveryGroupIdIsNullOrderByScheduledAtAscIdAsc(
                        deviceId, roleId, ReminderStatus.PENDING)
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

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public DeliveryTimeline timeline(UUID deviceId, UUID roleId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidReminderException("Reminder device is invalid");
        }
        UUID role = requireRole(roleId);
        Instant now = clock.instant();
        var upcoming = reminderRepository.findByDeviceIdAndRoleIdAndStatusIn(deviceId, role,
                List.of(ReminderStatus.PENDING, ReminderStatus.DISPATCHED),
                PageRequest.of(0, 10, Sort.by("scheduledAt", "id")));
        var recent = reminderRepository.findRecentCompletedDeliveries(deviceId, role, now.minusSeconds(1800), now,
                PageRequest.of(0, 11));
        return new DeliveryTimeline(deviceId, role, upcoming.getContent().stream().map(this::toSnapshot).toList(),
                upcoming.getTotalElements(), recent.stream().limit(10).map(this::toSnapshot).toList(), recent.size() > 10, now);
    }

    public record DeliveryTimeline(UUID deviceId, UUID roleId, List<ReminderSnapshot> upcoming,
            long upcomingTotal, List<ReminderSnapshot> recent, boolean recentHasMore, Instant checkedAt) { }

    @Transactional(readOnly = true)
    public ReminderSnapshot latestHeard(UUID deviceId, UUID roleId, Instant topicBoundary) {
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(1800);
        if (topicBoundary != null && topicBoundary.isAfter(cutoff)) cutoff = topicBoundary;
        var recent = reminderRepository.findRecentCompletedDeliveries(deviceId, roleId, cutoff, now, PageRequest.of(0, 2));
        if (recent.isEmpty() || (recent.size() > 1
                && recent.getFirst().getLastCompletedAt().equals(recent.get(1).getLastCompletedAt()))) return null;
        return toSnapshot(recent.getFirst());
    }

    @Transactional
    public ReminderSnapshot requireHeardUserReminder(UUID id, UUID deviceId, UUID roleId, Instant playedAt, String content) {
        var reminder = reminderRepository.findByIdAndSourceForUpdate(id, ReminderSource.USER)
                .orElseThrow(ReminderNotFoundException::new);
        Instant now = clock.instant();
        if (!reminder.getDeviceId().equals(deviceId) || !reminder.getRoleId().equals(roleId)
                || reminder.getLastOutcome() != ReminderStatus.DELIVERED || playedAt == null
                || !playedAt.equals(reminder.getLastCompletedAt()) || playedAt.isAfter(now)
                || !playedAt.isAfter(now.minusSeconds(1800)) || !reminder.getContent().equals(content)
                || (reminder.getStatus() != ReminderStatus.DELIVERED && reminder.getStatus() != ReminderStatus.PENDING)) {
            throw new InvalidReminderException("Recent reminder is no longer available");
        }
        return toSnapshot(reminder);
    }

    @Transactional(readOnly = true)
    public ReminderSnapshot nextPendingUserReminder(UUID deviceId, UUID roleId) {
        return reminderRepository.findFirstByDeviceIdAndRoleIdAndSourceAndStatusAndDeliveryGroupIdIsNullOrderByScheduledAtAscIdAsc(
                deviceId, roleId, ReminderSource.USER, ReminderStatus.PENDING).map(this::toSnapshot).orElse(null);
    }

    @Transactional
    public ReminderSnapshot applyConfirmedVoiceChange(UUID id, UUID deviceId, UUID roleId,
            Instant expectedSchedule, String expectedContent, Integer minutes) {
        if (id == null) throw new InvalidReminderException("Reminder confirmation has no target");
        ReminderEntity reminder = reminderRepository.findByIdAndSourceForUpdate(id, ReminderSource.USER)
                .orElseThrow(ReminderNotFoundException::new);
        if (!reminder.getDeviceId().equals(deviceId) || !reminder.getRoleId().equals(roleId)
                || !reminder.getScheduledAt().equals(expectedSchedule)
                || !reminder.getContent().equals(expectedContent)
                || reminder.getStatus() != ReminderStatus.PENDING || reminder.getDeliveryGroupId() != null) {
            throw new InvalidReminderException("Reminder confirmation target has changed");
        }
        if (minutes == null) return skipNext(id);
        if (minutes < 1 || minutes > 1440) throw new InvalidReminderException("Reminder snooze duration is invalid");
        Instant now = clock.instant();
        Instant base = reminder.getScheduledAt().isAfter(now) ? reminder.getScheduledAt() : now;
        reminder.deferUntil(base.plusSeconds(minutes * 60L), now);
        return toSnapshot(reminder);
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
        if (reminder.getStatus() != ReminderStatus.PENDING || reminder.getDeliveryGroupId() != null) {
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

    private UUID requireRole(UUID roleId) {
        UUID resolved = roleId == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : roleId;
        if (!roleRepository.existsById(resolved)) throw new InvalidReminderException("Reminder role is invalid");
        return resolved;
    }

    private ReminderSnapshot toSnapshot(ReminderEntity reminder) {
        return new ReminderSnapshot(
                reminder.getId(),
                reminder.getDeviceId(),
                reminder.getRoleId(),
                reminder.getContent(),
                reminder.getScheduledAt(),
                reminder.getZoneId(),
                reminder.getStatus(),
                reminder.getRecurrenceType(),
                reminder.getRecurrenceInterval(),
                reminder.getSource(),
                reminder.getProactiveTopicKey(),
                reminder.getProactiveGenerationStatus(),
                reminder.getProactiveSourceName(),
                reminder.getProactiveSourceTitle(),
                reminder.getProactiveSourceUrl(),
                reminder.getProactiveSourcePublishedAt(),
                reminder.getProactiveSourceRetrievedAt(),
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
            UUID roleId,
            String content,
            Instant scheduledAt,
            String zoneId,
            ReminderStatus status,
            ReminderRecurrence recurrenceType,
            int recurrenceInterval,
            ReminderSource source,
            String proactiveTopicKey,
            ProactiveGenerationStatus proactiveGenerationStatus,
            String proactiveSourceName,
            String proactiveSourceTitle,
            String proactiveSourceUrl,
            Instant proactiveSourcePublishedAt,
            Instant proactiveSourceRetrievedAt,
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
                    id, deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, content, scheduledAt, zoneId, status,
                    ReminderRecurrence.NONE, 1, ReminderSource.USER, null, null,
                    null, null, null, null, null, null, null,
                    attemptCount, failureCode, createdAt, updatedAt
            );
        }

        public ReminderSnapshot(
                UUID id, UUID deviceId, String content, Instant scheduledAt, String zoneId,
                ReminderStatus status, ReminderRecurrence recurrenceType, int recurrenceInterval,
                ReminderSource source, ReminderStatus lastOutcome, Instant lastCompletedAt,
                int attemptCount, String failureCode, Instant createdAt, Instant updatedAt
        ) {
            this(id, deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, content, scheduledAt, zoneId, status, recurrenceType,
                    recurrenceInterval, source, null, null,
                    null, null, null, null, null, lastOutcome, lastCompletedAt,
                    attemptCount, failureCode, createdAt, updatedAt);
        }
    }

    public record ReminderPage(List<ReminderSnapshot> list, long total) {
    }
}
