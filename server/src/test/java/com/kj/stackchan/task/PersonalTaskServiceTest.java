package com.kj.stackchan.task;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.role.CompanionRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalTaskServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Mock private PersonalTaskRepository taskRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private CompanionRoleRepository roleRepository;
    @Mock private ReminderService reminderService;
    private PersonalTaskService service;

    @BeforeEach
    void setUp() {
        service = new PersonalTaskService(taskRepository, deviceRepository, roleRepository, reminderService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void futureDueTaskCreatesOneRoleScopedReminder() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        Instant dueAt = NOW.plusSeconds(3600);
        allow(deviceId, roleId);
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reminderService.create(eq(roleId), any())).thenReturn(reminder(reminderId, deviceId, dueAt));

        PersonalTaskService.TaskSnapshot task = service.create(roleId, command(deviceId, "整理会议材料", dueAt));

        assertThat(task.reminderId()).isEqualTo(reminderId);
        ArgumentCaptor<ReminderService.ReminderCommand> reminder = ArgumentCaptor.forClass(ReminderService.ReminderCommand.class);
        verify(reminderService).create(eq(roleId), reminder.capture());
        assertThat(reminder.getValue().content()).isEqualTo("待办到期：整理会议材料");
        assertThat(reminder.getValue().scheduledAt()).isEqualTo(dueAt);
    }

    @Test
    void overdueTaskIsStoredWithoutSchedulingAReplayReminder() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        allow(deviceId, roleId);
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalTaskService.TaskSnapshot task = service.create(roleId,
                command(deviceId, "补交昨天的材料", NOW.minusSeconds(60)));

        assertThat(task.status()).isEqualTo(PersonalTaskStatus.OPEN);
        assertThat(task.reminderId()).isNull();
        verify(reminderService, never()).create(any(), any());
    }

    @Test
    void completingTaskCancelsLinkedReminderAndIsIdempotent() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID reminderId = UUID.randomUUID();
        PersonalTaskEntity task = entity(deviceId, roleId, "提交周报", NOW.plusSeconds(600));
        task.linkReminder(reminderId, NOW);
        when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        assertThat(service.complete(task.getId(), deviceId, roleId).status()).isEqualTo(PersonalTaskStatus.COMPLETED);
        assertThat(service.complete(task.getId(), deviceId, roleId).completedAt()).isEqualTo(NOW);
        verify(reminderService).cancelLinked(reminderId);
    }

    @Test
    void completionCannotCrossDeviceOrRoleScope() {
        PersonalTaskEntity task = entity(UUID.randomUUID(), UUID.randomUUID(), "私有待办", null);
        when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.complete(task.getId(), UUID.randomUUID(), task.getRoleId()))
                .isInstanceOf(PersonalTaskNotFoundException.class);
    }

    @Test
    void editingTaskCannotChangeItsRole() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        PersonalTaskEntity task = entity(deviceId, roleId, "私有待办", null);
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(taskRepository.findByIdForUpdate(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.update(task.getId(), UUID.randomUUID(),
                command(deviceId, "修改标题", null)))
                .isInstanceOf(InvalidPersonalTaskException.class)
                .hasMessageContaining("role cannot be changed");
    }

    @Test
    void exactTitleWinsOverSimilarMatchesAndDuplicatesStayAmbiguous() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        PersonalTaskEntity exact = entity(deviceId, roleId, "整理材料", null);
        PersonalTaskEntity similar = entity(deviceId, roleId, "整理材料并发送", null);
        when(taskRepository.findTop10ByDeviceIdAndRoleIdAndStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                deviceId, roleId, PersonalTaskStatus.OPEN, "整理材料")).thenReturn(List.of(similar, exact));

        PersonalTaskService.TitleMatch match = service.matchOpenTitle(deviceId, roleId, "整理材料");

        assertThat(match.status()).isEqualTo(PersonalTaskService.TitleMatchStatus.MATCHED);
        assertThat(match.task().id()).isEqualTo(exact.getId());

        PersonalTaskEntity duplicate = entity(deviceId, roleId, "整理材料", null);
        when(taskRepository.findTop10ByDeviceIdAndRoleIdAndStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
                deviceId, roleId, PersonalTaskStatus.OPEN, "整理材料")).thenReturn(List.of(exact, duplicate));
        assertThat(service.matchOpenTitle(deviceId, roleId, "整理材料").status())
                .isEqualTo(PersonalTaskService.TitleMatchStatus.AMBIGUOUS);
    }

    private void allow(UUID deviceId, UUID roleId) {
        when(deviceRepository.existsById(deviceId)).thenReturn(true);
        when(roleRepository.existsById(roleId)).thenReturn(true);
    }

    private PersonalTaskService.TaskCommand command(UUID deviceId, String title, Instant dueAt) {
        return new PersonalTaskService.TaskCommand(
                deviceId, title, "仅在管理页查看", PersonalTaskPriority.NORMAL, dueAt, "Asia/Shanghai");
    }

    private PersonalTaskEntity entity(UUID deviceId, UUID roleId, String title, Instant dueAt) {
        return new PersonalTaskEntity(deviceId, roleId, title, null, PersonalTaskPriority.NORMAL,
                dueAt, "Asia/Shanghai", NOW);
    }

    private ReminderService.ReminderSnapshot reminder(UUID id, UUID deviceId, Instant dueAt) {
        return new ReminderService.ReminderSnapshot(id, deviceId, "待办到期", dueAt, "Asia/Shanghai",
                ReminderStatus.PENDING, 0, null, NOW, NOW);
    }
}
