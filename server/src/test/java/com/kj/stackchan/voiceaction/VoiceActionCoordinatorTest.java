package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.ProactiveTopicCooldownService;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ReminderService;
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
    private VoiceActionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new VoiceActionCoordinator(proposalService, reminderService, memoryService, settingsService,
                Clock.fixed(Instant.parse("2026-08-02T08:00:00Z"), ZoneOffset.UTC), null, topicCooldownService);
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
}
