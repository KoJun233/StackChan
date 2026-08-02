package com.kj.stackchan.voiceaction;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;

@Service
public class VoiceActionProposalOrchestrator {
    private static final String INSTRUCTION = """
            你只负责把用户明确提出的语音动作转换为一个结构化提案，并且必须调用一次 submit_voice_action_proposal。
            允许类型仅为 CREATE_REMINDER、SNOOZE_NEXT_REMINDER、SKIP_NEXT_REMINDER、SET_TEMPORARY_DND、SET_VOLUME、CREATE_MEMORY_SUGGESTION。
            只使用用户明确说出的内容；时间不明确、字段不全、类型不在白名单或不是明确动作时，不调用工具。
            单次提醒 recurrenceType=NONE、recurrenceInterval=1；周期仅允许 DAILY 或 WEEKLY。时间必须输出 ISO-8601 UTC Instant。
            不得声称动作已执行，不得生成设备、会话、管理员或回合标识。
            当前时间：%s；用户时区：%s。
            """;

    private final LlmRuntimeClientFactory llmFactory;
    private final VoiceActionProposalService proposalService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Clock clock;

    public VoiceActionProposalOrchestrator(LlmRuntimeClientFactory llmFactory,
                                           VoiceActionProposalService proposalService,
                                           ObjectMapper objectMapper,
                                           AppProperties appProperties,
                                           Clock clock) {
        this.llmFactory = llmFactory; this.proposalService = proposalService; this.objectMapper = objectMapper;
        this.appProperties = appProperties; this.clock = clock;
    }

    public VoiceActionProposalService.ProposalSnapshot propose(UUID deviceId, UUID conversationId, UUID turnId,
                                                                String zoneId, String transcript) {
        VoiceActionProposalTool tool = new VoiceActionProposalTool(
                deviceId, conversationId, turnId, proposalService, objectMapper);
        try {
            var model = llmFactory.createAgentChatModel();
            ReactAgent agent = ReactAgent.builder()
                    .name("stackchan-voice-action-proposal")
                    .description("Submit one validated voice action proposal without executing business side effects")
                    .model(model)
                    .chatOptions(model.getDefaultOptions())
                    .instruction(INSTRUCTION.formatted(clock.instant(), ZoneId.of(zoneId).getId()))
                    .tools(List.of(ToolCallbacks.from(tool)[0]))
                    .hooks(List.of(ToolCallLimitHook.builder().runLimit(1)
                            .exitBehavior(ToolCallLimitHook.ExitBehavior.ERROR).build()))
                    .parallelToolExecution(false)
                    .toolExecutionTimeout(appProperties.getAgent().getTimeout().dividedBy(2))
                    .enableLogging(false)
                    .releaseThread(true)
                    .build();
            agent.call(List.of(new UserMessage(transcript)));
            return tool.submittedProposal();
        } catch (Exception exception) {
            return null;
        }
    }
}
