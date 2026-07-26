package com.kj.stackchan.speech;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.conversation.GenerationStart;
import com.kj.stackchan.conversation.GenerationStatus;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.llm.ResolvedLlmSettings;
import com.kj.stackchan.memory.CompanionPromptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceTurnServiceTest {

    @Mock
    private SpeechRuntimeClient speechRuntimeClient;
    @Mock
    private DeviceVoiceConversationService deviceVoiceConversationService;
    @Mock
    private ConversationService conversationService;
    @Mock
    private LlmRuntimeClientFactory llmRuntimeClientFactory;
    @Mock
    private LlmSettingsService llmSettingsService;
    @Mock
    private CompanionPromptService companionPromptService;
    @Mock
    private VoiceTurnDiagnosticsService diagnosticsService;
    private final VoiceTurnCancellationService cancellationService =
            new VoiceTurnCancellationService(Clock.systemUTC());

    @Test
    void runsAsrLlmPersistenceAndTtsForOneDeviceConversation() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        byte[] input = new byte[64];
        byte[] replyAudio = new byte[44];
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(speechRuntimeClient.transcribe(input)).thenReturn("提醒我拿外卖");
        when(deviceVoiceConversationService.getOrCreateConversationId(deviceId)).thenReturn(conversationId);
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(eq(conversationId), any(UUID.class), eq("提醒我拿外卖")))
                .thenReturn(new GenerationStart(
                        conversationId, userMessageId, assistantMessageId, false, GenerationStatus.STREAMING, ""
                ));
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.com/v1", "model", "prompt", "secret"
        ));
        when(chatClient.prompt()
                .system("prompt\n当前是机器人语音对话。请直接使用简体中文回答，不要使用 Markdown。\n")
                .messages(List.of())
                .user("提醒我拿外卖")
                .stream()
                .content())
                .thenReturn(Flux.just("好的，", "记得去拿外卖。"));
        when(speechRuntimeClient.synthesize("好的，记得去拿外卖。")).thenReturn(replyAudio);

        VoiceTurnService.VoiceTurnResult result = service().handle(deviceId, input);

        assertThat(result.transcript()).isEqualTo("提醒我拿外卖");
        assertThat(result.reply()).isEqualTo("好的，记得去拿外卖。");
        assertThat(result.wavAudio()).isSameAs(replyAudio);
        verify(conversationService).completeGeneration(assistantMessageId, "好的，记得去拿外卖。");
        verify(diagnosticsService).recordServerStage(
                eq(deviceId), any(UUID.class), eq(VoiceTurnStage.REQUEST_RECEIVED), eq(null)
        );
        verify(diagnosticsService).recordServerStage(
                eq(deviceId), any(UUID.class), eq(VoiceTurnStage.TTS_COMPLETED), eq(null)
        );
    }

    @Test
    void sendsTheCompleteMultiSentenceLlmReplyToTtsAndHistory() {
        UUID deviceId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        byte[] input = new byte[64];
        byte[] replyAudio = new byte[44];
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(speechRuntimeClient.transcribe(input)).thenReturn("告诉我今天该做什么");
        when(deviceVoiceConversationService.getOrCreateConversationId(deviceId)).thenReturn(conversationId);
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(eq(conversationId), any(UUID.class), eq("告诉我今天该做什么")))
                .thenReturn(new GenerationStart(
                        conversationId,
                        UUID.randomUUID(),
                        assistantMessageId,
                        false,
                        GenerationStatus.STREAMING,
                        ""
                ));
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.com/v1", "model", "prompt", "secret"
        ));
        when(chatClient.prompt()
                .system(anyString())
                .messages(List.of())
                .user("告诉我今天该做什么")
                .stream()
                .content())
                .thenReturn(Flux.just(
                        "先完成最重要的一件事。",
                        "然后检查剩余安排，",
                        "最后留出休息时间。"
                ));
        when(speechRuntimeClient.synthesize("先完成最重要的一件事。然后检查剩余安排，最后留出休息时间。"))
                .thenReturn(replyAudio);

        VoiceTurnService.VoiceTurnResult result = service().handle(deviceId, input);

        assertThat(result.reply()).isEqualTo("先完成最重要的一件事。然后检查剩余安排，最后留出休息时间。");
        assertThat(result.wavAudio()).isSameAs(replyAudio);
        verify(conversationService).completeGeneration(
                assistantMessageId,
                "先完成最重要的一件事。然后检查剩余安排，最后留出休息时间。"
        );
    }

    @Test
    void rejectsATurnCancelledBeforeItsHttpRequestArrives() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        cancellationService.cancel(deviceId, turnId);

        assertThatThrownBy(() -> service().handle(deviceId, turnId, new byte[64]))
                .isInstanceOf(VoiceTurnCancelledException.class);

        verifyNoInteractions(speechRuntimeClient, conversationService, llmRuntimeClientFactory);
    }

    @Test
    void interruptsStreamingGenerationAndSkipsTtsWhenTheDeviceCancels() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        byte[] input = new byte[64];
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(speechRuntimeClient.transcribe(input)).thenReturn("测试取消");
        when(deviceVoiceConversationService.getOrCreateConversationId(deviceId)).thenReturn(conversationId);
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(eq(conversationId), any(UUID.class), eq("测试取消")))
                .thenReturn(new GenerationStart(
                        conversationId,
                        UUID.randomUUID(),
                        assistantMessageId,
                        false,
                        GenerationStatus.STREAMING,
                        ""
                ));
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.com/v1", "model", "prompt", "secret"
        ));
        when(chatClient.prompt()
                .system(anyString())
                .messages(List.of())
                .user("测试取消")
                .stream()
                .content())
                .thenReturn(Flux.concat(
                        Flux.just("部分回答"),
                        Flux.defer(() -> {
                            cancellationService.cancel(deviceId, turnId);
                            return Flux.empty();
                        })
                ));

        assertThatThrownBy(() -> service().handle(deviceId, turnId, input))
                .isInstanceOf(VoiceTurnCancelledException.class);

        verify(conversationService).interruptGeneration(assistantMessageId, "部分回答");
        verify(conversationService, never()).completeGeneration(eq(assistantMessageId), anyString());
        verify(conversationService, never()).failGeneration(eq(assistantMessageId), anyString(), anyString());
        verify(speechRuntimeClient, never()).synthesize(anyString());
        verify(diagnosticsService).recordServerStage(deviceId, turnId, VoiceTurnStage.CANCELLED, null);
    }

    @Test
    void interruptsTheAssistantMessageWhenCancellationArrivesDuringTts() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        byte[] input = new byte[64];
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(speechRuntimeClient.transcribe(input)).thenReturn("测试合成取消");
        when(deviceVoiceConversationService.getOrCreateConversationId(deviceId)).thenReturn(conversationId);
        when(conversationService.loadHistory(conversationId)).thenReturn(List.of());
        when(conversationService.startGeneration(eq(conversationId), any(UUID.class), eq("测试合成取消")))
                .thenReturn(new GenerationStart(
                        conversationId,
                        UUID.randomUUID(),
                        assistantMessageId,
                        false,
                        GenerationStatus.STREAMING,
                        ""
                ));
        when(llmRuntimeClientFactory.createChatClient()).thenReturn(chatClient);
        when(llmSettingsService.resolveForInvocation()).thenReturn(new ResolvedLlmSettings(
                "https://example.com/v1", "model", "prompt", "secret"
        ));
        when(chatClient.prompt()
                .system(anyString())
                .messages(List.of())
                .user("测试合成取消")
                .stream()
                .content())
                .thenReturn(Flux.just("已生成回答"));
        when(speechRuntimeClient.synthesize("已生成回答")).thenAnswer(ignored -> {
            cancellationService.cancel(deviceId, turnId);
            return new byte[44];
        });

        assertThatThrownBy(() -> service().handle(deviceId, turnId, input))
                .isInstanceOf(VoiceTurnCancelledException.class);

        verify(conversationService).interruptGeneration(assistantMessageId, "已生成回答");
        verify(conversationService, never()).completeGeneration(eq(assistantMessageId), anyString());
        verify(conversationService, never()).failGeneration(eq(assistantMessageId), anyString(), anyString());
        verify(diagnosticsService).recordServerStage(deviceId, turnId, VoiceTurnStage.CANCELLED, null);
    }

    private VoiceTurnService service() {
        lenient().when(companionPromptService.assemble(any(UUID.class), anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, String.class)
                        + invocation.getArgument(2, String.class));
        return new VoiceTurnService(
                speechRuntimeClient,
                deviceVoiceConversationService,
                conversationService,
                llmRuntimeClientFactory,
                llmSettingsService,
                companionPromptService,
                diagnosticsService,
                cancellationService
        );
    }
}
