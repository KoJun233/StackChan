package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
import reactor.core.scheduler.Schedulers;

@Service
public class VoiceTurnService {

    private static final Logger logger = LoggerFactory.getLogger(VoiceTurnService.class);
    private static final Duration VOICE_LLM_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_SPOKEN_REPLY_CODE_POINTS = 160;
    private static final String VOICE_SYSTEM_INSTRUCTION = """

            当前是机器人语音对话。请直接使用简体中文回答，不要使用 Markdown。
            对话历史只包含最近三十分钟内完整结束的至多四轮对话。用户用“刚才”“还是”“又”
            “然后”“那个”等省略说法时，优先结合最近一轮自然承接，不要复述历史。历史不足以确定
            对象时只问一句简短澄清；不得根据长期记忆补造本次游戏、输赢、人物或事件。
            对随口吐槽、输赢分享和简短闲聊，默认只回应一到两句、整个回答尽量不超过四十个汉字，先自然接住用户的情绪或话题；
            用户没有请求建议时不要主动说教，也不要为了延续对话而每次反问。只有用户明确要求解释、
            分析、步骤或完整事实时才展开，必要的 Tool 查询结果必须完整准确。即使展开，语音正文也必须
            控制在一百六十个汉字以内，并优先给出适合直接听取的摘要。
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
    private final VoiceConversationContextPolicy conversationContextPolicy;
    private final RecentProactiveContextService recentProactiveContextService;
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
            VoiceConversationContextPolicy conversationContextPolicy,
            RecentProactiveContextService recentProactiveContextService
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
        this.conversationContextPolicy = conversationContextPolicy;
        this.recentProactiveContextService = recentProactiveContextService;
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
                null, null, new VoiceConversationContextPolicy(Clock.systemUTC()), null);
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
                voiceActionCoordinator, completedTurnMemoryCoordinator,
                new VoiceConversationContextPolicy(Clock.systemUTC()), null);
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

    public void handleLiveStreaming(
            UUID deviceId,
            UUID turnId,
            byte[] streamingWavAudio,
            VoiceTurnSegmentSink segmentSink
    ) {
        if (segmentSink == null) throw new IllegalArgumentException("Voice turn segment sink is required");
        handle(
                deviceId,
                turnId,
                WavPcmAudio.normalizeUploadedMono16KhzWav(streamingWavAudio),
                segmentSink
        );
    }

    private VoiceTurnResult handle(
            UUID deviceId,
            UUID turnId,
            byte[] wavAudio,
            VoiceTurnSegmentSink segmentSink
    ) {
        long requestStartedNanos = System.nanoTime();
        try (VoiceTurnCancellationService.CancellationHandle cancellation =
                     cancellationService.register(deviceId, turnId)) {
            cancellation.throwIfCancelled();
            recordStage(deviceId, turnId, VoiceTurnStage.REQUEST_RECEIVED, null);
            VoiceTurnStage lastCompletedStage = VoiceTurnStage.REQUEST_RECEIVED;
            GenerationStart start = null;
            String reply = "";
            StringBuilder streamedReply = new StringBuilder();
            List<UUID> usedMemoryIds = List.of();
            boolean extractMemorySuggestion = false;
            boolean generationCompleted = false;
            try {
                long asrStartedNanos = System.nanoTime();
                String transcript = speechRuntimeClient.transcribe(wavAudio).trim();
                cancellation.throwIfCancelled();
                if (transcript.isBlank()) {
                    throw new VoiceInputException("没有识别到清晰语音");
                }
                recordStage(deviceId, turnId, VoiceTurnStage.ASR_COMPLETED, null);
                logger.info(
                        "Voice turn timing: turn_id={} stage=ASR_COMPLETED stage_ms={} request_ms={}",
                        turnId,
                        elapsedMillis(asrStartedNanos),
                        elapsedMillis(requestStartedNanos)
                );
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
                VoiceDialogueControl dialogue = VoiceDialogueControl.parse(transcript);
                boolean newTopic = dialogue.kind() == VoiceDialogueControl.Kind.NEW_TOPIC;
                boolean ending = dialogue.closesTopic();
                Instant topicBoundary = (newTopic || ending)
                        ? conversationService.resetVoiceTopic(conversationId, start.userMessageId())
                        : conversationService.voiceTopicBoundary(conversationId);
                topicBoundary = conversationContextPolicy.topicBoundary(history, topicBoundary);
                if ((newTopic || ending || dialogue.kind() == VoiceDialogueControl.Kind.CORRECTION) && voiceActionCoordinator != null) {
                    voiceActionCoordinator.cancelPendingOperation(deviceId, conversationId);
                }
                String voiceInstruction = VOICE_SYSTEM_INSTRUCTION + dialogue.guidance();
                List<Message> modelHistory = ((newTopic || ending) ? List.<ConversationMessageSnapshot>of()
                        : conversationContextPolicy.select(history, topicBoundary)).stream()
                        .map(this::toModelMessage)
                        .toList();
                VoiceActionCoordinator.ActionResult actionResult = ending
                        ? new VoiceActionCoordinator.ActionResult(dialogue.kind() == VoiceDialogueControl.Kind.DECLINE
                            ? "好的，先不聊这个。" : "好的，先聊到这里。", true)
                        : voiceActionCoordinator == null ? null
                        : voiceActionCoordinator.handle(deviceId, conversationId, turnId, transcript);
                if (actionResult != null && actionResult.handled()) {
                    reply = actionResult.reply();
                    logger.info(
                            "Voice turn timing: turn_id={} stage=ACTION_COMPLETED request_ms={}",
                            turnId,
                            elapsedMillis(requestStartedNanos)
                    );
                } else {
                    String proactiveContext = "";
                    if (recentProactiveContextService != null && !newTopic) {
                        proactiveContext = topicBoundary == null ? recentProactiveContextService.context(deviceId, roleId)
                                : recentProactiveContextService.context(deviceId, roleId, topicBoundary);
                    }
                    if (proactiveContext == null) proactiveContext = "";
                    if (dialogue.kind() == VoiceDialogueControl.Kind.CONTINUE
                            && modelHistory.isEmpty() && proactiveContext.isBlank()) {
                        reply = "你想让我继续讲哪个话题？";
                    } else {
                        CompanionPromptService.PromptAssembly promptAssembly = companionPromptService.assembleWithMemoryContext(
                                conversationId, llmSettingsService.resolveForInvocation().systemPrompt(), voiceInstruction, transcript);
                        if (promptAssembly == null) {
                            promptAssembly = new CompanionPromptService.PromptAssembly(companionPromptService.assemble(
                                    conversationId, llmSettingsService.resolveForInvocation().systemPrompt(), voiceInstruction), List.of());
                        }
                        String systemPrompt = promptAssembly.prompt() + proactiveContext;
                        usedMemoryIds = promptAssembly.memoryIds();
                        extractMemorySuggestion = true;
                        long agentStartedNanos = System.nanoTime();
                        logger.info(
                                "Voice turn timing: turn_id={} stage=AGENT_STARTED request_ms={}",
                                turnId,
                                elapsedMillis(requestStartedNanos)
                        );
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
                                .timeout(VOICE_LLM_TIMEOUT)
                                .onErrorMap(TimeoutException.class, ignored -> new LlmProviderUnavailableException())
                                .publishOn(Schedulers.boundedElastic(), 32)
                                .doOnNext(streamedReply::append)
                                .collect(Collectors.joining())
                                .block();
                        logger.info(
                                "Voice turn timing: turn_id={} stage=AGENT_COMPLETED stage_ms={} request_ms={}",
                                turnId,
                                elapsedMillis(agentStartedNanos),
                                elapsedMillis(requestStartedNanos)
                        );
                    }
                }
                cancellation.throwIfCancelled();
                if (reply == null || reply.isBlank()) {
                    throw new LlmProviderUnavailableException();
                }
                ExpressionSuggestionParser.Suggestion expression = ExpressionSuggestionParser.parse(reply);
                reply = boundSingleSpokenReply(expression.reply());
                if (reply.isBlank()) {
                    throw new LlmProviderUnavailableException();
                }
                if (deviceExpressionService != null) {
                    deviceExpressionService.apply(deviceId, roleId, expression);
                }
                recordStage(deviceId, turnId, VoiceTurnStage.LLM_COMPLETED, null);
                lastCompletedStage = VoiceTurnStage.LLM_COMPLETED;
                cancellation.throwIfCancelled();
                long ttsStartedNanos = System.nanoTime();
                byte[] audio = null;
                int segmentCount = 0;
                if (segmentSink == null) {
                    long segmentStartedNanos = System.nanoTime();
                    audio = speechRuntimeClient.synthesize(reply, roleId);
                    cancellation.throwIfCancelled();
                    logger.info(
                            "Voice turn timing: turn_id={} stage=TTS_AUDIO_READY sequence=0 characters={} "
                                    + "bytes={} stage_ms={} request_ms={}",
                            turnId,
                            reply.codePointCount(0, reply.length()),
                            audio.length,
                            elapsedMillis(segmentStartedNanos),
                            elapsedMillis(requestStartedNanos)
                    );
                } else {
                    long segmentStartedNanos = System.nanoTime();
                    byte[] segmentAudio = speechRuntimeClient.synthesize(reply, roleId);
                    cancellation.throwIfCancelled();
                    long frameStartedNanos = System.nanoTime();
                    segmentSink.audio(0, segmentAudio);
                    segmentCount = 1;
                    logger.info(
                            "Voice turn timing: turn_id={} stage=TTS_SEGMENT_FLUSHED sequence=0 "
                                    + "characters={} bytes={} synth_ms={} flush_ms={} request_ms={}",
                            turnId,
                            reply.codePointCount(0, reply.length()),
                            segmentAudio.length,
                            elapsedMillis(segmentStartedNanos),
                            elapsedMillis(frameStartedNanos),
                            elapsedMillis(requestStartedNanos)
                    );
                    cancellation.throwIfCancelled();
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
                logger.info(
                        "Voice turn timing: turn_id={} stage=TTS_COMPLETED segments={} stage_ms={} request_ms={}",
                        turnId,
                        segmentSink == null ? 1 : segmentCount,
                        elapsedMillis(ttsStartedNanos),
                        elapsedMillis(requestStartedNanos)
                );
                return new VoiceTurnResult(transcript, reply, audio);
            } catch (VoiceTurnCancelledException | VoiceTurnClientDisconnectedException exception) {
                if (start != null && !generationCompleted) {
                    conversationService.interruptGeneration(
                            start.assistantMessageId(), partialReply(reply, streamedReply)
                    );
                }
                recordStage(deviceId, turnId, VoiceTurnStage.CANCELLED, null);
                throw exception;
            } catch (RuntimeException exception) {
                if (start != null && !generationCompleted) {
                    conversationService.failGeneration(
                            start.assistantMessageId(),
                            exception instanceof LlmProviderUnavailableException ? "provider_unavailable" : "voice_turn_failed",
                            partialReply(reply, streamedReply)
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

    private String boundSingleSpokenReply(String reply) {
        String normalized = reply == null ? "" : reply.trim();
        if (normalized.codePointCount(0, normalized.length()) <= MAX_SPOKEN_REPLY_CODE_POINTS) {
            return normalized;
        }
        int limit = normalized.offsetByCodePoints(0, MAX_SPOKEN_REPLY_CODE_POINTS);
        String bounded = normalized.substring(0, limit).stripTrailing();
        int boundary = lastStrongBoundary(bounded);
        if (boundary >= 0 && bounded.codePointCount(0, boundary + 1)
                >= MAX_SPOKEN_REPLY_CODE_POINTS / 2) {
            return bounded.substring(0, boundary + 1).stripTrailing();
        }
        return bounded + "。";
    }

    private int lastStrongBoundary(String value) {
        for (int index = value.length() - 1; index >= 0; index--) {
            char current = value.charAt(index);
            if (current == '。' || current == '！' || current == '？'
                    || current == '!' || current == '?' || current == '\n') {
                return index;
            }
        }
        return -1;
    }

    private String partialReply(String completedReply, StringBuilder streamedReply) {
        return completedReply.isEmpty() ? streamedReply.toString() : completedReply;
    }

    private long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
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
