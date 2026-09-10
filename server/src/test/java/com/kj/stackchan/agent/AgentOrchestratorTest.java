package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    @Test
    void streamsShortVoiceConversationWithoutBuildingTheToolAgent() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(clientFactory.createLowLatencyChatClient()).thenReturn(chatClient);
        when(chatClient.prompt()
                .system(any(String.class))
                .messages(any(List.class))
                .user(any(String.class))
                .stream()
                .content()).thenReturn(Flux.just("这把确实可惜。", "下一把打回来。"));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory,
                new ObjectMapper(), new AppProperties()
        );
        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "又输了"
        )).collectList().block();

        assertThat(output).containsExactly("这把确实可惜。", "下一把打回来。");
        verify(assemblyService, never()).assemble(any());
        verify(clientFactory, never()).createAgentChatModel();
    }

    @Test
    void answersAuthorizedVoiceTimeDeterministicallyWithoutCallingTheModel() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        ToolCallback callback = ToolCallbacks.from(new FixedCurrentTimeTool())[0];
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-09-08T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory,
                new ObjectMapper(), new AppProperties()
        );
        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "现在几点"
        )).collectList().block();

        assertThat(output).containsExactly("现在是9月8日星期二，晚上10点05分。");
        verify(clientFactory, never()).createAgentChatModel();
        verify(clientFactory, never()).createLowLatencyChatClient();
        verify(auditService).record(
                eq(context), eq(null), eq(CurrentTimeTool.ID), eq(AgentToolSource.BUILTIN), eq(null),
                eq(AgentToolOutcome.SUCCESS), anyLong(), anyInt(), eq(false)
        );
    }

    @Test
    void keepsReminderQuestionsOnTheRequiredToolPath() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-09-08T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory,
                new ObjectMapper(), new AppProperties()
        );
        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "下一条提醒是什么"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前角色的下一条提醒，所以不能猜测。");
        verify(clientFactory, never()).createLowLatencyChatClient();
    }

    @Test
    void executesAReadOnlyToolThroughReactAgentAndReturnsTheFinalAnswer() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AtomicInteger executions = new AtomicInteger();
        ToolCallback callback = ToolCallbacks.from(new CurrentTimeTestTool(executions))[0];
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                AgentChannel.WEB
        );
        AgentToolAssemblyService.AgentToolAssembly assembly = new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback),
                Map.of(),
                skillRegistry,
                List.of(),
                Map.of(CurrentTimeTool.ID, new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.BUILTIN,
                        null,
                        null
                )),
                List.of()
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-07-28T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(assembly);
        when(clientFactory.createAgentChatModel()).thenReturn(chatModel);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getOptions()).isInstanceOf(OpenAiChatOptions.class);
            OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
            assertThat(options.getToolCallbacks())
                    .extracting(tool -> tool.getToolDefinition().name())
                    .containsExactly(CurrentTimeTool.ID);
            assertThat(options.getToolChoice()).isNull();
            return response(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-1", "function", CurrentTimeTool.ID, "{}"
                        )))
                        .build());
        }).thenReturn(response(new AssistantMessage("现在是十点。")));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService,
                assemblyService,
                auditService,
                clientFactory,
                new ObjectMapper(),
                new AppProperties()
        );
        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context,
                "你是测试助手。",
                List.of(),
                "现在几点"
        )).collectList().block();

        assertThat(output).containsExactly("现在是十点。");
        assertThat(executions).hasValue(1);
        verify(auditService).record(
                eq(context),
                eq(null),
                eq(CurrentTimeTool.ID),
                eq(AgentToolSource.BUILTIN),
                eq(null),
                eq(AgentToolOutcome.SUCCESS),
                anyLong(),
                eq(16),
                eq(false)
        );
    }

    @Test
    void refusesToGuessWhenRequiredCurrentTimeToolIsNotAuthorized() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                AgentChannel.WEB
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-07-28T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService,
                assemblyService,
                auditService,
                clientFactory,
                new ObjectMapper(),
                new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context,
                "你是测试助手。",
                List.of(),
                "现在几点了呀"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前日期和时间，所以不能猜测。");
    }

    @Test
    void refusesAHallucinatedTimeWhenTheModelSkipsTheRequiredTool() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AtomicInteger executions = new AtomicInteger();
        ToolCallback callback = ToolCallbacks.from(new CurrentTimeTestTool(executions))[0];
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                AgentChannel.WEB
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-07-28T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback),
                Map.of(),
                skillRegistry,
                List.of(),
                Map.of(CurrentTimeTool.ID, new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.BUILTIN, null, null
                )),
                List.of()
        ));
        when(clientFactory.createAgentChatModel()).thenReturn(chatModel);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(new AssistantMessage("现在是下午两点四十五分。"))
        );

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService,
                assemblyService,
                auditService,
                clientFactory,
                new ObjectMapper(),
                new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context,
                "你是测试助手。",
                List.of(),
                "现在几点"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前日期和时间，所以不能猜测。");
        assertThat(executions).hasValue(0);
    }

    @Test
    void refusesToGuessCalendarWhenTheDeviceCalendarToolIsUnavailable() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-25T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService,
                assemblyService,
                auditService,
                clientFactory,
                new ObjectMapper(),
                new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context,
                "你是测试助手。",
                List.of(),
                "我最近几天有什么日程"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前设备的日历缓存，所以不能猜测。");
    }

    @Test
    void requiresTheDeviceCalendarToolBeforeAnsweringCalendarQuestions() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AtomicInteger executions = new AtomicInteger();
        ToolCallback callback = ToolCallbacks.from(new CalendarTestTool(executions))[0];
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-25T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback),
                Map.of(),
                skillRegistry,
                List.of(),
                Map.of(UpcomingCalendarEventsTool.ID, new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.BUILTIN, null, null
                )),
                List.of()
        ));
        when(clientFactory.createAgentChatModel()).thenReturn(chatModel);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-calendar", "function", UpcomingCalendarEventsTool.ID, "{}"
                        )))
                        .build()),
                response(new AssistantMessage("明天有一条日程。"))
        );

        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService,
                assemblyService,
                auditService,
                clientFactory,
                new ObjectMapper(),
                new AppProperties()
        );
        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context,
                "你是测试助手。",
                List.of(),
                "明天有什么日程"
        )).collectList().block();

        assertThat(output).containsExactly("明天有一条日程。");
        assertThat(executions).hasValue(1);
    }

    @Test
    void refusesToGuessWeatherWhenTheDeviceWeatherToolIsUnavailable() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-25T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory,
                new ObjectMapper(), new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "今天会下雨吗"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前设备的天气缓存，所以不能猜测。");
    }

    @Test
    void refusesToGuessPersonalTasksWhenTheTaskToolIsUnavailable() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-30T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory, new ObjectMapper(), new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "我还有哪些待办"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前角色的待办，所以不能猜测。");
    }

    @Test
    void refusesToGuessDailyTaskProgressWhenTheTaskToolIsUnavailable() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-31T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), skillRegistry, List.of(), Map.of(), List.of()
        ));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory, new ObjectMapper(), new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "我今天完成了什么"
        )).collectList().block();

        assertThat(output).containsExactly("我暂时无法可靠读取当前角色的待办，所以不能猜测。");
    }

    @Test
    void requiresThePersonalTaskToolBeforeAnsweringDailyProgressQuestions() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AtomicInteger executions = new AtomicInteger();
        ToolCallback callback = ToolCallbacks.from(new PersonalTaskTestTool(executions))[0];
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-31T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback), Map.of(), skillRegistry, List.of(),
                Map.of(PersonalTasksTool.ID, new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.BUILTIN, null, null
                )), List.of()
        ));
        when(clientFactory.createAgentChatModel()).thenReturn(chatModel);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-task-progress", "function", PersonalTasksTool.ID, "{}"
                        )))
                        .build()),
                response(new AssistantMessage("今天完成了一项，还剩一项。"))
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory,
                new ObjectMapper(), new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "今天的任务进度怎么样"
        )).collectList().block();

        assertThat(output).containsExactly("今天完成了一项，还剩一项。");
        assertThat(executions).hasValue(1);
    }

    @Test
    void requiresTheDeviceWeatherToolBeforeAnsweringWeatherQuestions() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        AtomicInteger executions = new AtomicInteger();
        ToolCallback callback = ToolCallbacks.from(new WeatherTestTool(executions))[0];
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-08-25T00:00:00Z")
        ));
        when(assemblyService.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback), Map.of(), skillRegistry, List.of(),
                Map.of(CurrentDeviceWeatherTool.ID, new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.BUILTIN, null, null
                )), List.of()
        ));
        when(clientFactory.createAgentChatModel()).thenReturn(chatModel);
        when(chatModel.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-weather", "function", CurrentDeviceWeatherTool.ID, "{}"
                        )))
                        .build()),
                response(new AssistantMessage("今天有雨，记得带伞。"))
        );
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                settingsService, assemblyService, auditService, clientFactory,
                new ObjectMapper(), new AppProperties()
        );

        List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                context, "你是测试助手。", List.of(), "今天需要带伞吗"
        )).collectList().block();

        assertThat(output).containsExactly("今天有雨，记得带伞。");
        assertThat(executions).hasValue(1);
    }

    @Test
    void readsAnEnabledManagedSkillBeforeAnswering() {
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        AgentToolAssemblyService assemblyService = mock(AgentToolAssemblyService.class);
        AgentToolAuditService auditService = mock(AgentToolAuditService.class);
        LlmRuntimeClientFactory clientFactory = mock(LlmRuntimeClientFactory.class);
        ChatModel chatModel = mock(ChatModel.class);
        AgentInvocationContext context = new AgentInvocationContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                AgentChannel.WEB
        );
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.parse("2026-07-28T00:00:00Z")
        ));
        when(clientFactory.createAgentChatModel()).thenReturn(chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                response(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-skill", "function", "read_skill", "{\"skill_name\":\"test-guidance\"}"
                        )))
                        .build()),
                response(new AssistantMessage("已读取测试指导。"))
        );

        FileSystemSkillRegistry skillRegistry = FileSystemSkillRegistry.builder()
                .userSkillsDirectory("src/test/resources/no-user-skills")
                .projectSkillsDirectory("src/test/resources/skills")
                .build();
            AgentToolAssemblyService.AgentToolAssembly assembly = new AgentToolAssemblyService.AgentToolAssembly(
                    List.of(),
                    Map.of(),
                    new FilteringSkillRegistry(skillRegistry, java.util.Set.of("test-guidance")),
                    List.of(new AgentToolAssemblyService.SkillSnapshot(
                            "test-guidance", "Test managed skill", true
                    )),
                    Map.of(),
                    List.of()
            );
            when(assemblyService.assemble(context)).thenReturn(assembly);

            AgentOrchestrator orchestrator = new AgentOrchestrator(
                    settingsService,
                    assemblyService,
                    auditService,
                    clientFactory,
                    new ObjectMapper(),
                    new AppProperties()
            );
            List<String> output = orchestrator.stream(new AgentOrchestrator.AgentRequest(
                    context,
                    "你是测试助手。",
                    List.of(),
                    "按测试技能回答"
            )).collectList().block();

            assertThat(output).containsExactly("已读取测试指导。");
    }

    private ChatResponse response(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    public static class CurrentTimeTestTool {

        private final AtomicInteger executions;

        public CurrentTimeTestTool(AtomicInteger executions) {
            this.executions = executions;
        }

        @Tool(name = CurrentTimeTool.ID, description = "Return a fixed test time")
        public String read() {
            executions.incrementAndGet();
            return "{\"time\":\"10:00\"}";
        }
    }

    public static class FixedCurrentTimeTool {

        @Tool(name = CurrentTimeTool.ID, description = "Return a complete fixed test time")
        public String read() {
            return "{\"date\":\"2026-09-08\",\"time\":\"22:05:00\","
                    + "\"dayOfWeek\":\"TUESDAY\",\"zoneId\":\"Asia/Shanghai\",\"utcOffset\":\"+08:00\"}";
        }
    }

    public static class CalendarTestTool {

        private final AtomicInteger executions;

        public CalendarTestTool(AtomicInteger executions) {
            this.executions = executions;
        }

        @Tool(name = UpcomingCalendarEventsTool.ID, description = "Return a fixed calendar event")
        public String read() {
            executions.incrementAndGet();
            return "{\"available\":true,\"total\":1}";
        }
    }

    public static class WeatherTestTool {

        private final AtomicInteger executions;

        public WeatherTestTool(AtomicInteger executions) {
            this.executions = executions;
        }

        @Tool(name = CurrentDeviceWeatherTool.ID, description = "Return fixed weather")
        public String read() {
            executions.incrementAndGet();
            return "{\"available\":true,\"summary\":\"今天有雨\"}";
        }
    }

    public static class PersonalTaskTestTool {

        private final AtomicInteger executions;

        public PersonalTaskTestTool(AtomicInteger executions) {
            this.executions = executions;
        }

        @Tool(name = PersonalTasksTool.ID, description = "Return fixed task progress")
        public String read() {
            executions.incrementAndGet();
            return "{\"count\":1,\"completedTodayCount\":1}";
        }
    }
}
