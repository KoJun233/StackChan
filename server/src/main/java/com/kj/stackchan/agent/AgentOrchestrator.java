package com.kj.stackchan.agent;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
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
import com.kj.stackchan.speech.VoiceDialogueControl;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
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
            天气和日程的日期追问必须重新读取对应 Tool，只回答返回数据覆盖的日期；没有覆盖时如实说明，
            不得把今天的结果挪用为明天或把缺少数据说成没有安排。
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
    private static final Pattern CALENDAR_QUESTION = Pattern.compile(
            "(?i)(日程|行程|日历|(?:今天|明天|最近|这几天|本周).{0,6}安排)"
                    + "|(?:calendar|schedule|agenda)"
    );
    private static final Pattern WEATHER_QUESTION = Pattern.compile(
            "(?:天气|气温|温度|下雨|降雨|带伞|冷不冷|热不热"
                    + "|(?:weather|temperature|rain|forecast))",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PERSONAL_TASK_QUESTION = Pattern.compile(
            "(?i)(待办|任务清单|要做(?:的)?事|todo|to-do"
                    + "|(?:今天|今日).{0,8}(?:完成|做完|任务进度)"
                    + "|(?:完成|做完).{0,8}(?:什么|哪些|多少)"
                    + "|任务.{0,4}进度)"
    );
    private static final Pattern NEXT_REMINDER_QUESTION = Pattern.compile(
            "(?i)(下一(?:个|条).{0,6}提醒|最近.{0,4}提醒|什么时候提醒|next reminder)"
    );
    private static final Pattern PENDING_MEMORY_QUESTION = Pattern.compile(
            "(?i)(待确认.{0,6}记忆|记忆.{0,6}待确认|pending memor)"
    );
    private static final Pattern EXPLICIT_AGENT_REQUEST = Pattern.compile(
            "(?i)(查一下|查询|搜索|检索|联网|使用.{0,8}(?:tool|工具|skill|技能|mcp)"
                    + "|(?:tool|skill|mcp)|最新.{0,12}(?:消息|新闻|进展|发布|更新)"
                    + "|最近.{0,12}(?:消息|新闻|进展|发布|更新))"
    );
    private static final String TOOL_LIMIT_REPLY = "本次查询已达到工具调用上限，我不能可靠地继续查询。";
    private static final Pattern TEMPORAL_FOLLOW_UP = Pattern.compile(
            "(?:那|那么)?(?:今天|明天|后天|今晚|明早|明晚|上午|下午|晚上|这周|本周|下周|周[一二三四五六日天])"
                    + "(?:呢|怎么样|如何)?[？?。！!]*"
    );
    private static final String AGENT_TIMEOUT_REPLY = "这次工具查询超时了，我暂时无法给出可靠结果。";
    private static final String REQUIRED_TIME_TOOL_REPLY = "我暂时无法可靠读取当前日期和时间，所以不能猜测。";
    private static final String REQUIRED_CAPABILITY_TOOL_REPLY = "我暂时无法可靠读取当前授权的 Tool 和 Skill，所以不能猜测。";
    private static final String REQUIRED_CALENDAR_TOOL_REPLY = "我暂时无法可靠读取当前设备的日历缓存，所以不能猜测。";
    private static final String REQUIRED_WEATHER_TOOL_REPLY = "我暂时无法可靠读取当前设备的天气缓存，所以不能猜测。";
    private static final String REQUIRED_PERSONAL_TASK_TOOL_REPLY = "我暂时无法可靠读取当前角色的待办，所以不能猜测。";
    private static final String REQUIRED_REMINDER_TOOL_REPLY = "我暂时无法可靠读取当前角色的下一条提醒，所以不能猜测。";
    private static final String REQUIRED_MEMORY_TOOL_REPLY = "我暂时无法可靠读取当前角色的待确认记忆，所以不能猜测。";

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
        String requiredToolName = requiredToolName(request);
        if (usesLowLatencyVoiceConversation(request, requiredToolName)) {
            return lowLatencyConversation(request);
        }
        if (usesDeterministicVoiceTime(request, requiredToolName)
                && settingsService.runtimeSettings().enabled()) {
            return deterministicVoiceTime(request);
        }
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

    private String requiredToolName(AgentRequest request) {
        var dialogue = VoiceDialogueControl.parse(request.userMessage());
        String explicit = requiredToolName(request.context().channel() == AgentChannel.VOICE
                ? dialogue.routingText() : request.userMessage());
        boolean continuation = dialogue.kind() == VoiceDialogueControl.Kind.CONTINUE;
        if (explicit != null || request.context().channel() != AgentChannel.VOICE
                || (!isTemporalFollowUp(dialogue.routingText()) && !continuation)) {
            return explicit;
        }
        // Voice history has already been scoped to this role and recent complete turns.
        // Walk only an uninterrupted chain of temporal follow-ups, never revive an older topic.
        List<Message> history = request.history();
        for (int index = history.size() - 1, turns = 0; index >= 1 && turns < 4; index -= 2, turns++) {
            if (!(history.get(index) instanceof AssistantMessage)
                    || !(history.get(index - 1) instanceof UserMessage previous)) {
                return null;
            }
            var previousDialogue = VoiceDialogueControl.parse(previous.getText());
            String previousTool = requiredToolName(previousDialogue.routingText());
            if (previousTool != null && continuation) return previousTool;
            if (CurrentDeviceWeatherTool.ID.equals(previousTool)
                    || UpcomingCalendarEventsTool.ID.equals(previousTool)) {
                return previousTool;
            }
            if (previousTool != null || (!isTemporalFollowUp(previousDialogue.routingText())
                    && previousDialogue.kind() != VoiceDialogueControl.Kind.CONTINUE)) {
                return null;
            }
        }
        return null;
    }

    private boolean isTemporalFollowUp(String message) {
        return TEMPORAL_FOLLOW_UP.matcher(message.strip()).matches();
    }

    private String requiredToolName(String userMessage) {
        if (CURRENT_DATE_TIME_QUESTION.matcher(userMessage).find()) {
            return CurrentTimeTool.ID;
        }
        if (CAPABILITY_QUESTION.matcher(userMessage).find()) {
            return CapabilityListTool.ID;
        }
        if (CALENDAR_QUESTION.matcher(userMessage).find()) {
            return UpcomingCalendarEventsTool.ID;
        }
        if (WEATHER_QUESTION.matcher(userMessage).find()) {
            return CurrentDeviceWeatherTool.ID;
        }
        if (PERSONAL_TASK_QUESTION.matcher(userMessage).find()) {
            return PersonalTasksTool.ID;
        }
        if (NEXT_REMINDER_QUESTION.matcher(userMessage).find()) {
            return NextReminderTool.ID;
        }
        if (PENDING_MEMORY_QUESTION.matcher(userMessage).find()) {
            return PendingMemoryCountTool.ID;
        }
        return null;
    }

    private String requiredToolReply(String toolName) {
        if (CurrentTimeTool.ID.equals(toolName)) {
            return REQUIRED_TIME_TOOL_REPLY;
        }
        if (UpcomingCalendarEventsTool.ID.equals(toolName)) {
            return REQUIRED_CALENDAR_TOOL_REPLY;
        }
        if (CurrentDeviceWeatherTool.ID.equals(toolName)) {
            return REQUIRED_WEATHER_TOOL_REPLY;
        }
        if (PersonalTasksTool.ID.equals(toolName)) {
            return REQUIRED_PERSONAL_TASK_TOOL_REPLY;
        }
        if (NextReminderTool.ID.equals(toolName)) {
            return REQUIRED_REMINDER_TOOL_REPLY;
        }
        if (PendingMemoryCountTool.ID.equals(toolName)) {
            return REQUIRED_MEMORY_TOOL_REPLY;
        }
        return REQUIRED_CAPABILITY_TOOL_REPLY;
    }

    private boolean usesLowLatencyVoiceConversation(AgentRequest request, String requiredToolName) {
        if (request.context().channel() != AgentChannel.VOICE || requiredToolName != null) {
            return false;
        }
        String message = request.userMessage();
        return message.codePointCount(0, message.length()) <= 80
                && !EXPLICIT_AGENT_REQUEST.matcher(message).find();
    }

    private Flux<String> lowLatencyConversation(AgentRequest request) {
        return Flux.defer(() -> llmRuntimeClientFactory.createLowLatencyChatClient()
                        .prompt()
                        .system(request.systemPrompt())
                        .messages(request.history())
                        .user(request.userMessage())
                        .stream()
                        .content())
                .timeout(appProperties.getAgent().getTimeout())
                .filter(text -> !text.isEmpty());
    }

    private boolean usesDeterministicVoiceTime(AgentRequest request, String requiredToolName) {
        return request.context().channel() == AgentChannel.VOICE
                && CurrentTimeTool.ID.equals(requiredToolName);
    }

    private Flux<String> deterministicVoiceTime(AgentRequest request) {
        return Flux.defer(() -> {
            AgentToolAssemblyService.AgentToolAssembly tools = toolAssemblyService.assemble(request.context());
            ToolCallback callback = tools.directTools().stream()
                    .filter(tool -> CurrentTimeTool.ID.equals(tool.getToolDefinition().name()))
                    .findFirst()
                    .orElse(null);
            if (callback == null) {
                return Flux.just(REQUIRED_TIME_TOOL_REPLY);
            }
            long started = System.nanoTime();
            try {
                String result = callback.call("{}");
                int resultBytes = result == null ? 0 : result.getBytes(StandardCharsets.UTF_8).length;
                if (result == null || result.isBlank()
                        || resultBytes > appProperties.getAgent().getMaxToolResultBytes()) {
                    throw new RequiredToolUnavailableException();
                }
                String reply = spokenCurrentTime(result);
                recordFastTimeTool(request.context(), AgentToolOutcome.SUCCESS, started, resultBytes);
                return Flux.just(reply);
            } catch (RuntimeException exception) {
                recordFastTimeTool(request.context(), AgentToolOutcome.TOOL_FAILED, started, 0);
                return Flux.just(REQUIRED_TIME_TOOL_REPLY);
            }
        });
    }

    private String spokenCurrentTime(String result) {
        try {
            var value = objectMapper.readTree(result);
            LocalDate date = LocalDate.parse(value.path("date").asText());
            LocalTime time = LocalTime.parse(value.path("time").asText());
            return "现在是%d月%d日%s，%s%d点%02d分。".formatted(
                    date.getMonthValue(), date.getDayOfMonth(), chineseWeekday(date.getDayOfWeek()),
                    dayPeriod(time.getHour()), displayHour(time.getHour()), time.getMinute()
            );
        } catch (RuntimeException | java.io.IOException exception) {
            throw new RequiredToolUnavailableException();
        }
    }

    private String chineseWeekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
    }

    private String dayPeriod(int hour) {
        if (hour < 6) return "凌晨";
        if (hour < 12) return "上午";
        if (hour < 18) return "下午";
        return "晚上";
    }

    private int displayHour(int hour) {
        int value = hour % 12;
        return value == 0 ? 12 : value;
    }

    private void recordFastTimeTool(
            AgentInvocationContext context,
            AgentToolOutcome outcome,
            long started,
            int resultBytes
    ) {
        try {
            auditService.record(
                    context, null, CurrentTimeTool.ID, AgentToolSource.BUILTIN, null, outcome,
                    Math.max(0, (System.nanoTime() - started) / 1_000_000), resultBytes, false
            );
        } catch (RuntimeException ignored) {
            // Auditing must not block a deterministic read-only result.
        }
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
                用户询问日程、行程、日历或近期安排时，必须调用 upcoming_device_calendar_events；
                不得用提醒 Tool、历史对话或常识代替日历缓存。缓存不可用时必须如实说明。
                用户询问天气、气温、降雨或是否需要带伞时，必须调用 current_device_weather；
                不得用历史对话、常识或模型知识代替天气缓存。缓存不可用时必须如实说明。
                用户询问待办、任务清单、要做的事、今天完成了什么或任务进度时，必须调用
                current_personal_tasks；
                不得用提醒、日历或历史对话代替待办数据。Tool 不可用时必须如实说明。
                用户询问下一条或最近的提醒时，必须调用 next_device_reminder；用户询问待确认记忆数量时，
                必须调用 pending_device_memory_count。两者均不得用历史对话猜测，Tool 不可用时必须如实说明。
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
