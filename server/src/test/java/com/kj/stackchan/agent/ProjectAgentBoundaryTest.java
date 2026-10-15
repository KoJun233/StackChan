package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectAgentBoundaryTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void explicitEmptyProjectPermissionDoesNotInheritLegacyBuiltinReads(boolean project) {
        var settings=mock(AgentSettingsService.class);
        when(settings.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(true,true,true,Instant.EPOCH));
        var assembly=mock(AgentToolAssemblyService.class); var model=mock(ChatModel.class); var factory=mock(LlmRuntimeClientFactory.class);
        when(factory.createAgentChatModel()).thenReturn(model);
        when(model.getDefaultOptions()).thenReturn(OpenAiChatOptions.builder().model("synthetic-project-test").build());
        var context=new AgentInvocationContext(UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),AgentChannel.DELEGATION,UUID.randomUUID(),1);
        var callbacks=List.of(ToolCallbacks.from(new SyntheticRead())[0],ToolCallbacks.from(new SyntheticTime())[0],ToolCallbacks.from(new SyntheticPhone())[0]);
        var builtin=new AgentToolPolicyInterceptor.ToolAuditMetadata(AgentToolSource.BUILTIN,null,null);
        when(assembly.assemble(context)).thenReturn(new AgentToolAssemblyService.AgentToolAssembly(callbacks,Map.of(),mock(SkillRegistry.class),List.of(),
                Map.of("read_public_source",builtin,"current_date_time",builtin,"send_phone_notification",builtin),List.of()));
        when(model.call(any(Prompt.class))).thenAnswer(call->{
            var options=(OpenAiChatOptions)((Prompt)call.getArgument(0)).getOptions();
            if(project) assertThat(options.getToolCallbacks()).isNullOrEmpty();
            else assertThat(options.getToolCallbacks()).extracting(tool->tool.getToolDefinition().name()).containsExactlyInAnyOrder("read_public_source","current_date_time");
            return new ChatResponse(List.of(new Generation(new AssistantMessage("合成文字生成"))));
        });
        var orchestrator=new AgentOrchestrator(settings,assembly,mock(AgentToolAuditService.class),factory,new ObjectMapper(),new AppProperties());
        var request=new AgentOrchestrator.AgentRequest(context,"合成只读任务",List.of(),"写一份提纲");
        var result=project?orchestrator.executeTask(request,List.of(),operation->operation.get(),List.of())
                :orchestrator.executeTask(request,List.of(),operation->operation.get());
        assertThat(result.text()).isEqualTo("合成文字生成");
    }
    static class SyntheticRead { @Tool(name="read_public_source",description="Synthetic read") public String read(String url) { return "synthetic"; } }
    static class SyntheticTime { @Tool(name="current_date_time",description="Synthetic time") public String time() { return "synthetic"; } }
    static class SyntheticPhone { @Tool(name="send_phone_notification",description="Synthetic phone") public String send() { return "synthetic"; } }
}
