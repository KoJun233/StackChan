package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
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

    public ReminderService(ReminderRepository reminderRepository, DeviceRepository deviceRepository, Clock clock) {
        this.reminderRepository = reminderRepository;
        this.deviceRepository = deviceRepository;
        this.clock = clock;
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
                clock.instant()
        );
        return toSnapshot(reminder);
    }

    @Transactional
    public void delete(UUID id) {
        ReminderEntity reminder = reminderRepository.findById(id).orElseThrow(ReminderNotFoundException::new);
        reminderRepository.delete(reminder);
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
        return new ValidatedCommand(command.deviceId(), content, command.scheduledAt(), zoneId);
    }

    private ReminderSnapshot toSnapshot(ReminderEntity reminder) {
        return new ReminderSnapshot(
                reminder.getId(),
                reminder.getDeviceId(),
                reminder.getContent(),
                reminder.getScheduledAt(),
                reminder.getZoneId(),
                reminder.getStatus(),
                reminder.getAttemptCount(),
                reminder.getFailureCode(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }

    private record ValidatedCommand(UUID deviceId, String content, Instant scheduledAt, String zoneId) {
    }

    public record ReminderCommand(UUID deviceId, String content, Instant scheduledAt, String zoneId) {
    }

    public record ReminderSnapshot(
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
    }

    public record ReminderPage(List<ReminderSnapshot> list, long total) {
    }
}
