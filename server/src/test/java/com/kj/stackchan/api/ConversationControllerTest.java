package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.ConversationNotFoundException;
import com.kj.stackchan.conversation.ConversationSnapshot;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.conversation.GenerationStart;
import com.kj.stackchan.conversation.GenerationStatus;
import com.kj.stackchan.conversation.MessageRole;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.llm.ResolvedLlmSettings;
import com.kj.stackchan.memory.CompanionPromptService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversationController.class)
@Import(SecurityConfiguration.class)
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @MockitoBean
    private LlmRuntimeClientFactory llmRuntimeClientFactory;

    @MockitoBean
    private LlmSettingsService llmSettingsService;

    @MockitoBean
    private CompanionPromptService companionPromptService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @BeforeEach
    void preserveTheConfiguredBasePromptByDefault() {
        lenient().when(companionPromptService.assemble(any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class));
    }

    @Test
    void streamsOrderedEventsAndPersistsTheCompletedAssistantMessage() throws Exception {
        UUID conversationId = UUID.fromString("01d42d1a-00dd-4b88-b7df-18b8ac3dd350");
        UUID clientMessageId = UUID.fromString("2e0004b3-5146-4585-9bae-d15d3bd812d4");
        UUID userMessageId = UUID.fromString("cfc53ef6-81c9-49cf-890b-c509b3d9103e");
        UUID assistantMessageId = UUID.fromString("96c50203-0e6d-44ce-b759-b52be324ff58");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of(new ConversationMessageSnapshot(
                UUID.fromString("5f40e84f-0937-4cd0-96db-82d2d1e0f2df"),
                MessageRole.USER,
                "早上好",
                GenerationStatus.COMPLETED,
                Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T12:00:00Z")
        )));
        when(conversationService.startGeneration(conversationId, clientMessageId, "今天有点累"))
                .thenReturn(new GenerationStart(conversationId, userMessageId, assistantMessageId, false, GenerationStatus.STREAMING, ""));
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.invalid/v1", "qwen3.7-plus", "你是温柔的陪伴", "sk-secret"
        ));

        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("你是温柔的陪伴")).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user("今天有点累")).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("你", "好"));

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"今天有点累\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:completed")))
                .andExpect(content().string(containsString("\"content\":\"你好\"")));

        verify(conversationService).completeGeneration(assistantMessageId, "你好");
        verify(requestSpec).messages(anyList());
    }

    @Test
    void emitsASafeErrorEventAndPersistsPartialContentWhenTheProviderFails() throws Exception {
        UUID conversationId = UUID.fromString("06a88f60-1bb5-4c5e-af9a-5cb8f4c77977");
        UUID clientMessageId = UUID.fromString("6f386f32-ae7d-45e2-bc3e-961ac4b73a5a");
        UUID assistantMessageId = UUID.fromString("04a8f81b-6dce-4332-91b9-2a21aa3a1a23");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(conversationId, clientMessageId, "在吗"))
                .thenReturn(new GenerationStart(conversationId, UUID.randomUUID(), assistantMessageId, false, GenerationStatus.STREAMING, ""));
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.invalid/v1", "qwen3.7-plus", "你是温柔的陪伴", "sk-secret"
        ));

        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("你是温柔的陪伴")).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user("在吗")).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.concat(
                Flux.just("我在"),
                Mono.error(new LlmProviderUnavailableException())
        ));

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"在吗\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("\"code\":\"provider_unavailable\"")))
                .andExpect(content().string(containsString("模型服务暂时不可用")));

        verify(conversationService).failGeneration(assistantMessageId, "provider_unavailable", "我在");
    }

    @Test
    void synchronousClientCreationFailureMarksTheAssistantFailedAndEmitsError() throws Exception {
        UUID conversationId = UUID.fromString("62b9630f-28d7-43f6-a0b5-cb908aa9b8d1");
        UUID clientMessageId = UUID.fromString("a206bc55-4dff-44ed-8657-ebf7b030956b");
        UUID userMessageId = UUID.fromString("640d8fe5-eaeb-4aaa-9fe3-bcabcc431dea");
        UUID assistantMessageId = UUID.fromString("6b8aac0a-6984-4c7d-a28d-ae79a459f246");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(any(), any(), any()))
                .thenReturn(new GenerationStart(conversationId, userMessageId, assistantMessageId, false, GenerationStatus.STREAMING, ""));
        when(llmRuntimeClientFactory.createChatClient())
                .thenThrow(new LlmProviderUnavailableException());

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"test\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")));
        verify(conversationService).failGeneration(assistantMessageId, "provider_unavailable", "");
    }

    @Test
    void completionPersistenceFailureMarksTheAssistantFailedAndEmitsSafeError() throws Exception {
        UUID conversationId = UUID.fromString("3c57d9eb-c42e-4b12-b582-54b995472e13");
        UUID clientMessageId = UUID.fromString("349747f6-e2dc-4269-8c11-d02db4364963");
        UUID assistantMessageId = UUID.fromString("ba798709-07dc-4c06-9d88-c680e96a516b");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(conversationId, clientMessageId, "test"))
                .thenReturn(new GenerationStart(conversationId, UUID.randomUUID(), assistantMessageId, false, GenerationStatus.STREAMING, ""));
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.invalid/v1", "model", "system", "key"
        ));
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system")).thenReturn(requestSpec);
        when(requestSpec.messages(anyList())).thenReturn(requestSpec);
        when(requestSpec.user("test")).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("part"));
        doThrow(new IllegalStateException("persistence unavailable"))
                .when(conversationService).completeGeneration(assistantMessageId, "part");

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"test\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString("\"code\":\"generation_failed\"")))
                .andExpect(content().string(containsString("模型服务暂时不可用")));
        verify(conversationService).failGeneration(assistantMessageId, "generation_failed", "part");
    }

    @Test
    void createsAndReadsPersistedConversations() throws Exception {
        UUID conversationId = UUID.fromString("d2b1eeb0-ea84-4e5a-a834-07b15c4f7aa3");
        ConversationSnapshot conversation = new ConversationSnapshot(
                conversationId,
                "新对话",
                Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T12:00:00Z")
        );
        when(conversationService.createConversation()).thenReturn(conversation);
        when(conversationService.listConversations()).thenReturn(List.of(conversation));
        when(conversationService.getMessages(conversationId)).thenReturn(List.of(new ConversationMessageSnapshot(
                UUID.fromString("cd2a58b1-4092-494e-bbde-3517f15a6e43"),
                MessageRole.USER,
                "你好",
                GenerationStatus.COMPLETED,
                Instant.parse("2026-07-17T12:00:00Z"),
                Instant.parse("2026-07-17T12:00:00Z")
        )));

        mockMvc.perform(post("/api/v1/conversations")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":"d2b1eeb0-ea84-4e5a-a834-07b15c4f7aa3","title":"新对话","createdAt":"2026-07-17T12:00:00Z","updatedAt":"2026-07-17T12:00:00Z"}
                        """));
        mockMvc.perform(get("/api/v1/conversations").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":"d2b1eeb0-ea84-4e5a-a834-07b15c4f7aa3","title":"新对话"}]
                        """));
        mockMvc.perform(get("/api/v1/conversations/{conversationId}/messages", conversationId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"role":"USER","content":"你好","generationStatus":"COMPLETED"}]
                        """));
    }

    @Test
    void replaysCompletedStateForADuplicateWithoutInvokingTheModel() throws Exception {
        UUID conversationId = UUID.fromString("f790e5c9-9c4f-446f-97e3-0aaf0da607f5");
        UUID clientMessageId = UUID.fromString("bde4c468-0d03-44d7-8ee4-5a2bfa408fa1");
        UUID userMessageId = UUID.fromString("a495c35d-d7f6-4298-a90c-e5c18844ce95");
        UUID assistantMessageId = UUID.fromString("b373af26-fb7b-4ec0-88b4-dc3ac59b51bf");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(conversationId, clientMessageId, "重试这条消息"))
                .thenReturn(new GenerationStart(
                        conversationId, userMessageId, assistantMessageId, true, GenerationStatus.COMPLETED, "already done"
                ));

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"重试这条消息\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:completed")))
                .andExpect(content().string(containsString("already done")));

        verifyNoInteractions(llmRuntimeClientFactory, llmSettingsService);
    }

    @Test
    void replaysFailedStateForADuplicateWithoutInvokingTheModel() throws Exception {
        UUID conversationId = UUID.fromString("f790e5c9-9c4f-446f-97e3-0aaf0da607f5");
        UUID clientMessageId = UUID.fromString("bde4c468-0d03-44d7-8ee4-5a2bfa408fa1");
        UUID userMessageId = UUID.fromString("a495c35d-d7f6-4298-a90c-e5c18844ce95");
        UUID assistantMessageId = UUID.fromString("b373af26-fb7b-4ec0-88b4-dc3ac59b51bf");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(conversationId, clientMessageId, "重试这条消息"))
                .thenReturn(new GenerationStart(
                        conversationId, userMessageId, assistantMessageId, true, GenerationStatus.FAILED, "partial"
                ));

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"重试这条消息\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:error")))
                .andExpect(content().string(containsString(LlmProviderUnavailableException.SAFE_MESSAGE)));

        verifyNoInteractions(llmRuntimeClientFactory, llmSettingsService);
    }

    @Test
    void replaysInterruptedStateForADuplicateWithoutInvokingTheModel() throws Exception {
        UUID conversationId = UUID.fromString("f790e5c9-9c4f-446f-97e3-0aaf0da607f5");
        UUID clientMessageId = UUID.fromString("bde4c468-0d03-44d7-8ee4-5a2bfa408fa1");
        UUID userMessageId = UUID.fromString("a495c35d-d7f6-4298-a90c-e5c18844ce95");
        UUID assistantMessageId = UUID.fromString("b373af26-fb7b-4ec0-88b4-dc3ac59b51bf");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(conversationId, clientMessageId, "重试这条消息"))
                .thenReturn(new GenerationStart(
                        conversationId, userMessageId, assistantMessageId, true, GenerationStatus.INTERRUPTED, "partial reply"
                ));

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"重试这条消息\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(5_000);
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:interrupted")))
                .andExpect(content().string(containsString("partial reply")));

        verifyNoInteractions(llmRuntimeClientFactory, llmSettingsService);
    }

    @Test
    void replaysStreamingStateForADuplicateWithoutInvokingTheModel() throws Exception {
        UUID conversationId = UUID.fromString("f790e5c9-9c4f-446f-97e3-0aaf0da607f5");
        UUID clientMessageId = UUID.fromString("bde4c468-0d03-44d7-8ee4-5a2bfa408fa1");
        UUID userMessageId = UUID.fromString("a495c35d-d7f6-4298-a90c-e5c18844ce95");
        UUID assistantMessageId = UUID.fromString("b373af26-fb7b-4ec0-88b4-dc3ac59b51bf");
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(conversationId, clientMessageId, "重试这条消息"))
                .thenReturn(new GenerationStart(
                        conversationId, userMessageId, assistantMessageId, true, GenerationStatus.STREAMING, "partial reply"
                ));

        MvcResult result = mockMvc.perform(post("/api/v1/conversations/{conversationId}/messages:stream", conversationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"clientMessageId\":\"" + clientMessageId + "\",\"content\":\"重试这条消息\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(not(containsString("event:completed"))))
                .andExpect(content().string(not(containsString("event:error"))))
                .andExpect(content().string(not(containsString("event:interrupted"))));

        verifyNoInteractions(llmRuntimeClientFactory, llmSettingsService);
    }

    @Test
    void returnsNotFoundForAnUnknownConversation() throws Exception {
        UUID conversationId = UUID.fromString("ef79a20d-0a34-4b62-9d28-a5f0d5dd017e");
        when(conversationService.getMessages(conversationId)).thenThrow(new ConversationNotFoundException(conversationId));

        mockMvc.perform(get("/api/v1/conversations/{conversationId}/messages", conversationId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"code":"conversation_not_found","message":"未找到指定对话。"}
                        """, STRICT));
    }

    @Test
    void rejectsAnInvalidConversationIdentifierWithTheGenericSafeResponse() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/not-a-uuid/messages")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"invalid_request","message":"请求参数无效。"}
                        """, STRICT));
    }
}
