package com.kj.stackchan.conversation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationStreamingRecoveryRaceTest {

    @Test
    void countsAndTouchesOnlyRowsThatRemainStreamingAtConditionalUpdate() {
        Instant now = Instant.parse("2026-07-18T12:00:00Z");
        UUID recoveredMessageId = UUID.fromString("d318dcf5-67c2-440d-aee4-36945ec06cc7");
        UUID racedMessageId = UUID.fromString("221b200b-72de-42dc-a003-344b8ef60039");
        UUID recoveredConversationId = UUID.fromString("d86683b6-8263-46ae-a436-a77f18b8998b");
        UUID racedConversationId = UUID.fromString("54198776-f23c-400a-a3f2-b15dfb53ef9f");
        ConversationMessageEntity recoveredMessage = mock(ConversationMessageEntity.class);
        ConversationMessageEntity racedMessage = mock(ConversationMessageEntity.class);
        when(recoveredMessage.getId()).thenReturn(recoveredMessageId);
        when(recoveredMessage.getConversationId()).thenReturn(recoveredConversationId);
        when(recoveredMessage.getContent()).thenReturn("part-a");
        when(racedMessage.getId()).thenReturn(racedMessageId);
        when(racedMessage.getConversationId()).thenReturn(racedConversationId);
        when(racedMessage.getContent()).thenReturn("part-race");

        ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        ConversationEntity recoveredConversation = mock(ConversationEntity.class);
        when(recoveredConversation.getId()).thenReturn(recoveredConversationId);
        when(messageRepository.findAllByRoleAndGenerationStatus(
                MessageRole.ASSISTANT, GenerationStatus.STREAMING
        )).thenReturn(List.of(recoveredMessage, racedMessage));
        when(messageRepository.finalizeStreamingAssistant(
                recoveredMessageId,
                MessageRole.ASSISTANT,
                GenerationStatus.STREAMING,
                GenerationStatus.INTERRUPTED,
                null,
                "part-a",
                now
        )).thenReturn(1);
        when(messageRepository.finalizeStreamingAssistant(
                racedMessageId,
                MessageRole.ASSISTANT,
                GenerationStatus.STREAMING,
                GenerationStatus.INTERRUPTED,
                null,
                "part-race",
                now
        )).thenReturn(0);
        when(conversationRepository.findAllById(Set.of(recoveredConversationId)))
                .thenReturn(List.of(recoveredConversation));
        ConversationService service = new ConversationService(
                conversationRepository,
                messageRepository,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        int recovered = service.recoverStreamingGenerations();

        assertThat(recovered).isEqualTo(1);
        verify(conversationRepository).findAllById(Set.of(recoveredConversationId));
        verify(recoveredConversation).touch(now);
        verify(conversationRepository, never()).findById(any());
    }
}
