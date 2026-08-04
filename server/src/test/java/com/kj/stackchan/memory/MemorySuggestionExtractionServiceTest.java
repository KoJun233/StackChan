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
}
