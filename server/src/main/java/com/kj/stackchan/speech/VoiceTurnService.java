package com.kj.stackchan.speech;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.conversation.GenerationStart;
import com.kj.stackchan.conversation.MessageRole;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.llm.LlmSettingsService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VoiceTurnService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceTurnService.class);
    private static final Duration VOICE_LLM_TIMEOUT = Duration.ofSeconds(30);
    private static final String VOICE_SYSTEM_INSTRUCTION = """

            当前是机器人语音对话。请直接使用简体中文简短回答，最多两句话，不要使用 Markdown。
            """;

    private final SpeechRuntimeClient speechRuntimeClient;
    private final DeviceVoiceConversationService deviceVoiceConversationService;
    private final ConversationService conversationService;
    private final LlmRuntimeClientFactory llmRuntimeClientFactory;
    private final LlmSettingsService llmSettingsService;
    private final VoiceTurnDiagnosticsService diagnosticsService;

    public VoiceTurnService(
            SpeechRuntimeClient speechRuntimeClient,
            DeviceVoiceConversationService deviceVoiceConversationService,
            ConversationService conversationService,
            LlmRuntimeClientFactory llmRuntimeClientFactory,
            LlmSettingsService llmSettingsService,
            VoiceTurnDiagnosticsService diagnosticsService
    ) {
        this.speechRuntimeClient = speechRuntimeClient;
        this.deviceVoiceConversationService = deviceVoiceConversationService;
        this.conversationService = conversationService;
        this.llmRuntimeClientFactory = llmRuntimeClientFactory;
        this.llmSettingsService = llmSettingsService;
        this.diagnosticsService = diagnosticsService;
    }

    public VoiceTurnResult handle(UUID deviceId, byte[] wavAudio) {
        return handle(deviceId, UUID.randomUUID(), wavAudio);
    }

    public VoiceTurnResult handle(UUID deviceId, UUID turnId, byte[] wavAudio) {
        recordStage(deviceId, turnId, VoiceTurnStage.REQUEST_RECEIVED, null);
        VoiceTurnStage lastCompletedStage = VoiceTurnStage.REQUEST_RECEIVED;
        GenerationStart start = null;
        String reply = "";
        try {
            String transcript = speechRuntimeClient.transcribe(wavAudio).trim();
            if (transcript.isBlank()) {
                throw new VoiceInputException("没有识别到清晰语音");
            }
            recordStage(deviceId, turnId, VoiceTurnStage.ASR_COMPLETED, null);
            lastCompletedStage = VoiceTurnStage.ASR_COMPLETED;

            UUID conversationId = deviceVoiceConversationService.getOrCreateConversationId(deviceId);
            List<ConversationMessageSnapshot> history = conversationService.loadHistory(conversationId);
            start = conversationService.startGeneration(conversationId, UUID.randomUUID(), transcript);
            List<Message> modelHistory = history.stream().map(this::toModelMessage).toList();
            reply = llmRuntimeClientFactory.createChatClient()
                    .prompt()
                    .system(llmSettingsService.resolveForInvocation().systemPrompt() + VOICE_SYSTEM_INSTRUCTION)
                    .messages(modelHistory)
                    .user(transcript)
                    .stream()
                    .content()
                    .collect(Collectors.joining())
                    .timeout(VOICE_LLM_TIMEOUT)
                    .onErrorMap(TimeoutException.class, ignored -> new LlmProviderUnavailableException())
                    .block();
            if (reply == null || reply.isBlank()) {
                throw new LlmProviderUnavailableException();
            }
            conversationService.completeGeneration(start.assistantMessageId(), reply);
            recordStage(deviceId, turnId, VoiceTurnStage.LLM_COMPLETED, null);
            lastCompletedStage = VoiceTurnStage.LLM_COMPLETED;
            byte[] audio = speechRuntimeClient.synthesize(reply);
            recordStage(deviceId, turnId, VoiceTurnStage.TTS_COMPLETED, null);
            return new VoiceTurnResult(transcript, reply, audio);
        } catch (RuntimeException exception) {
            if (start != null) {
                conversationService.failGeneration(
                        start.assistantMessageId(),
                        exception instanceof LlmProviderUnavailableException ? "provider_unavailable" : "voice_turn_failed",
                        reply
                );
            }
            recordStage(
                    deviceId,
                    turnId,
                    VoiceTurnStage.FAILED,
                    failureCode(exception, lastCompletedStage)
            );
            throw exception;
        }
    }

    private VoiceTurnFailureCode failureCode(RuntimeException exception, VoiceTurnStage lastCompletedStage) {
        if (exception instanceof VoiceInputException) {
            return VoiceTurnFailureCode.NO_SPEECH;
        }
        if (exception instanceof LlmProviderUnavailableException) {
            return VoiceTurnFailureCode.LLM_UNAVAILABLE;
        }
        if (exception instanceof SpeechProviderUnavailableException) {
            return lastCompletedStage == VoiceTurnStage.LLM_COMPLETED
                    ? VoiceTurnFailureCode.TTS_UNAVAILABLE
                    : VoiceTurnFailureCode.ASR_UNAVAILABLE;
        }
        return VoiceTurnFailureCode.INTERNAL_ERROR;
    }

    private void recordStage(
            UUID deviceId,
            UUID turnId,
            VoiceTurnStage stage,
            VoiceTurnFailureCode failureCode
    ) {
        try {
            diagnosticsService.recordServerStage(deviceId, turnId, stage, failureCode);
        } catch (RuntimeException exception) {
            logger.warn("Voice turn diagnostics unavailable for turn={} stage={}", turnId, stage);
        }
    }

    private Message toModelMessage(ConversationMessageSnapshot message) {
        return message.role() == MessageRole.USER
                ? new UserMessage(message.content())
                : new AssistantMessage(message.content());
    }

    public record VoiceTurnResult(String transcript, String reply, byte[] wavAudio) {
    }
}
