package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.ProactiveTopicCooldownService;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.notification.InteractiveNotificationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.task.PersonalTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceActionCoordinatorTest {
    @Mock private VoiceActionProposalService proposalService;
    @Mock private ReminderService reminderService;
    @Mock private LongTermMemoryService memoryService;
    @Mock private InteractionSettingsService settingsService;
    @Mock private ProactiveTopicCooldownService topicCooldownService;
    @Mock private InteractiveNotificationService notificationService;
    @Mock private ConversationService conversationService;
    @Mock private PersonalTaskService personalTaskService;
    private VoiceActionCoordinator coordinator;

    @Test
    void explicitTemporaryPauseAndResumeUseCurrentPartnerWithoutChangingReminderSettings() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var pauses = org.mockito.Mockito.mock(com.kj.stackchan.interaction.ProactivePauseService.class);
        coordinator.setPauseService(pauses);
        when(conversationService.roleId(conversation)).thenReturn(role);
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "今天别主动聊").reply()).contains("提醒照常");
        verify(pauses).pause(device, role, null);
        coordinator.handle(device, conversation, UUID.randomUUID(), "暂停主动聊天 30 分钟");
        verify(pauses).pause(device, role, 30);
        coordinator.handle(device, conversation, UUID.randomUUID(), "恢复主动聊天");
        verify(pauses).resume(device, role);
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "暂停主动聊天 0 分钟").reply()).contains("1 到 1440");
        verify(pauses, never()).pause(device, role, 0);
        verifyNoInteractions(settingsService, reminderService);
        org.mockito.Mockito.clearInvocations(pauses);
        coordinator.handle(device, conversation, UUID.randomUUID(), "他说今天别主动聊是什么意思");
        verifyNoInteractions(pauses);
    }

    @BeforeEach
    void setUp() {
        coordinator = new VoiceActionCoordinator(proposalService, reminderService, memoryService, settingsService,
                Clock.fixed(Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC), null, topicCooldownService,
                notificationService, conversationService, personalTaskService);
    }

    @Test
    void readOnlyReminderAndMemoryQuestionsStayWithConversationPartner() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        when(conversationService.roleId(conversation)).thenReturn(role);
        when(memoryService.pendingVisibleCount(role, device)).thenReturn(2L);
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "待确认记忆").reply()).contains("2 条");
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "下一条提醒").reply()).contains("没有");
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "提醒推迟 10 分钟").reply()).contains("当前伙伴没有");
        verify(reminderService).nextPending(device, role);
        verify(memoryService, never()).pendingVisibleCount(device);
        verify(reminderService, never()).nextPending(device);
        verify(proposalService, never()).propose(any(), any(), any(), any());
    }

    @Test
    void shortSnoozeUsesTheHeardPersonalReminderAndLeavesCompletionSeparate() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        UUID reminder = UUID.randomUUID();
        Instant playedAt = Instant.parse("2026-08-02T07:59:00Z");
        when(conversationService.roleId(conversation)).thenReturn(role);
        var heard = mock(ReminderService.ReminderSnapshot.class);
        when(reminderService.latestHeard(device, role, null)).thenReturn(heard);
        when(heard.source()).thenReturn(com.kj.stackchan.reminder.ReminderSource.USER);
        when(heard.content()).thenReturn("喝水");
        when(heard.id()).thenReturn(reminder);
        when(heard.zoneId()).thenReturn("Asia/Shanghai");
        when(heard.lastCompletedAt()).thenReturn(playedAt);
        when(proposalService.restatement(any())).thenReturn("确认再提醒一次吗？");
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "稍后").reply()).contains("确认");
        var captor = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(eq(device), eq(conversation), any(), captor.capture());
        assertThat(captor.getValue().actionType()).isEqualTo(VoiceActionType.CREATE_REMINDER);
        assertThat(captor.getValue().targetReference()).isEqualTo(reminder);
        assertThat(captor.getValue().targetAt()).isEqualTo(playedAt);
        assertThat(captor.getValue().recurrenceType()).isEqualTo("NONE");
        assertThat(captor.getValue().durationMinutes()).isEqualTo(10);
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "完成了").reply()).contains("具体标题");
        verify(personalTaskService, never()).complete(any(), any(), any());
    }

    @Test
    void shortRestSnoozeBindsTheHeardPromptAndRejectsUnsupportedDuration() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        when(conversationService.roleId(conversation)).thenReturn(role);
        var heard = mock(ReminderService.ReminderSnapshot.class);
        when(reminderService.latestHeard(device, role, null)).thenReturn(heard);
        when(heard.source()).thenReturn(com.kj.stackchan.reminder.ReminderSource.PROACTIVE);
        when(heard.proactiveTopicKey()).thenReturn("workday:rest:fixture");
        when(heard.lastCompletedAt()).thenReturn(Instant.EPOCH);
        when(proposalService.restatement(any())).thenReturn("确认推迟休息吗？");
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "稍后十分钟").reply()).contains("确认");
        var captor = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(eq(device), eq(conversation), any(), captor.capture());
        assertThat(captor.getValue().actionType()).isEqualTo(VoiceActionType.SNOOZE_WORKDAY_REST);
        assertThat(captor.getValue().targetAt()).isEqualTo(Instant.EPOCH);
        assertThat(coordinator.handle(device, conversation, UUID.randomUUID(), "稍后30分钟").reply()).contains("十分钟");
    }

    @Test
    void changingTopicsCancelsPendingConfirmationWithoutExecutingIt() {
        UUID device = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        var pending = mock(VoiceActionProposalService.ProposalSnapshot.class);
        UUID proposal = UUID.randomUUID();
        when(pending.id()).thenReturn(proposal);
        when(proposalService.latestPending(device, conversation)).thenReturn(pending);
        coordinator.cancelPendingOperation(device, conversation);
        verify(proposalService).cancel(proposal, device, conversation);
        verify(proposalService, never()).confirm(any(), any(), any());
        verifyNoInteractions(settingsService, personalTaskService);
    }

    @Test
    void ordinaryConversationDoesNotCreateAProposal() {
        assertThat(coordinator.handle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "今天天气怎么样"))
                .isNull();
        verify(proposalService, never()).propose(any(), any(), any(), any());
    }

    @Test
    void taskListQuestionRemainsAReadOnlyAgentQuery() {
        assertThat(coordinator.handle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "我的任务有哪些"))
                .isNull();
        verify(proposalService, never()).propose(any(), any(), any(), any());
        verifyNoInteractions(personalTaskService);
    }

    @Test
    void explicitVolumePhraseCreatesConfirmationProposalWithoutApplyingSettings() {
        UUID proposalId = UUID.randomUUID();
        when(proposalService.propose(any(), any(), any(), any())).thenReturn(
                new VoiceActionProposalService.ProposalSnapshot(proposalId, VoiceActionType.SET_VOLUME,
                        VoiceActionStatus.PENDING, true, null, null, null, null, null, 50,
                        null, null, Instant.parse("2026-08-02T08:02:00Z")));
        when(proposalService.restatement(any())).thenReturn("确认音量调整");

        var result = coordinator.handle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "把音量调到50%吧");

        assertThat(result.reply()).isEqualTo("确认音量调整");
        ArgumentCaptor<VoiceActionDraft> draft = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(any(), any(), any(), draft.capture());
        assertThat(draft.getValue().volumePercent()).isEqualTo(50);
        verifyNoInteractions(settingsService);
    }

    @Test
    void explicitRequestPermanentlyMutesTheMostRecentProactiveTopic() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);
        Instant boundary = Instant.parse("2026-08-02T07:50:00Z");
        when(conversationService.voiceTopicBoundary(conversationId)).thenReturn(boundary);
        when(topicCooldownService.muteLastDeliveredTopic(deviceId, roleId, boundary)).thenReturn(true);

        var result = coordinator.handle(deviceId, conversationId, UUID.randomUUID(), "别再提这个了");

        assertThat(result.handled()).isTrue();
        assertThat(result.reply()).isEqualTo("好的，我不会再主动提这个话题。");
        verify(topicCooldownService).muteLastDeliveredTopic(deviceId, roleId, boundary);
        verify(topicCooldownService, never()).muteLastTopic(deviceId);
        verify(proposalService, never()).propose(any(), any(), any(), any());
    }

    @Test
    void quotedMutePhraseInsideOrdinaryConversationDoesNotChangeTopicState() {
        var result = coordinator.handle(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "他说别再提这个以后就离开了"
        );

        assertThat(result).isNull();
        verifyNoInteractions(topicCooldownService);
    }

    @Test
    void actionableNotificationCreatesTrustedConfirmationProposal() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);
        when(notificationService.latestActionable(deviceId, roleId, NotificationResponseAction.ACKNOWLEDGE, null))
                .thenReturn(notificationId);
        when(proposalService.propose(any(), any(), any(), any())).thenReturn(
                new VoiceActionProposalService.ProposalSnapshot(proposalId, VoiceActionType.ACKNOWLEDGE_NOTIFICATION,
                        VoiceActionStatus.PENDING, true, null, null, null, null, null, null,
                        null, null, Instant.parse("2026-08-02T08:02:00Z")));
        when(proposalService.restatement(any())).thenReturn("要将最近通知标记为已知晓。确认执行吗？");

        var result = coordinator.handle(deviceId, conversationId, UUID.randomUUID(), "知道了");

        assertThat(result.reply()).contains("确认执行");
        ArgumentCaptor<VoiceActionDraft> draft = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(eq(deviceId), eq(conversationId), any(), draft.capture());
        assertThat(draft.getValue().actionType()).isEqualTo(VoiceActionType.ACKNOWLEDGE_NOTIFICATION);
        assertThat(draft.getValue().targetReference()).isEqualTo(notificationId);
    }

    @Test
    void ordinaryAcknowledgementDoesNotModifyAnOlderNotification() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);

        assertThat(coordinator.handle(deviceId, conversationId, UUID.randomUUID(), "知道了").reply()).isEqualTo("好的。");

        verify(notificationService).latestActionable(deviceId, roleId, NotificationResponseAction.ACKNOWLEDGE, null);
        verify(proposalService, never()).propose(any(), any(), any(), any());
    }

    @Test
    void explicitWorkdayLifecyclePhraseCreatesAConfirmedFixedAction() {
        UUID proposalId = UUID.randomUUID();
        when(proposalService.propose(any(), any(), any(), any())).thenReturn(
                new VoiceActionProposalService.ProposalSnapshot(proposalId, VoiceActionType.START_WORKDAY,
                        VoiceActionStatus.PENDING, true, null, null, null, null, null, null,
                        null, null, Instant.parse("2026-08-02T08:02:00Z")));
        when(proposalService.restatement(any())).thenReturn("要开始当前设备的工作模式。确认执行吗？");

        var result = coordinator.handle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "开始工作");

        assertThat(result.reply()).contains("确认执行");
        ArgumentCaptor<VoiceActionDraft> draft = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(any(), any(), any(), draft.capture());
        assertThat(draft.getValue().actionType()).isEqualTo(VoiceActionType.START_WORKDAY);
        assertThat(draft.getValue().confirmationRequired()).isTrue();
    }

    @Test
    void simpleTaskCreationProducesConfirmationWithoutWritingTask() {
        UUID proposalId = UUID.randomUUID();
        when(proposalService.propose(any(), any(), any(), any())).thenReturn(
                new VoiceActionProposalService.ProposalSnapshot(proposalId, VoiceActionType.CREATE_PERSONAL_TASK,
                        VoiceActionStatus.PENDING, true, "整理会议材料", null, null, null, null, null,
                        null, null, Instant.parse("2026-08-02T08:02:00Z")));
        when(proposalService.restatement(any())).thenReturn("要新增待办。确认执行吗？");

        var result = coordinator.handle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "添加待办：整理会议材料");

        assertThat(result.reply()).contains("确认执行");
        ArgumentCaptor<VoiceActionDraft> draft = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(any(), any(), any(), draft.capture());
        assertThat(draft.getValue().actionType()).isEqualTo(VoiceActionType.CREATE_PERSONAL_TASK);
        verify(personalTaskService, never()).create(any(), any());
    }

    @Test
    void matchingTaskCompletionProducesScopedConfirmation() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);
        when(personalTaskService.matchOpenTitle(deviceId, roleId, "整理会议材料")).thenReturn(
                new PersonalTaskService.TitleMatch(PersonalTaskService.TitleMatchStatus.MATCHED,
                        new PersonalTaskService.TaskSnapshot(taskId, deviceId, roleId, "整理会议材料", null,
                                PersonalTaskPriority.NORMAL, PersonalTaskStatus.OPEN, null, "Asia/Shanghai", null,
                                null, Instant.EPOCH, Instant.EPOCH)));
        when(proposalService.propose(any(), any(), any(), any())).thenReturn(
                new VoiceActionProposalService.ProposalSnapshot(UUID.randomUUID(), VoiceActionType.COMPLETE_PERSONAL_TASK,
                        VoiceActionStatus.PENDING, true, "整理会议材料", null, null, null, null, null,
                        null, null, Instant.parse("2026-08-02T08:02:00Z")));
        when(proposalService.restatement(any())).thenReturn("要标记完成。确认执行吗？");

        var result = coordinator.handle(deviceId, conversationId, UUID.randomUUID(), "完成待办：整理会议材料");

        assertThat(result.reply()).contains("确认执行");
        ArgumentCaptor<VoiceActionDraft> draft = ArgumentCaptor.forClass(VoiceActionDraft.class);
        verify(proposalService).propose(eq(deviceId), eq(conversationId), any(), draft.capture());
        assertThat(draft.getValue().targetReference()).isEqualTo(taskId);
        verify(personalTaskService, never()).complete(any(), any(), any());
    }
}
