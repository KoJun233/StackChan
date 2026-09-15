package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kj.stackchan.device.DeviceInteractionSettingsCoordinator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.notification.InteractiveNotificationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.workday.WorkdayCompanionService;
import com.kj.stackchan.workday.WorkdayRestAction;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.task.PersonalTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceActionProposalServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T08:00:00Z");
    @Mock private VoiceActionProposalRepository proposalRepository;
    @Mock private VoiceActionAuditRepository auditRepository;
    @Mock private ReminderService reminderService;
    @Mock private InteractionSettingsService settingsService;
    @Mock private LongTermMemoryService memoryService;
    @Mock private DeviceInteractionSettingsCoordinator settingsCoordinator;
    private VoiceActionProposalService service;

    @BeforeEach
    void setUp() {
        when(proposalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new VoiceActionProposalService(proposalRepository, auditRepository, reminderService,
                settingsService, memoryService, settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reminderConfirmationPinsServerSelectedPartnerObjectBeforeQueueChanges() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        UUID reminder = UUID.randomUUID();
        var conversations = mock(ConversationService.class);
        when(conversations.roleId(conversation)).thenReturn(role);
        var scopedService = new VoiceActionProposalService(proposalRepository, auditRepository, reminderService,
                settingsService, memoryService, settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC),
                conversations, mock(CompanionRoleService.class));
        var target = new ReminderService.ReminderSnapshot(reminder, device, "喝水", NOW.plusSeconds(600),
                "Asia/Shanghai", ReminderStatus.PENDING, 0, null, NOW, NOW);
        when(reminderService.nextPendingUserReminder(device, role)).thenReturn(target);
        var proposal = scopedService.propose(device, conversation, UUID.randomUUID(), new VoiceActionDraft(
                VoiceActionType.SNOOZE_NEXT_REMINDER, true, "模型伪造标题", null, null, null, null, null,
                10, null, null, null, UUID.randomUUID()));
        assertThat(scopedService.restatement(proposal)).contains("喝水").doesNotContain("模型伪造标题");
        var entity = capturedProposal();
        assertThat(entity.getTargetReference()).isEqualTo(reminder);
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));
        when(reminderService.applyConfirmedVoiceChange(reminder, device, role, target.scheduledAt(), "喝水", 10))
                .thenReturn(target);
        assertThat(scopedService.confirm(proposal.id(), device, conversation).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        verify(reminderService, times(1)).nextPendingUserReminder(device, role);
        verify(reminderService, never()).snoozeNext(any(), anyInt());
    }

    @Test
    void repeatHeardReminderCreatesOneNewOccurrenceAfterRevalidation() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID role = com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID;
        Instant played = NOW.minusSeconds(30);
        var heard = mock(ReminderService.ReminderSnapshot.class);
        when(heard.id()).thenReturn(source);
        when(heard.content()).thenReturn("喝水");
        when(heard.zoneId()).thenReturn("UTC");
        when(heard.lastCompletedAt()).thenReturn(played);
        when(reminderService.requireHeardUserReminder(source, device, role, played, "喝水")).thenReturn(heard);
        var proposal = service.propose(device, conversation, UUID.randomUUID(), new VoiceActionDraft(
                VoiceActionType.CREATE_REMINDER, true, "喝水", "再提醒", NOW.plusSeconds(600), "UTC", "NONE", 1,
                10, played, null, null, source));
        assertThat(service.restatement(proposal)).contains("再提醒一次", "原来的周期和待办状态不变");
        var entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));
        UUID copy = UUID.randomUUID();
        when(reminderService.create(any())).thenReturn(new ReminderService.ReminderSnapshot(copy, device, "喝水",
                NOW.plusSeconds(600), "UTC", ReminderStatus.PENDING, 0, null, NOW, NOW));
        assertThat(service.confirm(proposal.id(), device, conversation).resultReference()).isEqualTo(copy);
        assertThat(service.confirm(proposal.id(), device, conversation).resultReference()).isEqualTo(copy);
        verify(reminderService, times(2)).requireHeardUserReminder(source, device, role, played, "喝水");
        verify(reminderService, times(1)).create(any());
        verify(reminderService, never()).snooze(any(), anyInt());
    }

    @Test
    void confirmationExecutesReminderExactlyOnceAndReplayIsIdempotent() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = service.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        verifyNoInteractions(reminderService);

        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));
        UUID reminderId = UUID.randomUUID();
        when(reminderService.create(any())).thenReturn(new ReminderService.ReminderSnapshot(
                reminderId, deviceId, "喝水", NOW.plusSeconds(600), "Asia/Shanghai",
                ReminderStatus.PENDING, 0, null, NOW, NOW));

        assertThat(service.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(service.confirm(proposal.id(), deviceId, conversationId).resultReference())
                .isEqualTo(reminderId);
        verify(reminderService, times(1)).create(any());
    }

    @Test
    void cancellationPreventsLaterExecution() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = service.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(service.cancel(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.CANCELLED);
        assertThat(service.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.CANCELLED);
        verifyNoInteractions(reminderService);
    }

    @Test
    void crossDeviceConfirmationIsRejectedBeforeExecution() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = service.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThatThrownBy(() -> service.confirm(proposal.id(), UUID.randomUUID(), conversationId))
                .isInstanceOf(VoiceActionException.class)
                .hasMessageContaining("unauthorized");
        verifyNoInteractions(reminderService);
    }

    @Test
    void expiredProposalCannotExecute() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(121));
        VoiceActionProposalService expiringService = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, clock);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        var proposal = expiringService.propose(deviceId, conversationId, UUID.randomUUID(), reminderDraft());
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(expiringService.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXPIRED);
        verifyNoInteractions(reminderService);
    }

    @Test
    void proposalToolPersistsMemoryProposalWithoutCallingMemoryService() {
        VoiceActionProposalTool tool = new VoiceActionProposalTool(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), service, new ObjectMapper());

        String result = tool.submit(new VoiceActionProposalTool.Input(
                "CREATE_MEMORY_SUGGESTION", "喜欢爵士乐", "偏好", null, null,
                null, null, null, null, null, "USER_PROFILE"));

        assertThat(result).contains("CREATE_MEMORY_SUGGESTION").contains("PENDING");
        assertThat(tool.submittedProposal().status()).isEqualTo(VoiceActionStatus.PENDING);
        verifyNoInteractions(memoryService);
    }

    @Test
    void confirmedNotificationResponseUsesPersistedTrustedScopeExactlyOnce() {
        ConversationService conversationService = mock(ConversationService.class);
        CompanionRoleService roleService = mock(CompanionRoleService.class);
        InteractiveNotificationService notificationService = mock(InteractiveNotificationService.class);
        VoiceActionProposalService interactiveService = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC), conversationService, roleService,
                notificationService);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);
        when(notificationService.descriptionForVoice(notificationId, deviceId, roleId, NotificationResponseAction.SNOOZE))
                .thenReturn("构建已经完成");

        var proposal = interactiveService.propose(deviceId, conversationId, UUID.randomUUID(),
                VoiceActionDraft.notificationResponse(
                        VoiceActionType.SNOOZE_NOTIFICATION, notificationId, 15));
        VoiceActionProposalEntity entity = capturedProposal();
        assertThat(interactiveService.restatement(proposal)).contains("构建已经完成", "15 分钟");
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(interactiveService.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(interactiveService.confirm(proposal.id(), deviceId, conversationId).resultReference())
                .isEqualTo(notificationId);
        verify(notificationService, times(1)).respond(
                notificationId, deviceId, roleId, NotificationResponseAction.SNOOZE, 15);
    }

    @Test
    void confirmedWorkdayActionsUseTheBoundDeviceExactlyOnce() {
        ConversationService conversationService = mock(ConversationService.class);
        CompanionRoleService roleService = mock(CompanionRoleService.class);
        InteractiveNotificationService notificationService = mock(InteractiveNotificationService.class);
        WorkdayCompanionService workdayService = mock(WorkdayCompanionService.class);
        VoiceActionProposalService workdayActions = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC), conversationService, roleService,
                notificationService, workdayService);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(UUID.randomUUID());

        var proposal = workdayActions.propose(deviceId, conversationId, UUID.randomUUID(),
                workdayDraft(VoiceActionType.START_WORKDAY));
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(workdayActions.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(workdayActions.confirm(proposal.id(), deviceId, conversationId).resultReference())
                .isEqualTo(deviceId);
        verify(workdayService, times(1)).start(deviceId);
    }

    @Test
    void confirmedRestSnoozeMapsToTheDeterministicWorkdayAction() {
        ConversationService conversationService = mock(ConversationService.class);
        CompanionRoleService roleService = mock(CompanionRoleService.class);
        InteractiveNotificationService notificationService = mock(InteractiveNotificationService.class);
        WorkdayCompanionService workdayService = mock(WorkdayCompanionService.class);
        VoiceActionProposalService workdayActions = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC), conversationService, roleService,
                notificationService, workdayService);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(UUID.randomUUID());
        when(workdayService.pendingRestPromptAt(deviceId)).thenReturn(NOW.minusSeconds(30));

        var proposal = workdayActions.propose(deviceId, conversationId, UUID.randomUUID(),
                workdayDraft(VoiceActionType.SNOOZE_WORKDAY_REST));
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(workdayActions.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        verify(workdayService).respondToRest(deviceId, WorkdayRestAction.SNOOZE, NOW.minusSeconds(30));
    }

    @Test
    void confirmedTaskCreationUsesPersistedDeviceAndRoleExactlyOnce() {
        ConversationService conversationService = mock(ConversationService.class);
        CompanionRoleService roleService = mock(CompanionRoleService.class);
        PersonalTaskService taskService = mock(PersonalTaskService.class);
        VoiceActionProposalService taskActions = new VoiceActionProposalService(
                proposalRepository, auditRepository, reminderService, settingsService, memoryService,
                settingsCoordinator, Clock.fixed(NOW, ZoneOffset.UTC), conversationService, roleService,
                null, null, taskService);
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);
        when(taskService.create(eq(roleId), any())).thenReturn(new PersonalTaskService.TaskSnapshot(
                taskId, deviceId, roleId, "整理会议材料", null, PersonalTaskPriority.NORMAL,
                PersonalTaskStatus.OPEN, null, "Asia/Shanghai", null, null, NOW, NOW));

        var proposal = taskActions.propose(deviceId, conversationId, UUID.randomUUID(),
                VoiceActionDraft.personalTask("整理会议材料", null, null));
        VoiceActionProposalEntity entity = capturedProposal();
        when(proposalRepository.findByIdForUpdate(proposal.id())).thenReturn(java.util.Optional.of(entity));

        assertThat(taskActions.confirm(proposal.id(), deviceId, conversationId).resultReference()).isEqualTo(taskId);
        assertThat(taskActions.confirm(proposal.id(), deviceId, conversationId).status())
                .isEqualTo(VoiceActionStatus.EXECUTED);
        verify(taskService, times(1)).create(eq(roleId), any());
    }

    private VoiceActionDraft reminderDraft() {
        return VoiceActionDraft.reminder("喝水", NOW.plusSeconds(600), "Asia/Shanghai", "NONE", 1);
    }

    private VoiceActionDraft workdayDraft(VoiceActionType actionType) {
        return new VoiceActionDraft(actionType, true, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private VoiceActionProposalEntity capturedProposal() {
        var captor = org.mockito.ArgumentCaptor.forClass(VoiceActionProposalEntity.class);
        verify(proposalRepository).save(captor.capture());
        return captor.getValue();
    }
}
