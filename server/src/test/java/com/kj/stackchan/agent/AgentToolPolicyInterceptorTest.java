package com.kj.stackchan.agent;

import java.util.Map;
import java.util.UUID;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentToolPolicyInterceptorTest {
    @Test void aPausedDelegationCannotExecuteTheToolAndDoesNotRecordSuccess() {
        var handler=mock(com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler.class);
        var ctx=new AgentInvocationContext(UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),AgentChannel.DELEGATION,UUID.randomUUID(),1);
        var policy=new AgentToolPolicyInterceptor(ctx,Map.of("computer_start",new AgentToolPolicyInterceptor.ToolAuditMetadata(
                AgentToolSource.MCP,"connection",null)),auditService,new ObjectMapper(),8192,24576)
                .withExecutionGuard(operation->{throw new IllegalStateException("paused");});
        assertThat(policy.interceptToolCall(request("computer_start","{}"),handler).isError()).isTrue();
        verifyNoInteractions(handler);
        assertThat(policy.wasSuccessful("computer_start")).isFalse();
        assertThat(policy.steps()).extracting(AgentToolPolicyInterceptor.ToolStep::status).containsExactly("FAILED");
    }
    @Test void delegationCitationsAreAssignedOnlyAfterAnActualAuthorizedSourceRead() {
        var ctx=new AgentInvocationContext(UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),AgentChannel.DELEGATION,UUID.randomUUID(),1);
        var policy=new AgentToolPolicyInterceptor(ctx,Map.of(PublicSourceReadTool.ID,new AgentToolPolicyInterceptor.ToolAuditMetadata(
                AgentToolSource.BUILTIN,null,null)),auditService,new ObjectMapper(),8192,24576);
        var response=policy.interceptToolCall(request(PublicSourceReadTool.ID,"{}"),ignored->ToolCallResponse.of("call",PublicSourceReadTool.ID,
                "{\"evidence\":[{\"title\":\"实际来源\",\"url\":\"https://example.com/post\",\"detail\":\"正文\"}]}"));
        assertThat(response.getResult()).contains("E1");
        assertThat(policy.evidence()).extracting(AgentToolPolicyInterceptor.ToolEvidence::url).containsExactly("https://example.com/post");
        var handler=mock(com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler.class);
        assertThat(policy.interceptToolCall(request("not_authorized","{}"),handler).isError()).isTrue();
        verifyNoInteractions(handler);
    }

    private final AgentToolAuditService auditService = mock(AgentToolAuditService.class);
    private final AgentInvocationContext context = new AgentInvocationContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            AgentChannel.VOICE
    );

    @Test
    void truncatesToolResultsAndAuditsOnlyMetadata() {
        AgentToolPolicyInterceptor interceptor = interceptor(64, 128);
        ToolCallRequest request = request("next_reminder", "{\"private\":\"never-audited\"}");

        ToolCallResponse response = interceptor.interceptToolCall(
                request,
                ignored -> ToolCallResponse.of("call-1", "next_reminder", "内容".repeat(100))
        );

        assertThat(response.getResult()).endsWith("[tool_result_truncated]");
        assertThat(response.getResult().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(64);
        verify(auditService).record(
                eq(context),
                eq("reminder-query"),
                eq("next_reminder"),
                eq(AgentToolSource.SKILL),
                eq(null),
                eq(AgentToolOutcome.RESULT_TRUNCATED),
                anyLong(),
                eq(response.getResult().getBytes(java.nio.charset.StandardCharsets.UTF_8).length),
                eq(true)
        );
    }

    @Test
    void stopsReturningToolBodiesAfterTheTotalBudgetIsUsed() {
        AgentToolPolicyInterceptor interceptor = interceptor(64, 5);

        ToolCallResponse first = interceptor.interceptToolCall(
                request("next_reminder", "{}"),
                ignored -> ToolCallResponse.of("call-1", "next_reminder", "12345")
        );
        ToolCallResponse second = interceptor.interceptToolCall(
                request("next_reminder", "{}"),
                ignored -> ToolCallResponse.of("call-2", "next_reminder", "another result")
        );

        assertThat(first.getResult()).isEqualTo("12345");
        assertThat(second.isError()).isTrue();
        assertThat(second.getResult()).contains("tool_result_budget_exceeded");
    }

    @Test
    void convertsToolExceptionsToASafeResult() {
        AgentToolPolicyInterceptor interceptor = interceptor(64, 128);

        ToolCallResponse response = interceptor.interceptToolCall(
                request("next_reminder", "{\"secret\":\"not-logged\"}"),
                ignored -> {
                    throw new IllegalStateException("provider returned a secret body");
                }
        );

        assertThat(response.isError()).isTrue();
        assertThat(response.getResult()).contains("tool_unavailable").doesNotContain("secret body");
    }

    private AgentToolPolicyInterceptor interceptor(int maxResultBytes, int maxTotalBytes) {
        return new AgentToolPolicyInterceptor(
                context,
                Map.of("next_reminder", new AgentToolPolicyInterceptor.ToolAuditMetadata(
                        AgentToolSource.SKILL,
                        null,
                        "reminder-query"
                )),
                auditService,
                new ObjectMapper(),
                maxResultBytes,
                maxTotalBytes
        );
    }

    private ToolCallRequest request(String toolName, String arguments) {
        return ToolCallRequest.builder()
                .toolName(toolName)
                .arguments(arguments)
                .toolCallId(UUID.randomUUID().toString())
                .context(Map.of())
                .build();
    }
}
