package com.kj.stackchan.speech;

import java.util.List;
import java.util.UUID;

import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.conversation.GenerationStart;
import com.kj.stackchan.conversation.GenerationStatus;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.llm.ResolvedLlmSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    private VoiceTurnDiagnosticsService diagnosticsService;

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
                .system("prompt\n当前是机器人语音对话。请直接使用简体中文简短回答，最多两句话，不要使用 Markdown。\n")
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

    private VoiceTurnService service() {
        return new VoiceTurnService(
                speechRuntimeClient,
                deviceVoiceConversationService,
                conversationService,
                llmRuntimeClientFactory,
                llmSettingsService,
                diagnosticsService
        );
    }
}
