package com.kj.stackchan.agent;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AgentToolPolicyInterceptor extends ToolInterceptor {

    private static final String TOOL_FAILURE = "tool_unavailable";
    private static final String RESULT_BUDGET_EXCEEDED = "tool_result_budget_exceeded";
    private static final String TRUNCATION_MARKER = "\n[tool_result_truncated]";

    private final AgentInvocationContext context;
    private final Map<String, ToolAuditMetadata> metadataByToolName;
    private final AgentToolAuditService auditService;
    private final ObjectMapper objectMapper;
    private final AtomicInteger totalResultBytes = new AtomicInteger();
    private final Set<String> successfulToolNames = ConcurrentHashMap.newKeySet();
    private final int maxResultBytes;
    private final int maxTotalResultBytes;

    public AgentToolPolicyInterceptor(
            AgentInvocationContext context,
            Map<String, ToolAuditMetadata> metadataByToolName,
            AgentToolAuditService auditService,
            ObjectMapper objectMapper,
            int maxResultBytes,
            int maxTotalResultBytes
    ) {
        this.context = context;
        this.metadataByToolName = Map.copyOf(metadataByToolName);
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.maxResultBytes = maxResultBytes;
        this.maxTotalResultBytes = maxTotalResultBytes;
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        long started = System.nanoTime();
        ToolAuditMetadata metadata = metadata(request);
        try {
            ToolCallResponse response = handler.call(request);
            if (response == null || response.isError()) {
                recordBestEffort(request, metadata, AgentToolOutcome.TOOL_FAILED, started, 0, false);
                return ToolCallResponse.error(request.getToolCallId(), request.getToolName(), TOOL_FAILURE);
            }
            String result = response.getResult() == null ? "" : response.getResult();
            int remaining = maxTotalResultBytes - totalResultBytes.get();
            if (remaining <= 0) {
                recordBestEffort(
                        request,
                        metadata,
                        AgentToolOutcome.RESULT_BUDGET_EXCEEDED,
                        started,
                        0,
                        true
                );
                return ToolCallResponse.error(
                        request.getToolCallId(),
                        request.getToolName(),
                        RESULT_BUDGET_EXCEEDED
                );
            }
            TruncatedResult bounded = truncate(result, Math.min(maxResultBytes, remaining));
            totalResultBytes.addAndGet(bounded.bytes());
            AgentToolOutcome outcome = bounded.truncated()
                    ? AgentToolOutcome.RESULT_TRUNCATED
                    : AgentToolOutcome.SUCCESS;
            successfulToolNames.add(request.getToolName());
            recordBestEffort(request, metadata, outcome, started, bounded.bytes(), bounded.truncated());
            return new ToolCallResponse(
                    bounded.value(),
                    response.getToolName(),
                    response.getToolCallId(),
                    response.getStatus(),
                    response.getMetadata()
            );
        } catch (RuntimeException exception) {
            recordBestEffort(request, metadata, AgentToolOutcome.TOOL_FAILED, started, 0, false);
            return ToolCallResponse.error(request.getToolCallId(), request.getToolName(), TOOL_FAILURE);
        }
    }

    @Override
    public String getName() {
        return "stackchan-tool-policy";
    }

    public boolean wasSuccessful(String toolName) {
        return successfulToolNames.contains(toolName);
    }

    private ToolAuditMetadata metadata(ToolCallRequest request) {
        ToolAuditMetadata known = metadataByToolName.get(request.getToolName());
        if (known != null) {
            return known;
        }
        if ("read_skill".equals(request.getToolName())) {
            return new ToolAuditMetadata(AgentToolSource.SKILL, null, skillName(request.getArguments()));
        }
        return new ToolAuditMetadata(AgentToolSource.BUILTIN, null, null);
    }

    private String skillName(String arguments) {
        try {
            JsonNode node = objectMapper.readTree(arguments);
            String skillName = node.path("skill_name").asText(null);
            return skillName == null || skillName.length() > 64 ? null : skillName;
        } catch (RuntimeException | java.io.IOException ignored) {
            return null;
        }
    }

    private void recordBestEffort(
            ToolCallRequest request,
            ToolAuditMetadata metadata,
            AgentToolOutcome outcome,
            long started,
            int resultBytes,
            boolean truncated
    ) {
        try {
            auditService.record(
                    context,
                    metadata.skillId(),
                    request.getToolName(),
                    metadata.source(),
                    metadata.sourceId(),
                    outcome,
                    Math.max(0, (System.nanoTime() - started) / 1_000_000),
                    resultBytes,
                    truncated
            );
        } catch (RuntimeException ignored) {
            // Never log tool input, output, model response or authentication data.
        }
    }

    private TruncatedResult truncate(String value, int maxBytes) {
        byte[] full = value.getBytes(StandardCharsets.UTF_8);
        if (full.length <= maxBytes) {
            return new TruncatedResult(value, full.length, false);
        }
        byte[] marker = TRUNCATION_MARKER.getBytes(StandardCharsets.UTF_8);
        int contentBudget = Math.max(0, maxBytes - marker.length);
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (used + characterBytes > contentBudget) {
                break;
            }
            builder.append(character);
            used += characterBytes;
            offset += Character.charCount(codePoint);
        }
        if (marker.length <= maxBytes) {
            builder.append(TRUNCATION_MARKER);
            used += marker.length;
        }
        return new TruncatedResult(builder.toString(), used, true);
    }

    public record ToolAuditMetadata(AgentToolSource source, String sourceId, String skillId) {
    }

    private record TruncatedResult(String value, int bytes, boolean truncated) {
    }
}
