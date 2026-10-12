package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.delegation.BarkNotificationService;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BarkNotificationToolTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final BarkNotificationService service=mock(BarkNotificationService.class);
    private final AgentSettingsService settings=mock(AgentSettingsService.class);
    private final AgentToolAssemblyService assembly=mock(AgentToolAssemblyService.class);
    private final AgentToolAuditService audit=mock(AgentToolAuditService.class);
    private final LlmRuntimeClientFactory factory=mock(LlmRuntimeClientFactory.class);
    private final ChatModel model=mock(ChatModel.class);
    private AgentInvocationContext context(AgentChannel channel) {
        return new AgentInvocationContext(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),channel);
    }
    private AgentOrchestrator orchestrator(AgentInvocationContext context) {
        var tool=ToolCallbacks.from(new BarkNotificationTool(context,service,mapper))[0];
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(true,true,true,Instant.now()));
        when(assembly.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(List.of(tool),Map.of(),
                mock(SkillRegistry.class),List.of(),Map.of(BarkNotificationTool.ID,
                new AgentToolPolicyInterceptor.ToolAuditMetadata(AgentToolSource.BUILTIN,null,null)),List.of()));
        when(factory.createAgentChatModel()).thenReturn(model);
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("test").build());
        return new AgentOrchestrator(settings,assembly,audit,factory,mapper,new AppProperties());
    }
    private ChatResponse response(AssistantMessage message) { return new ChatResponse(List.of(new Generation(message))); }
    private ChatResponse pushCall() { return response(AssistantMessage.builder().content("").toolCalls(List.of(
            new AssistantMessage.ToolCall("push-1","function",BarkNotificationTool.ID,"{\"title\":\"任意通知\",\"body\":\"我的内容\"}"))).build()); }

    @Test void schemaAcceptsOnlyMessageContentAndFixesRecipientAndScopeOnTheServer() {
        var context=context(AgentChannel.WEB); var tool=ToolCallbacks.from(new BarkNotificationTool(context,service,mapper))[0];
        assertThat(tool.getToolDefinition().inputSchema()).contains("title","body").doesNotContain("deviceKey","serverUrl","deviceId","roleId","turnId");
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),"SENT",null,false));
        assertThat(tool.call("{\"title\":\"任意通知\",\"body\":\"我的内容\"}")).contains("SENT").doesNotContain("device_key","ciphertext");
        verify(service).send(context,"任意通知","我的内容");
    }
    @Test void explicitLiteralMessageContentDoesNotRequireAnUnrelatedWeatherLookup() {
        var context=context(AgentChannel.WEB); var agent=orchestrator(context);
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),"SENT",null,false));
        when(model.call(any(Prompt.class))).thenReturn(pushCall(),response(new AssistantMessage("Bark 已接受通知。")));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),"给我手机推送一条通知：今天记得带伞"))
                .collectList().block()).containsExactly("Bark 已接受通知。");
        verify(service).send(context,"任意通知","我的内容");
    }
    @ParameterizedTest @EnumSource(value=AgentChannel.class,names={"WEB","VOICE"})
    void explicitPhoneRequestExecutesActualToolThroughAgentInsteadOfChatFallback(AgentChannel channel) {
        var context=context(channel); var agent=orchestrator(context);
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),"SENT",null,false));
        when(model.call(any(Prompt.class))).thenReturn(pushCall(),response(new AssistantMessage("Bark 已接受通知。")));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),"把刚才的结果发到我的手机"))
                .collectList().block()).containsExactly("Bark 已接受通知。");
        verify(service).send(context,"任意通知","我的内容"); verify(factory,never()).createChatClient(); verify(factory,never()).createLowLatencyChatClient();
    }
    @ParameterizedTest @ValueSource(strings={"FAILED","UNKNOWN"})
    void failedOrAmbiguousDeliveryIsNeverSuccessfulEvidenceOrAChatSuccess(String status) {
        var context=context(AgentChannel.WEB); var agent=orchestrator(context);
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),status,"PUSH_DELIVERY_"+status,false));
        when(model.call(any(Prompt.class))).thenReturn(pushCall(),response(new AssistantMessage("虚构发送成功")));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),"请把这个推送到手机"))
                .collectList().block()).allMatch(text->text.contains("没有获得") && !text.contains("虚构"));
        verify(factory,never()).createChatClient();
        var policy=new AgentToolPolicyInterceptor(context,Map.of(BarkNotificationTool.ID,
                new AgentToolPolicyInterceptor.ToolAuditMetadata(AgentToolSource.BUILTIN,null,null)),audit,mapper,8192,24576);
        var request=ToolCallRequest.builder().toolCallId("push").toolName(BarkNotificationTool.ID).arguments("{}").context(Map.of()).build();
        var result=policy.interceptToolCall(request,ignored->ToolCallResponse.of("push",BarkNotificationTool.ID,
                "{\"status\":\""+status+"\",\"errorCode\":\"PUSH_DELIVERY_"+status+"\"}"));
        assertThat(result.isError()).isTrue(); assertThat(result.getResult()).contains(status);
        assertThat(policy.wasSuccessful(BarkNotificationTool.ID)).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"你好","不要把这个发到我的手机","比如每天推送到手机",
            "别往我的手机发消息","不用给我手机发通知了"})
    void ordinaryConversationNegationAndExamplesCannotExposePhoneWriteTool(String message) {
        var context=context(AgentChannel.WEB); var agent=orchestrator(context);
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var options=(OpenAiChatOptions)((Prompt)call.getArgument(0)).getOptions();
            assertThat(options.getToolCallbacks()).noneMatch(tool->BarkNotificationTool.ID.equals(tool.getToolDefinition().name()));
            return response(new AssistantMessage("普通回复"));
        });
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),message)).collectList().block()).containsExactly("普通回复");
        verifyNoInteractions(service);
    }
    @Test void legacyTasksWithEmptyPermissionSnapshotCannotInheritPhoneWriteTool() {
        var context=new AgentInvocationContext(UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),AgentChannel.DELEGATION,UUID.randomUUID(),1);
        var agent=orchestrator(context);
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var options=(OpenAiChatOptions)((Prompt)call.getArgument(0)).getOptions();
            assertThat(options.getToolCallbacks()).noneMatch(tool->BarkNotificationTool.ID.equals(tool.getToolDefinition().name()));
            return response(new AssistantMessage("{}"));
        });
        agent.executeTask(new AgentOrchestrator.AgentRequest(context,"任务",List.of(),"测试旧任务"),List.of(),operation->operation.get());
        verifyNoInteractions(service);
    }
    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> phoneRequests() {
        return java.util.stream.Stream.of(AgentChannel.WEB, AgentChannel.VOICE).flatMap(channel ->
                java.util.stream.Stream.of("嗯，给我的手机发一条消息：今天记得带伞。", "往我手机上发个通知：早点休息",
                        "给我的 iPhone 发条消息：下午见", "请通过 Bark 送一条通知：你好",
                        "你能帮我给手机发一条消息吗？内容：今天记得带伞",
                        "给我手机发一条通知：不要忘了发邮件", "给手机发条消息：你有没有那个功能",
                        "不是给电脑，是给我的手机发一条消息：你好")
                        .map(message -> org.junit.jupiter.params.provider.Arguments.of(channel, message)));
    }

    @ParameterizedTest @MethodSource("phoneRequests")
    void naturalPhoneWordOrdersAndLiteralBodiesReachTheActualTool(AgentChannel channel, String message) {
        var context=context(channel); var agent=orchestrator(context);
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),"SENT",null,false));
        when(model.call(any(Prompt.class))).thenReturn(pushCall(),response(new AssistantMessage("Bark 已接受通知。")));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),message)).collectList().block())
                .containsExactly("Bark 已接受通知。");
        verify(service).send(context,"任意通知","我的内容");
        verify(factory,never()).createChatClient(); verify(factory,never()).createLowLatencyChatClient();
    }

    @ParameterizedTest @EnumSource(value=AgentChannel.class,names={"WEB","VOICE"})
    void vagueRequestAsksForContentAndItsImmediateAnswerCanSendWithoutWeatherLookup(AgentChannel channel) {
        var context=context(channel); var agent=orchestrator(context);
        when(model.call(any(Prompt.class))).thenReturn(response(new AssistantMessage("我没有发短信或通知的权限，只能陪你聊天。")));
        var question=agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),"嗯，给我的手机发一条消息。"))
                .collectList().block();
        assertThat(question).containsExactly("可以通过 Bark 给你的手机发通知。你想发送什么内容？");
        verifyNoInteractions(service);
        List<Message> history=List.of(new UserMessage("嗯，给我的手机发一条消息。"),new AssistantMessage(question.get(0)));
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),"SENT",null,false));
        when(model.call(any(Prompt.class))).thenReturn(pushCall(),response(new AssistantMessage("Bark 已接受通知。")));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",history,"今天记得带伞"))
                .collectList().block()).containsExactly("Bark 已接受通知。");
        verify(service).send(context,"任意通知","我的内容");
        verify(factory,never()).createChatClient(); verify(factory,never()).createLowLatencyChatClient();
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> phoneCapabilityQuestions() {
        return java.util.stream.Stream.of(AgentChannel.WEB, AgentChannel.VOICE).flatMap(channel ->
                java.util.stream.Stream.of("你没有那个 Send for notation 那个功能吗。", "你有 send_phone_notification 这个工具吗？",
                        "你能给手机发通知吗？", "你能不能通过 Bark 发通知？", "怎么推送到手机", "你有没有发通知的权限？")
                        .map(message -> org.junit.jupiter.params.provider.Arguments.of(channel, message)));
    }

    @ParameterizedTest @MethodSource("phoneCapabilityQuestions")
    void capabilityQuestionsReadCurrentCatalogDespiteEarlierDenialAndCannotSend(AgentChannel channel, String message) {
        var context=context(channel); var agent=orchestrator(context);
        var phone=ToolCallbacks.from(new BarkNotificationTool(context,service,mapper))[0];
        var catalog=ToolCallbacks.from(new CapabilityListTool(List.of(BarkNotificationTool.ID,CapabilityListTool.ID),List.of(),mapper))[0];
        var builtin=new AgentToolPolicyInterceptor.ToolAuditMetadata(AgentToolSource.BUILTIN,null,null);
        when(assembly.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(List.of(phone,catalog),Map.of(),
                mock(SkillRegistry.class),List.of(),Map.of(BarkNotificationTool.ID,builtin,CapabilityListTool.ID,builtin),List.of()));
        var catalogCall=response(AssistantMessage.builder().content("").toolCalls(List.of(new AssistantMessage.ToolCall(
                "catalog-1","function",CapabilityListTool.ID,"{}"))).build());
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var prompt=(Prompt)call.getArgument(0);
            var options=(OpenAiChatOptions)prompt.getOptions();
            assertThat(options.getToolCallbacks()).noneMatch(tool->BarkNotificationTool.ID.equals(tool.getToolDefinition().name()));
            assertThat(prompt.getSystemMessage().getText()).contains("能力目录的实际结果优先于历史回答");
            return catalogCall;
        }).thenReturn(response(new AssistantMessage("可以通过 Bark 发手机通知。")));
        List<Message> history=List.of(new UserMessage("给我的手机发一条消息"),new AssistantMessage("没有通知权限，只能陪你聊天。"));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",history,message)).collectList().block())
                .containsExactly("可以通过 Bark 发手机通知。");
        verify(audit).record(eq(context),isNull(),eq(CapabilityListTool.ID),eq(AgentToolSource.BUILTIN),isNull(),
                eq(AgentToolOutcome.SUCCESS),anyLong(),anyInt(),eq(false));
        verifyNoInteractions(service); verify(factory,never()).createChatClient(); verify(factory,never()).createLowLatencyChatClient();
    }

    @ParameterizedTest @ValueSource(strings={"取消","不用了","算了","别发了","不要发送了","不用给我手机发消息",
            "don't send it","比如给手机发消息","怎么发通知？","换个话题","继续"})
    void cancellationQuestionsAndTopicControlsDoNotContinuePendingPhoneAuthorization(String message) {
        var context=context(AgentChannel.WEB); var agent=orchestrator(context);
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var options=(OpenAiChatOptions)((Prompt)call.getArgument(0)).getOptions();
            assertThat(options.getToolCallbacks()).noneMatch(tool->BarkNotificationTool.ID.equals(tool.getToolDefinition().name()));
            return response(new AssistantMessage("普通回复"));
        });
        List<Message> history=List.of(new UserMessage("给我的手机发一条消息"),
                new AssistantMessage("可以通过 Bark 给你的手机发通知。你想发送什么内容？"));
        agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",history,message)).collectList().block();
        verifyNoInteractions(service);
    }

    @Test void aCompletedPhoneRequestCannotAuthorizeAnUnrelatedLaterMessage() {
        var context=context(AgentChannel.WEB); var agent=orchestrator(context);
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var options=(OpenAiChatOptions)((Prompt)call.getArgument(0)).getOptions();
            assertThat(options.getToolCallbacks()).noneMatch(tool->BarkNotificationTool.ID.equals(tool.getToolDefinition().name()));
            return response(new AssistantMessage("普通回复"));
        });
        List<Message> history=List.of(new UserMessage("给我的手机发一条消息：你好"),new AssistantMessage("Bark 已接受通知。"));
        agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",history,"明天记得出门")).collectList().block();
        verifyNoInteractions(service);
    }

    @Test void disabledRuntimeCannotFallBackToADenialOrClaimToSend() {
        var context=context(AgentChannel.VOICE); var agent=orchestrator(context);
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(false,true,false,Instant.now()));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),"给我的手机发一条消息"))
                .collectList().block()).allMatch(text->text.contains("没有获得 Bark 接受的确认"));
        verifyNoInteractions(service); verify(factory,never()).createChatClient(); verify(factory,never()).createLowLatencyChatClient();
        verify(factory,never()).createAgentChatModel();
    }

    @Test void anUnconfiguredChannelIsAFailedAttemptAndCannotBecomeAContentClarification() {
        var context=context(AgentChannel.VOICE); var agent=orchestrator(context);
        when(service.send(context,"任意通知","我的内容")).thenReturn(new BarkNotificationService.Receipt(UUID.randomUUID(),"FAILED","PUSH_NOT_CONFIGURED",false));
        when(model.call(any(Prompt.class))).thenReturn(pushCall(),response(new AssistantMessage("Bark 已接受通知。")));
        assertThat(agent.stream(new AgentOrchestrator.AgentRequest(context,"测试伙伴",List.of(),"给我的手机发一条消息：你好"))
                .collectList().block()).allMatch(text->text.contains("没有获得 Bark 接受的确认") && !text.contains("你想发送"));
        verify(service).send(context,"任意通知","我的内容");
        verify(factory,never()).createChatClient(); verify(factory,never()).createLowLatencyChatClient();
    }

    @Test void factualPhoneRequestsMustReadTheirActualSourceBeforeSending() {
        var context=context(AgentChannel.WEB);
        var policy=new AgentToolPolicyInterceptor(context,Map.of(BarkNotificationTool.ID,
                new AgentToolPolicyInterceptor.ToolAuditMetadata(AgentToolSource.BUILTIN,null,null),CurrentDeviceWeatherTool.ID,
                new AgentToolPolicyInterceptor.ToolAuditMetadata(AgentToolSource.BUILTIN,null,null)),audit,mapper,8192,24576)
                .withNotificationPrerequisite(CurrentDeviceWeatherTool.ID);
        var push=ToolCallRequest.builder().toolCallId("push").toolName(BarkNotificationTool.ID).arguments("{}").context(Map.of()).build();
        var handler=mock(com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler.class);
        assertThat(policy.interceptToolCall(push,handler).isError()).isTrue(); verifyNoInteractions(handler);
        var read=ToolCallRequest.builder().toolCallId("read").toolName(CurrentDeviceWeatherTool.ID).arguments("{}").context(Map.of()).build();
        policy.interceptToolCall(read,ignored->ToolCallResponse.of("read",CurrentDeviceWeatherTool.ID,"{\"temperature\":20}"));
        assertThat(policy.interceptToolCall(push,ignored->ToolCallResponse.of("push",BarkNotificationTool.ID,"{\"status\":\"SENT\"}")).isError()).isFalse();
    }
}
