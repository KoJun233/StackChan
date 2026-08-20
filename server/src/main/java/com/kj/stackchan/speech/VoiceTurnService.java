package com.kj.stackchan.speech;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.kj.stackchan.agent.AgentChannel;
import com.kj.stackchan.agent.AgentInvocationContext;
import com.kj.stackchan.agent.AgentOrchestrator;
import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.ConversationService;
import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.conversation.GenerationStart;
import com.kj.stackchan.conversation.MessageRole;
import com.kj.stackchan.llm.LlmProviderUnavailableException;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.memory.CompanionPromptService;
import com.kj.stackchan.memory.CompletedTurnMemoryCoordinator;
import com.kj.stackchan.expression.DeviceExpressionService;
import com.kj.stackchan.expression.ExpressionSuggestionParser;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.voiceaction.VoiceActionCoordinator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class VoiceTurnService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceTurnService.class);
    private static final Duration VOICE_LLM_TIMEOUT = Duration.ofSeconds(30);
    private static final String VOICE_SYSTEM_INSTRUCTION = """

            当前是机器人语音对话。请直接使用简体中文回答，不要使用 Markdown。
            回答正文末尾另起一行追加且只追加一个隐藏表情标记：
            [[emotion:情绪:强度:秒数]]。情绪只能是 NEUTRAL、HAPPY、LOVING、SAD、ANGRY、
            SURPRISED、CONFUSED、SHY、TIRED、FOCUSED、NERVOUS、CONTENT；强度只能是
            WEAK、MEDIUM、STRONG；秒数只能是 5 到 15。标记不会展示或朗读，不能改写正文。
            """;

    private final SpeechRuntimeClient speechRuntimeClient;
    private final DeviceVoiceConversationService deviceVoiceConversationService;
    private final ConversationService conversationService;
    private final AgentOrchestrator agentOrchestrator;
    private final LlmSettingsService llmSettingsService;
    private final CompanionPromptService companionPromptService;
    private final VoiceTurnDiagnosticsService diagnosticsService;
    private final VoiceTurnCancellationService cancellationService;
    private final VoiceActionCoordinator voiceActionCoordinator;
    private final CompletedTurnMemoryCoordinator completedTurnMemoryCoordinator;
    private final VoiceReplySegmenter replySegmenter;
    private DeviceExpressionService deviceExpressionService;

    @Autowired(required = false)
    public void setDeviceExpressionService(DeviceExpressionService deviceExpressionService) {
        this.deviceExpressionService = deviceExpressionService;
    }

    @Autowired
    public VoiceTurnService(
            SpeechRuntimeClient speechRuntimeClient,
            DeviceVoiceConversationService deviceVoiceConversationService,
            ConversationService conversationService,
            AgentOrchestrator agentOrchestrator,
            LlmSettingsService llmSettingsService,
            CompanionPromptService companionPromptService,
            VoiceTurnDiagnosticsService diagnosticsService,
            VoiceTurnCancellationService cancellationService,
            VoiceActionCoordinator voiceActionCoordinator,
            CompletedTurnMemoryCoordinator completedTurnMemoryCoordinator,
            VoiceReplySegmenter replySegmenter
    ) {
        this.speechRuntimeClient = speechRuntimeClient;
        this.deviceVoiceConversationService = deviceVoiceConversationService;
        this.conversationService = conversationService;
        this.agentOrchestrator = agentOrchestrator;
        this.llmSettingsService = llmSettingsService;
        this.companionPromptService = companionPromptService;
        this.diagnosticsService = diagnosticsService;
        this.cancellationService = cancellationService;
        this.voiceActionCoordinator = voiceActionCoordinator;
        this.completedTurnMemoryCoordinator = completedTurnMemoryCoordinator;
        this.replySegmenter = replySegmenter;
    }

    public VoiceTurnService(
            SpeechRuntimeClient speechRuntimeClient,
            DeviceVoiceConversationService deviceVoiceConversationService,
            ConversationService conversationService,
            AgentOrchestrator agentOrchestrator,
            LlmSettingsService llmSettingsService,
            CompanionPromptService companionPromptService,
            VoiceTurnDiagnosticsService diagnosticsService,
            VoiceTurnCancellationService cancellationService
    ) {
        this(speechRuntimeClient, deviceVoiceConversationService, conversationService, agentOrchestrator,
                llmSettingsService, companionPromptService, diagnosticsService, cancellationService,
                null, null, new VoiceReplySegmenter());
    }

    public VoiceTurnService(
            SpeechRuntimeClient speechRuntimeClient,
            DeviceVoiceConversationService deviceVoiceConversationService,
            ConversationService conversationService,
            AgentOrchestrator agentOrchestrator,
            LlmSettingsService llmSettingsService,
            CompanionPromptService companionPromptService,
            VoiceTurnDiagnosticsService diagnosticsService,
            VoiceTurnCancellationService cancellationService,
            VoiceActionCoordinator voiceActionCoordinator,
            CompletedTurnMemoryCoordinator completedTurnMemoryCoordinator
    ) {
        this(speechRuntimeClient, deviceVoiceConversationService, conversationService, agentOrchestrator,
                llmSettingsService, companionPromptService, diagnosticsService, cancellationService,
                voiceActionCoordinator, completedTurnMemoryCoordinator, new VoiceReplySegmenter());
    }

    public VoiceTurnResult handle(UUID deviceId, byte[] wavAudio) {
        return handle(deviceId, UUID.randomUUID(), wavAudio);
    }

    public VoiceTurnResult handle(UUID deviceId, UUID turnId, byte[] wavAudio) {
        return handle(deviceId, turnId, wavAudio, null);
    }

    public void handleStreaming(
            UUID deviceId,
            UUID turnId,
            byte[] wavAudio,
            VoiceTurnSegmentSink segmentSink
    ) {
        if (segmentSink == null) throw new IllegalArgumentException("Voice turn segment sink is required");
        handle(deviceId, turnId, wavAudio, segmentSink);
    }

    private VoiceTurnResult handle(
            UUID deviceId,
            UUID turnId,
            byte[] wavAudio,
            VoiceTurnSegmentSink segmentSink
    ) {
        try (VoiceTurnCancellationService.CancellationHandle cancellation =
                     cancellationService.register(deviceId, turnId)) {
            cancellation.throwIfCancelled();
            recordStage(deviceId, turnId, VoiceTurnStage.REQUEST_RECEIVED, null);
            VoiceTurnStage lastCompletedStage = VoiceTurnStage.REQUEST_RECEIVED;
            GenerationStart start = null;
            String reply = "";
            List<UUID> usedMemoryIds = List.of();
            boolean extractMemorySuggestion = false;
            boolean generationCompleted = false;
            try {
                String transcript = speechRuntimeClient.transcribe(wavAudio).trim();
                cancellation.throwIfCancelled();
                if (transcript.isBlank()) {
                    throw new VoiceInputException("没有识别到清晰语音");
                }
                recordStage(deviceId, turnId, VoiceTurnStage.ASR_COMPLETED, null);
                lastCompletedStage = VoiceTurnStage.ASR_COMPLETED;
                if (segmentSink != null) {
                    segmentSink.start(transcript);
                    cancellation.throwIfCancelled();
                }

                UUID conversationId = deviceVoiceConversationService.getOrCreateConversationId(deviceId);
                UUID roleId = conversationService.roleId(conversationId);
                List<ConversationMessageSnapshot> history = conversationService.loadHistory(conversationId);
                start = conversationService.startGeneration(conversationId, UUID.randomUUID(), transcript);
                cancellation.throwIfCancelled();
                List<Message> modelHistory = history.stream().map(this::toModelMessage).toList();
                CompanionPromptService.PromptAssembly promptAssembly = companionPromptService.assembleWithMemoryContext(
                        conversationId,
                        llmSettingsService.resolveForInvocation().systemPrompt(),
                        VOICE_SYSTEM_INSTRUCTION,
                        transcript
                );
                if (promptAssembly == null) {
                    promptAssembly = new CompanionPromptService.PromptAssembly(
                            companionPromptService.assemble(
                                    conversationId,
                                    llmSettingsService.resolveForInvocation().systemPrompt(),
                                    VOICE_SYSTEM_INSTRUCTION
                            ),
                            List.of()
                    );
                }
                String systemPrompt = promptAssembly.prompt();
                VoiceActionCoordinator.ActionResult actionResult = voiceActionCoordinator == null ? null
                        : voiceActionCoordinator.handle(deviceId, conversationId, turnId, transcript);
                if (actionResult != null && actionResult.handled()) {
                    reply = actionResult.reply();
                } else {
                    usedMemoryIds = promptAssembly.memoryIds();
                    extractMemorySuggestion = true;
                    reply = agentOrchestrator.stream(new AgentOrchestrator.AgentRequest(
                                    new AgentInvocationContext(
                                            turnId,
                                            conversationId,
                                            deviceId,
                                            roleId,
                                            AgentChannel.VOICE
                                    ),
                                    systemPrompt,
                                    modelHistory,
                                    transcript
                            ))
                            .takeUntilOther(cancellation.cancellationSignal())
                            .filter(chunk -> chunk != null && !chunk.isEmpty())
                            .collect(Collectors.joining())
                            .timeout(VOICE_LLM_TIMEOUT)
                            .onErrorMap(TimeoutException.class, ignored -> new LlmProviderUnavailableException())
                            .block();
                }
                cancellation.throwIfCancelled();
                if (reply == null || reply.isBlank()) {
                    throw new LlmProviderUnavailableException();
                }
                ExpressionSuggestionParser.Suggestion expression = ExpressionSuggestionParser.parse(reply);
                reply = expression.reply();
                if (reply.isBlank()) {
                    throw new LlmProviderUnavailableException();
                }
                if (deviceExpressionService != null) {
                    deviceExpressionService.apply(deviceId, roleId, expression);
                }
                recordStage(deviceId, turnId, VoiceTurnStage.LLM_COMPLETED, null);
                lastCompletedStage = VoiceTurnStage.LLM_COMPLETED;
                cancellation.throwIfCancelled();
                byte[] audio = null;
                int segmentCount = 0;
                if (segmentSink == null) {
                    audio = speechRuntimeClient.synthesize(reply, roleId);
                    cancellation.throwIfCancelled();
                } else {
                    List<String> segments = replySegmenter.segment(reply);
                    if (segments.isEmpty()) throw new LlmProviderUnavailableException();
                    for (int index = 0; index < segments.size(); index++) {
                        cancellation.throwIfCancelled();
                        byte[] segmentAudio = speechRuntimeClient.synthesize(segments.get(index), roleId);
                        cancellation.throwIfCancelled();
                        segmentSink.audio(index, segmentAudio);
                        segmentCount++;
                        cancellation.throwIfCancelled();
                    }
                }
                conversationService.completeGeneration(start.assistantMessageId(), reply);
                generationCompleted = true;
                if (completedTurnMemoryCoordinator != null && extractMemorySuggestion) {
                    if (roleId == null || CompanionRoleEntity.DEFAULT_ROLE_ID.equals(roleId)) {
                        completedTurnMemoryCoordinator.complete(turnId, turnId, deviceId, transcript, reply, usedMemoryIds, true);
                    } else {
                        completedTurnMemoryCoordinator.complete(turnId, turnId, deviceId, roleId, transcript, reply, usedMemoryIds, true);
                    }
                }
                if (segmentSink != null) segmentSink.complete(segmentCount);
                recordStage(deviceId, turnId, VoiceTurnStage.TTS_COMPLETED, null);
                return new VoiceTurnResult(transcript, reply, audio);
            } catch (VoiceTurnCancelledException | VoiceTurnClientDisconnectedException exception) {
                if (start != null && !generationCompleted) {
                    conversationService.interruptGeneration(start.assistantMessageId(), reply);
                }
                recordStage(deviceId, turnId, VoiceTurnStage.CANCELLED, null);
                throw exception;
            } catch (RuntimeException exception) {
                if (start != null && !generationCompleted) {
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
