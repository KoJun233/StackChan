package com.kj.stackchan.memory;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemorySuggestionExtractionServiceTest {
    @Test
    void continuationCandidateUsesDeviceRoleAndOriginalUserFactsWithoutOrdinarySuggestion() {
        var factory = mock(LlmRuntimeClientFactory.class);
        var memory = mock(LongTermMemoryService.class);
        var topics = mock(TopicContinuationService.class);
        var chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(factory.createChatClient()).thenReturn(chat);
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("""
                {"suggest":true,"category":"EVENT","title":"木工","content":"准备做书架","topicKey":"木工","importance":3,"reason":"用户分享项目","continueLater":true}
                """);
        var service = new MemorySuggestionExtractionService(Runnable::run, factory, memory, new ObjectMapper());
        service.setContinuationService(topics);
        UUID turn = UUID.randomUUID(), device = UUID.randomUUID(), role = UUID.randomUUID();
        service.schedule(new MemorySuggestionExtractionService.SuggestionTurn(turn, device, "我正在准备木工书架", "你已经获得一等奖", role));
        var command = org.mockito.ArgumentCaptor.forClass(LongTermMemoryService.MemorySuggestionCommand.class);
        verify(topics).suggest(org.mockito.ArgumentMatchers.eq(role), org.mockito.ArgumentMatchers.eq(device), org.mockito.ArgumentMatchers.eq("我正在准备木工书架"), command.capture());
        assertThat(command.getValue().sourceTurnId()).isEqualTo(turn);
        assertThat(command.getValue().memory().scopeType()).isEqualTo(MemoryScopeType.DEVICE);
        org.mockito.Mockito.verifyNoInteractions(memory);
    }

    @Test
    void stringContinuationFlagCannotGrantPermissionOrCreateAnotherSuggestion() {
        var factory = mock(LlmRuntimeClientFactory.class);
        var memory = mock(LongTermMemoryService.class);
        var topics = mock(TopicContinuationService.class);
        var chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(factory.createChatClient()).thenReturn(chat);
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("""
                {"suggest":true,"category":"EVENT","title":"木工","content":"准备做书架","topicKey":"木工","importance":3,"reason":"用户分享项目","continueLater":"true"}
                """);
        var service = new MemorySuggestionExtractionService(Runnable::run, factory, memory, new ObjectMapper());
        service.setContinuationService(topics);
        service.schedule(new MemorySuggestionExtractionService.SuggestionTurn(UUID.randomUUID(), UUID.randomUUID(), "我正在准备木工书架", "收到"));
        org.mockito.Mockito.verifyNoInteractions(memory, topics);
    }

    @Test
    void webConversationRetainsOrdinaryMemorySuggestionWithoutDeviceContinuation() {
        var factory = mock(LlmRuntimeClientFactory.class);
        var memory = mock(LongTermMemoryService.class);
        var topics = mock(TopicContinuationService.class);
        var chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(factory.createChatClient()).thenReturn(chat);
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("""
                {"suggest":true,"category":"EVENT","title":"木工","content":"准备做书架","topicKey":"木工","importance":3,"reason":"用户分享项目","continueLater":true}
                """);
        var service = new MemorySuggestionExtractionService(Runnable::run, factory, memory, new ObjectMapper());
        service.setContinuationService(topics);
        service.schedule(new MemorySuggestionExtractionService.SuggestionTurn(UUID.randomUUID(), null, "我正在准备木工书架", "收到"));
        verify(memory).suggest(any());
        org.mockito.Mockito.verifyNoInteractions(topics);
    }

    @Test
    void storesAtMostOnePendingSuggestionBoundToTheCompletedTurn() {
        LlmRuntimeClientFactory factory = mock(LlmRuntimeClientFactory.class);
        LongTermMemoryService memoryService = mock(LongTermMemoryService.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(factory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("""
                {"suggest":true,"category":"USER_PROFILE","title":"称呼偏好","content":"用户喜欢被称为阿俊","topicKey":"称呼偏好","importance":4,"reason":"用户明确表达"}
                """);
        MemorySuggestionExtractionService service = new MemorySuggestionExtractionService(
                Runnable::run, factory, memoryService, new ObjectMapper()
        );
        UUID turnId = UUID.randomUUID();

        service.schedule(new MemorySuggestionExtractionService.SuggestionTurn(
                turnId, null, "以后叫我阿俊", "好的"
        ));

        var captor = org.mockito.ArgumentCaptor.forClass(LongTermMemoryService.MemorySuggestionCommand.class);
        verify(memoryService).suggest(captor.capture());
        assertThat(captor.getValue().sourceTurnId()).isEqualTo(turnId);
        assertThat(captor.getValue().memory().scopeType()).isEqualTo(MemoryScopeType.GLOBAL);
        assertThat(captor.getValue().memory().topicKey()).isEqualTo("称呼偏好");
    }

    @Test
    void malformedProviderResultHasNoSideEffect() {
        LlmRuntimeClientFactory factory = mock(LlmRuntimeClientFactory.class);
        LongTermMemoryService memoryService = mock(LongTermMemoryService.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(factory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("not-json");
        MemorySuggestionExtractionService service = new MemorySuggestionExtractionService(
                Runnable::run, factory, memoryService, new ObjectMapper()
        );

        service.schedule(new MemorySuggestionExtractionService.SuggestionTurn(
                UUID.randomUUID(), UUID.randomUUID(), "普通聊天", "普通回答"
        ));

        verify(memoryService, never()).suggest(any());
    }

    @Test
    void marksAnExplicitInterestForProactiveUseAfterConfirmation() {
        LlmRuntimeClientFactory factory = mock(LlmRuntimeClientFactory.class);
        LongTermMemoryService memoryService = mock(LongTermMemoryService.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(factory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("""
                {"suggest":true,"category":"USER_PROFILE","title":"AI 兴趣","content":"用户喜欢关注 AI 技术","topicKey":"AI 技术","importance":4,"reason":"用户明确表达兴趣","allowProactiveMention":true}
                """);
        MemorySuggestionExtractionService service = new MemorySuggestionExtractionService(
                Runnable::run, factory, memoryService, new ObjectMapper()
        );

        service.schedule(new MemorySuggestionExtractionService.SuggestionTurn(
                UUID.randomUUID(), UUID.randomUUID(), "我平时挺关注 AI 技术", "记住了"
        ));

        var captor = org.mockito.ArgumentCaptor.forClass(LongTermMemoryService.MemorySuggestionCommand.class);
        verify(memoryService).suggest(captor.capture());
        assertThat(captor.getValue().memory().allowProactiveMention()).isTrue();
    }
}
