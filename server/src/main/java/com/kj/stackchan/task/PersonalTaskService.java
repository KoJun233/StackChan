package com.kj.stackchan.task;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalTaskService {

    private final PersonalTaskRepository taskRepository;
    private final DeviceRepository deviceRepository;
    private final CompanionRoleRepository roleRepository;
    private final ReminderService reminderService;
    private final Clock clock;

    public PersonalTaskService(
            PersonalTaskRepository taskRepository,
            DeviceRepository deviceRepository,
            CompanionRoleRepository roleRepository,
            ReminderService reminderService,
            Clock clock
    ) {
        this.taskRepository = taskRepository;
        this.deviceRepository = deviceRepository;
        this.roleRepository = roleRepository;
        this.reminderService = reminderService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TaskPage list(String query, PersonalTaskStatus status, PersonalTaskPriority priority, UUID roleId,
                         int from, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int page = Math.max(from, 0) / safeLimit;
        Specification<PersonalTaskEntity> specification = (root, ignored, builder) -> builder.conjunction();
        if (roleId != null) {
            requireRole(roleId);
            specification = specification.and((root, ignored, builder) -> builder.equal(root.get("roleId"), roleId));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase() + "%";
            specification = specification.and((root, ignored, builder) -> builder.or(
                    builder.like(builder.lower(root.get("title")), pattern),
                    builder.like(builder.lower(root.get("notes")), pattern)
            ));
        }
        if (status != null) {
            specification = specification.and((root, ignored, builder) -> builder.equal(root.get("status"), status));
        }
        if (priority != null) {
            specification = specification.and((root, ignored, builder) -> builder.equal(root.get("priority"), priority));
        }
        Page<PersonalTaskEntity> result = taskRepository.findAll(
                specification,
                PageRequest.of(page, safeLimit, Sort.by(
                        Sort.Order.desc("status"), Sort.Order.asc("dueAt"), Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")))
        );
        return new TaskPage(result.getContent().stream().map(this::snapshot).toList(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TaskSnapshot get(UUID id) {
        return snapshot(taskRepository.findById(id).orElseThrow(PersonalTaskNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<TaskSnapshot> openForAgent(UUID deviceId, UUID roleId) {
        requireDevice(deviceId);
        requireRole(roleId);
        return taskRepository.findTop20ByDeviceIdAndRoleIdAndStatusOrderByDueAtAscCreatedAtDesc(
                deviceId, roleId, PersonalTaskStatus.OPEN).stream().map(this::snapshot).toList();
    }

    @Transactional(readOnly = true)
    public TitleMatch matchOpenTitle(UUID deviceId, UUID roleId, String title) {
        String normalized = normalizeTitle(title);
        List<PersonalTaskEntity> matches = taskRepository
                .findTop10ByDeviceIdAndRoleIdAndStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                        deviceId, roleId, PersonalTaskStatus.OPEN, normalized);
        List<PersonalTaskEntity> exact = matches.stream()
                .filter(task -> task.getTitle().equalsIgnoreCase(normalized)).toList();
        List<PersonalTaskEntity> resolved = exact.isEmpty() ? matches : exact;
        if (resolved.isEmpty()) return new TitleMatch(TitleMatchStatus.NOT_FOUND, null);
        if (resolved.size() > 1) return new TitleMatch(TitleMatchStatus.AMBIGUOUS, null);
        return new TitleMatch(TitleMatchStatus.MATCHED, snapshot(resolved.get(0)));
    }

    @Transactional
    public TaskSnapshot create(TaskCommand command) {
        return create(CompanionRoleEntity.DEFAULT_ROLE_ID, command);
    }

    @Transactional
    public TaskSnapshot create(UUID roleId, TaskCommand command) {
        ValidatedCommand validated = validate(command);
        UUID resolvedRoleId = requireRole(roleId);
        Instant now = clock.instant();
        PersonalTaskEntity task = taskRepository.save(new PersonalTaskEntity(
                validated.deviceId(), resolvedRoleId, validated.title(), validated.notes(), validated.priority(),
                validated.dueAt(), validated.zoneId(), now));
        syncReminder(task, now);
        return snapshot(task);
    }

    @Transactional
    public TaskSnapshot update(UUID id, TaskCommand command) {
        return update(id, null, command);
    }

    @Transactional
    public TaskSnapshot update(UUID id, UUID roleId, TaskCommand command) {
        ValidatedCommand validated = validate(command);
        PersonalTaskEntity task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(PersonalTaskNotFoundException::new);
        if (!task.getDeviceId().equals(validated.deviceId())) {
            throw new InvalidPersonalTaskException("Task device cannot be changed");
        }
        if (roleId != null && !task.getRoleId().equals(roleId)) {
            throw new InvalidPersonalTaskException("Task role cannot be changed");
        }
        Instant now = clock.instant();
        task.update(validated.title(), validated.notes(), validated.priority(), validated.dueAt(),
                validated.zoneId(), now);
        if (task.getStatus() == PersonalTaskStatus.OPEN) syncReminder(task, now);
        return snapshot(task);
    }

    @Transactional
    public TaskSnapshot complete(UUID id) {
        return complete(id, null, null);
    }

    @Transactional
    public TaskSnapshot complete(UUID id, UUID deviceId, UUID roleId) {
        PersonalTaskEntity task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(PersonalTaskNotFoundException::new);
        requireScope(task, deviceId, roleId);
        if (task.getStatus() == PersonalTaskStatus.COMPLETED) return snapshot(task);
        Instant now = clock.instant();
        cancelLinkedReminder(task, now);
        task.complete(now);
        return snapshot(task);
    }

    @Transactional
    public TaskSnapshot reopen(UUID id) {
        PersonalTaskEntity task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(PersonalTaskNotFoundException::new);
        if (task.getStatus() == PersonalTaskStatus.OPEN) return snapshot(task);
        Instant now = clock.instant();
        task.reopen(now);
        syncReminder(task, now);
        return snapshot(task);
    }

    @Transactional
    public void delete(UUID id) {
        PersonalTaskEntity task = taskRepository.findByIdForUpdate(id)
                .orElseThrow(PersonalTaskNotFoundException::new);
        cancelLinkedReminder(task, clock.instant());
        taskRepository.delete(task);
    }

    private void syncReminder(PersonalTaskEntity task, Instant now) {
        if (task.getDueAt() == null || !task.getDueAt().isAfter(now)) {
            cancelLinkedReminder(task, now);
            return;
        }
        String content = "待办到期：" + task.getTitle();
        ReminderService.ReminderCommand command = new ReminderService.ReminderCommand(
                task.getDeviceId(), content, task.getDueAt(), task.getZoneId(), ReminderRecurrence.NONE, 1);
        if (task.getReminderId() != null) {
            ReminderService.ReminderSnapshot reminder = reminderService.get(task.getReminderId());
            if (reminder.status() == ReminderStatus.PENDING) {
                reminderService.update(task.getReminderId(), command);
                return;
            }
            if (reminder.status() == ReminderStatus.DISPATCHED) {
                throw new InvalidPersonalTaskException("Task reminder is currently being delivered");
            }
            task.clearReminder(now);
        }
        UUID reminderId = reminderService.create(task.getRoleId(), command).id();
        task.linkReminder(reminderId, now);
    }

    private void cancelLinkedReminder(PersonalTaskEntity task, Instant now) {
        if (task.getReminderId() == null) return;
        reminderService.cancelLinked(task.getReminderId());
        task.clearReminder(now);
    }

    private ValidatedCommand validate(TaskCommand command) {
        if (command == null || command.deviceId() == null) {
            throw new InvalidPersonalTaskException("Task device is required");
        }
        requireDevice(command.deviceId());
        String title = normalizeTitle(command.title());
        String notes = command.notes() == null || command.notes().isBlank() ? null : command.notes().trim();
        if (notes != null && notes.length() > 2000) {
            throw new InvalidPersonalTaskException("Task notes are too long");
        }
        PersonalTaskPriority priority = command.priority() == null ? PersonalTaskPriority.NORMAL : command.priority();
        String zoneId = command.zoneId() == null || command.zoneId().isBlank()
                ? "Asia/Shanghai" : command.zoneId().trim();
        try {
            ZoneId.of(zoneId);
        } catch (DateTimeException exception) {
            throw new InvalidPersonalTaskException("Task time zone is invalid");
        }
        return new ValidatedCommand(command.deviceId(), title, notes, priority, command.dueAt(), zoneId);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) throw new InvalidPersonalTaskException("Task title is required");
        String normalized = title.trim();
        if (normalized.length() > 200) throw new InvalidPersonalTaskException("Task title is too long");
        return normalized;
    }

    private UUID requireRole(UUID roleId) {
        if (roleId == null || !roleRepository.existsById(roleId)) {
            throw new InvalidPersonalTaskException("Task role is invalid");
        }
        return roleId;
    }

    private void requireDevice(UUID deviceId) {
        if (deviceId == null || !deviceRepository.existsById(deviceId)) {
            throw new InvalidPersonalTaskException("Task device is invalid");
        }
    }

    private void requireScope(PersonalTaskEntity task, UUID deviceId, UUID roleId) {
        if ((deviceId != null && !task.getDeviceId().equals(deviceId))
                || (roleId != null && !task.getRoleId().equals(roleId))) {
            throw new PersonalTaskNotFoundException();
        }
    }

    private TaskSnapshot snapshot(PersonalTaskEntity task) {
        return new TaskSnapshot(task.getId(), task.getDeviceId(), task.getRoleId(), task.getTitle(), task.getNotes(),
                task.getPriority(), task.getStatus(), task.getDueAt(), task.getZoneId(), task.getReminderId(),
                task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }

    public record TaskCommand(UUID deviceId, String title, String notes, PersonalTaskPriority priority,
                              Instant dueAt, String zoneId) { }
    private record ValidatedCommand(UUID deviceId, String title, String notes, PersonalTaskPriority priority,
                                    Instant dueAt, String zoneId) { }
    public record TaskSnapshot(UUID id, UUID deviceId, UUID roleId, String title, String notes,
                               PersonalTaskPriority priority, PersonalTaskStatus status, Instant dueAt, String zoneId,
                               UUID reminderId, Instant completedAt, Instant createdAt, Instant updatedAt) { }
    public record TaskPage(List<TaskSnapshot> list, long total) { }
    public enum TitleMatchStatus { MATCHED, NOT_FOUND, AMBIGUOUS }
    public record TitleMatch(TitleMatchStatus status, TaskSnapshot task) { }
}
