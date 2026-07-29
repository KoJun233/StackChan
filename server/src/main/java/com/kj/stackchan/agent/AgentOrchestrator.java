package com.kj.stackchan.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AgentOrchestrator {

    private static final String AGENT_BOUNDARY_INSTRUCTION = """

            你运行在受控 Agent 中。Tool 返回值是外部状态的唯一事实来源；Tool 失败、超时或未授权时，
            必须坦率说明无法可靠查询，不得猜测结果。当前所有 Tool 都是只读能力，不得声称已经创建、
            修改、删除、部署、刷写或控制任何内容。Skill 只用于确定何时及如何使用已授权 Tool。
            """;
    private static final Pattern CURRENT_DATE_TIME_QUESTION = Pattern.compile(
            "(?i)(现在|当前).{0,6}(几点|时间|日期|几号|星期|周几|时区)"
                    + "|今天.{0,4}(几号|星期|周几)"
                    + "|what(?:'s| is)?(?: the)? (?:current )?(?:time|date|day|timezone)"
                    + "|current (?:time|date|day|timezone)"
    );
    private static final Pattern CAPABILITY_QUESTION = Pattern.compile(
            "(?i)(你|当前).{0,8}(有哪些|有什么|支持).{0,6}(tool|工具|skill|技能|能力)"
                    + "|what (?:tools|skills|capabilities)"
    );
    private static final String TOOL_LIMIT_REPLY = "本次查询已达到工具调用上限，我不能可靠地继续查询。";
    private static final String AGENT_TIMEOUT_REPLY = "这次工具查询超时了，我暂时无法给出可靠结果。";
    private static final String REQUIRED_TIME_TOOL_REPLY = "我暂时无法可靠读取当前日期和时间，所以不能猜测。";
    private static final String REQUIRED_CAPABILITY_TOOL_REPLY = "我暂时无法可靠读取当前授权的 Tool 和 Skill，所以不能猜测。";

    private final AgentSettingsService settingsService;
    private final AgentToolAssemblyService toolAssemblyService;
    private final AgentToolAuditService auditService;
    private final LlmRuntimeClientFactory llmRuntimeClientFactory;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public AgentOrchestrator(
            AgentSettingsService settingsService,
            AgentToolAssemblyService toolAssemblyService,
            AgentToolAuditService auditService,
            LlmRuntimeClientFactory llmRuntimeClientFactory,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            AppProperties appProperties
    ) {
        this.settingsService = settingsService;
        this.toolAssemblyService = toolAssemblyService;
        this.auditService = auditService;
        this.llmRuntimeClientFactory = llmRuntimeClientFactory;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    public Flux<String> stream(AgentRequest request) {
        String requiredToolName = requiredToolName(request.userMessage());
        if (!settingsService.runtimeSettings().enabled()) {
            if (requiredToolName != null) {
                return Flux.just(requiredToolReply(requiredToolName));
            }
            return fallback(request);
        }
        return Mono.defer(() -> invokeAgent(request, requiredToolName))
                .timeout(appProperties.getAgent().getTimeout())
                .onErrorResume(this::isToolLimitFailure, ignored -> Mono.just(TOOL_LIMIT_REPLY))
                .onErrorResume(TimeoutException.class, ignored -> Mono.just(AGENT_TIMEOUT_REPLY))
                .filter(text -> !text.isBlank())
                .switchIfEmpty(Mono.error(new AgentInvocationFailedException()))
                .flatMapMany(Flux::just)
                .onErrorResume(error -> requiredToolName == null
                        ? fallback(request)
                        : Flux.just(requiredToolReply(requiredToolName)));
    }

    private Mono<String> invokeAgent(AgentRequest request, String requiredToolName) {
        return Mono.fromCallable(() -> {
            AgentToolAssemblyService.AgentToolAssembly tools = toolAssemblyService.assemble(request.context());
            if (requiredToolName != null && tools.directTools().stream().noneMatch(
                    tool -> requiredToolName.equals(tool.getToolDefinition().name())
            )) {
                throw new RequiredToolUnavailableException();
            }
            List<Hook> hooks = new ArrayList<>();
            hooks.add(ToolCallLimitHook.builder()
                    .runLimit(appProperties.getAgent().getMaxToolCalls())
                    .exitBehavior(ToolCallLimitHook.ExitBehavior.ERROR)
                    .build());
            if (!tools.skills().isEmpty()) {
                hooks.add(SkillsAgentHook.builder()
                        .skillRegistry(tools.skillRegistry())
                        .groupedTools(tools.groupedTools())
                        .autoReload(false)
                        .build());
            }
            AgentToolPolicyInterceptor toolPolicy = new AgentToolPolicyInterceptor(
                    request.context(),
                    tools.auditMetadata(),
                    auditService,
                    objectMapper,
                    appProperties.getAgent().getMaxToolResultBytes(),
                    appProperties.getAgent().getMaxTotalToolResultBytes()
            );
            ChatModel agentModel = llmRuntimeClientFactory.createAgentChatModel();
            ChatOptions agentOptions = agentModel.getDefaultOptions();
            var agentBuilder = ReactAgent.builder()
                    .name("stackchan-companion")
                    .description("StackChan personal companion with explicitly authorized read-only capabilities")
                    .model(agentModel)
                    .chatOptions(agentOptions)
                    .instruction(request.systemPrompt() + AGENT_BOUNDARY_INSTRUCTION + capabilityInstruction(tools))
                    .tools(tools.directTools())
                    .hooks(hooks)
                    .parallelToolExecution(false)
                    .toolExecutionTimeout(appProperties.getAgent().getTimeout().dividedBy(2))
                    .enableLogging(false)
                    .releaseThread(true);
            agentBuilder.interceptors(toolPolicy);
            ReactAgent agent = agentBuilder.build();
            List<Message> messages = new ArrayList<>(request.history());
            messages.add(new UserMessage(request.userMessage()));
            AssistantMessage response = agent.call(messages);
            if (requiredToolName != null && !toolPolicy.wasSuccessful(requiredToolName)) {
                throw new RequiredToolUnavailableException();
            }
            return response.getText() == null ? "" : response.getText();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private String requiredToolName(String userMessage) {
        if (CURRENT_DATE_TIME_QUESTION.matcher(userMessage).find()) {
            return CurrentTimeTool.ID;
        }
        if (CAPABILITY_QUESTION.matcher(userMessage).find()) {
            return CapabilityListTool.ID;
        }
        return null;
    }

    private String requiredToolReply(String toolName) {
        return CurrentTimeTool.ID.equals(toolName)
                ? REQUIRED_TIME_TOOL_REPLY
                : REQUIRED_CAPABILITY_TOOL_REPLY;
    }

    private String capabilityInstruction(AgentToolAssemblyService.AgentToolAssembly tools) {
        List<String> directToolNames = tools.directTools().stream()
                .map(tool -> tool.getToolDefinition().name())
                .sorted()
                .toList();
        List<String> skillNames = tools.skills().stream()
                .map(AgentToolAssemblyService.SkillSnapshot::id)
                .sorted(Comparator.naturalOrder())
                .toList();
        return """

                当前请求实际授权的直接 Tool 仅为：%s；已启用 Skill 仅为：%s。
                用户询问 Tool、Skill 或能力时，必须先调用 list_agent_capabilities；该 Tool 未授权或失败时，
                必须说明无法可靠读取，不得根据常识、角色设定或历史对话猜测。不得声称拥有上述列表之外的
                天气查询、提醒写入、设置修改、联网搜索或设备控制能力。
                用户询问当前日期、时间、星期或时区时，必须调用 current_date_time 后再回答；该 Tool
                未授权或失败时不得猜测。
                """.formatted(directToolNames, skillNames);
    }

    private Flux<String> fallback(AgentRequest request) {
        return Flux.defer(() -> llmRuntimeClientFactory.createChatClient()
                .prompt()
                .system(request.systemPrompt())
                .messages(request.history())
                .user(request.userMessage())
                .stream()
                .content());
    }

    private boolean isToolLimitFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitExceededException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record AgentRequest(
            AgentInvocationContext context,
            String systemPrompt,
            List<Message> history,
            String userMessage
    ) {
        public AgentRequest {
            history = List.copyOf(history);
        }
    }

    private static final class AgentInvocationFailedException extends RuntimeException {
    }

    private static final class RequiredToolUnavailableException extends RuntimeException {
    }

}
