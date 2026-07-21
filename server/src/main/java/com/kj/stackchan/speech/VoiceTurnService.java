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
import org.springframework.stereotype.Service;

@Service
public class VoiceTurnService {

    private static final Duration VOICE_LLM_TIMEOUT = Duration.ofSeconds(30);
    private static final String VOICE_SYSTEM_INSTRUCTION = """

            当前是机器人语音对话。请直接使用简体中文简短回答，最多两句话，不要使用 Markdown。
            """;

    private final SpeechRuntimeClient speechRuntimeClient;
    private final DeviceVoiceConversationService deviceVoiceConversationService;
    private final ConversationService conversationService;
    private final LlmRuntimeClientFactory llmRuntimeClientFactory;
    private final LlmSettingsService llmSettingsService;

    public VoiceTurnService(
            SpeechRuntimeClient speechRuntimeClient,
            DeviceVoiceConversationService deviceVoiceConversationService,
            ConversationService conversationService,
            LlmRuntimeClientFactory llmRuntimeClientFactory,
            LlmSettingsService llmSettingsService
    ) {
        this.speechRuntimeClient = speechRuntimeClient;
        this.deviceVoiceConversationService = deviceVoiceConversationService;
        this.conversationService = conversationService;
        this.llmRuntimeClientFactory = llmRuntimeClientFactory;
        this.llmSettingsService = llmSettingsService;
    }

    public VoiceTurnResult handle(UUID deviceId, byte[] wavAudio) {
        String transcript = speechRuntimeClient.transcribe(wavAudio).trim();
        if (transcript.isBlank()) {
            throw new VoiceInputException("没有识别到清晰语音");
        }

        UUID conversationId = deviceVoiceConversationService.getOrCreateConversationId(deviceId);
        List<ConversationMessageSnapshot> history = conversationService.loadHistory(conversationId);
        GenerationStart start = conversationService.startGeneration(conversationId, UUID.randomUUID(), transcript);
        String reply = "";
        try {
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
            byte[] audio = speechRuntimeClient.synthesize(reply);
            return new VoiceTurnResult(transcript, reply, audio);
        } catch (RuntimeException exception) {
            conversationService.failGeneration(
                    start.assistantMessageId(),
                    exception instanceof LlmProviderUnavailableException ? "provider_unavailable" : "voice_turn_failed",
                    reply
            );
            throw exception;
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
