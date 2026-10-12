package com.kj.stackchan.delegation;

import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.agent.AgentOrchestrator;
import com.kj.stackchan.agent.AgentToolPolicyInterceptor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AgentDelegationTaskExecutorTest {
    private final AgentDelegationTaskExecutor executor=new AgentDelegationTaskExecutor(mock(AgentOrchestrator.class),new ObjectMapper());
    private AgentOrchestrator.TaskAgentResponse response(String json,List<AgentToolPolicyInterceptor.ToolStep> steps) {
        return new AgentOrchestrator.TaskAgentResponse(json,steps,List.of());
    }
    @Test void unrelatedSuccessfulToolDoesNotProveTheRequestedAction() throws Exception {
        var result=executor.result(response("{\"status\":\"SUCCEEDED\",\"output\":\"已完成\",\"evidenceIds\":[]}",
                List.of(new AgentToolPolicyInterceptor.ToolStep("current_date_time","SUCCEEDED","返回当前时间"))),
                true,List.of("computer_control"));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
    }
    @Test void noToolCannotEstablishThatAQueryFoundNoUpdates() throws Exception {
        var result=executor.result(response("{\"status\":\"NO_UPDATES\",\"output\":\"没有新内容\",\"evidenceIds\":[]}",List.of()),false,List.of());
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
    }
    @Test void ordinaryContentGenerationRemainsAValidTask() throws Exception {
        var result=executor.result(response("{\"status\":\"SUCCEEDED\",\"output\":\"今天先完成最重要的一件事。\",\"evidenceIds\":[]}",List.of()),false,List.of());
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        assertThat(result.steps()).isEmpty();
    }
    @Test void citationsMustResolveToActualToolEvidence() throws Exception {
        var steps=List.of(new AgentToolPolicyInterceptor.ToolStep("read_public_source","SUCCEEDED","已读取来源"));
        var actual=new AgentOrchestrator.TaskAgentResponse("{\"status\":\"SUCCEEDED\",\"output\":\"本次更新。\",\"evidenceIds\":[\"E1\"]}",steps,
                List.of(new AgentToolPolicyInterceptor.ToolEvidence("E1","官方更新","https://example.com/news","实际来源内容")));
        assertThat(executor.result(actual,true,List.of("read_public_source")).evidence().getFirst().url()).isEqualTo("https://example.com/news");
        assertThatThrownBy(()->executor.result(new AgentOrchestrator.TaskAgentResponse(actual.text().replace("E1","E999"),steps,actual.evidence()),true,List.of("read_public_source")))
                .isInstanceOf(Exception.class);
    }
    @Test void modelCannotAddAnUnverifiedUrlOrUnknownOutcome() {
        assertThatThrownBy(()->executor.result(response("{\"status\":\"SUCCEEDED\",\"output\":\"https://fake.example/news\",\"evidenceIds\":[]}",List.of()),false,List.of()))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(()->executor.result(response("{\"status\":\"RUNNING\",\"output\":\"已完成\",\"evidenceIds\":[]}",List.of()),false,List.of()))
                .isInstanceOf(Exception.class);
    }
}
