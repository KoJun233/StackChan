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
    private VoiceActionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new VoiceActionCoordinator(proposalService, reminderService, memoryService, settingsService,
                Clock.fixed(Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC), null, topicCooldownService,
                notificationService, conversationService);
    }

    @Test
    void ordinaryConversationDoesNotCreateAProposal() {
        assertThat(coordinator.handle(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "今天天气怎么样"))
                .isNull();
        verify(proposalService, never()).propose(any(), any(), any(), any());
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
        when(topicCooldownService.muteLastTopic(deviceId)).thenReturn(true);

        var result = coordinator.handle(deviceId, UUID.randomUUID(), UUID.randomUUID(), "别再提这个了");

        assertThat(result.handled()).isTrue();
        assertThat(result.reply()).isEqualTo("好的，我不会再主动提这个话题。");
        verify(topicCooldownService).muteLastTopic(deviceId);
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
        when(notificationService.latestActionable(deviceId, roleId, NotificationResponseAction.ACKNOWLEDGE))
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
    void ordinaryAcknowledgementFallsThroughWithoutMatchingInteractiveNotification() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(conversationService.roleId(conversationId)).thenReturn(roleId);

        assertThat(coordinator.handle(deviceId, conversationId, UUID.randomUUID(), "知道了")).isNull();

        verify(notificationService).latestActionable(deviceId, roleId, NotificationResponseAction.ACKNOWLEDGE);
        verify(proposalService, never()).propose(any(), any(), any(), any());
    }
}
