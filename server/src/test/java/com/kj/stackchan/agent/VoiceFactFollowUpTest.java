package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VoiceFactFollowUpTest {
    private final AgentSettingsService settings = mock(AgentSettingsService.class);
    private final AgentToolAssemblyService assembly = mock(AgentToolAssemblyService.class);
    private final LlmRuntimeClientFactory clients = mock(LlmRuntimeClientFactory.class);
    private final AgentInvocationContext context = new AgentInvocationContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AgentChannel.VOICE);
    private final AgentOrchestrator orchestrator = new AgentOrchestrator(settings, assembly,
            mock(AgentToolAuditService.class), clients, new ObjectMapper(), new AppProperties());

    @Test
    void rereadsWeatherForTomorrowThroughTheAuthorizedTool() {
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.EPOCH));
        var executions = new AtomicInteger();
        var callback = ToolCallbacks.from(new AgentOrchestratorTest.WeatherTestTool(executions))[0];
        when(assembly.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(callback), Map.of(), mock(SkillRegistry.class), List.of(),
                Map.of(CurrentDeviceWeatherTool.ID, new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.BUILTIN, null, null)), List.of()));
        ChatModel model = mock(ChatModel.class);
        when(clients.createAgentChatModel()).thenReturn(model);
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test-model").build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            assertThat(prompt.getInstructions()).anyMatch(message -> "那明天呢？".equals(message.getText()));
            return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("weather-1", "function",
                            CurrentDeviceWeatherTool.ID, "{}"))).build())));
        }).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("缓存里没有明天的数据。")))));
        assertThat(reply(history("今天天气怎么样"), "那明天呢？")).isEqualTo("缓存里没有明天的数据。");
        assertThat(executions).hasValue(1);
        verify(clients, never()).createLowLatencyChatClient();
    }

    @Test
    void refusesToGuessWhenInheritedCalendarToolIsUnauthorized() {
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true, true, true, Instant.EPOCH));
        when(assembly.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(
                List.of(), Map.of(), mock(SkillRegistry.class), List.of(), Map.of(), List.of()));
        assertThat(reply(history("今天有哪些日程", "那明天呢"), "后天呢"))
                .contains("无法可靠读取当前设备的日历缓存");
        verify(clients, never()).createLowLatencyChatClient();
        verify(clients, never()).createAgentChatModel();
    }

    @Test
    void keepsRequiredWeatherBoundaryWhenAgentIsDisabled() {
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                false, false, false, Instant.EPOCH));
        assertThat(reply(history("天气如何", "明天呢", "后天呢"), "那下周呢"))
                .contains("无法可靠读取当前设备的天气缓存");
        verifyNoInteractions(clients, assembly);
    }

    @Test
    void explicitCurrentQuestionTakesPriorityOverThePreviousTopic() {
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                false, false, false, Instant.EPOCH));
        assertThat(reply(history("今天天气怎么样"), "明天有什么日程"))
                .contains("无法可靠读取当前设备的日历缓存");
    }

    @Test
    void keepsGamingTopicChangesMissingContextAndAssistantMentionsOnTheFastPath() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(clients.createLowLatencyChatClient()).thenReturn(client);
        when(client.prompt().system(any(String.class)).messages(any(List.class)).user(any(String.class))
                .stream().content()).thenReturn(Flux.just("自然回应"));
        assertThat(reply(history("今天天气怎么样", "刚才这局赢了"), "那明天呢")).isEqualTo("自然回应");
        assertThat(reply(history("今天天气怎么样"), "明天比赛加油")).isEqualTo("自然回应");
        assertThat(reply(List.of(), "那明天呢")).isEqualTo("自然回应");
        assertThat(reply(List.of(new UserMessage("赢了"), new AssistantMessage("明天天气好")), "明天呢"))
                .isEqualTo("自然回应");
        assertThat(reply(List.of(new UserMessage("天气怎么样")), "明天呢")).isEqualTo("自然回应");
        assertThat(reply(history("天气怎么样", "今天有哪些待办"), "明天呢")).isEqualTo("自然回应");
        verifyNoInteractions(settings, assembly);
        verify(clients, never()).createAgentChatModel();
    }

    private String reply(List<Message> history, String message) {
        return orchestrator.stream(new AgentOrchestrator.AgentRequest(context, "测试助手", history, message))
                .collectList().block().stream().reduce("", String::concat);
    }

    private List<Message> history(String... messages) {
        List<Message> history = new ArrayList<>();
        for (String message : messages) {
            history.add(new UserMessage(message));
            history.add(new AssistantMessage("上一轮回答"));
        }
        return history;
    }
}
