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

class AgentToolPolicyInterceptorTest {

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
