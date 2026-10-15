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
import com.kj.stackchan.delegation.PublicSourceScope;

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
    private String notificationPrerequisite;
    public AgentToolPolicyInterceptor withNotificationPrerequisite(String name) {
        notificationPrerequisite = name; return this;
    }
    private java.util.function.Function<java.util.function.Supplier<Object>,Object> executionGuard = operation -> operation.get();
    private final java.util.List<ToolStep> steps = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ToolEvidence> evidence = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<SourceRead> sourceReads = new java.util.concurrent.CopyOnWriteArrayList<>();
    private java.util.List<String> publicSourceScope = java.util.List.of();
    private java.time.Clock clock = java.time.Clock.systemUTC();

    public AgentToolPolicyInterceptor withPublicSourceScope(java.util.List<String> sourceUrls) {
        publicSourceScope = PublicSourceScope.canonical(sourceUrls); return this;
    }
    public AgentToolPolicyInterceptor withClock(java.time.Clock value) { clock = value; return this; }

    public AgentToolPolicyInterceptor withExecutionGuard(
            java.util.function.Function<java.util.function.Supplier<Object>,Object> guard) {
        executionGuard = guard; return this;
    }
    public java.util.List<ToolStep> steps() { return java.util.List.copyOf(steps); }
    public java.util.List<ToolEvidence> evidence() { return java.util.List.copyOf(evidence); }
    public java.util.List<SourceRead> sourceReads() { return java.util.List.copyOf(sourceReads); }
    public record ToolStep(String toolName, String status, String summary) {}
    public record ToolEvidence(String id, String title, String url, String detail, String inputUrl, java.time.Instant readAt) {
        public ToolEvidence(String id,String title,String url,String detail) { this(id,title,url,detail,null,null); }
    }
    public record SourceRead(String inputUrl, boolean succeeded, java.util.List<String> evidenceIds, java.time.Instant readAt) {}

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
        this.objectMapper = objectMapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.maxResultBytes = maxResultBytes;
        this.maxTotalResultBytes = maxTotalResultBytes;
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        long started = System.nanoTime();
        ToolAuditMetadata metadata = metadata(request);
        String inputUrl = null;
        boolean sourceRecorded = false;
        try {
            if ((context.channel() == AgentChannel.DELEGATION || BarkNotificationTool.ID.equals(request.getToolName()))
                    && !metadataByToolName.containsKey(request.getToolName())
                    && !"read_skill".equals(request.getToolName())) throw new IllegalStateException("TOOL_NOT_AUTHORIZED");
            if (BarkNotificationTool.ID.equals(request.getToolName()) && notificationPrerequisite != null
                    && !wasSuccessful(notificationPrerequisite)) throw new IllegalStateException("PUSH_READ_REQUIRED");
            if (PublicSourceReadTool.ID.equals(request.getToolName())) {
                var arguments = objectMapper.readTree(request.getArguments());
                if (!publicSourceScope.isEmpty()) {
                    if (arguments == null || !arguments.path("url").isTextual()) throw new IllegalStateException("SOURCE_SCOPE_REQUIRED");
                    String proposed = PublicSourceScope.canonical(arguments.path("url").textValue());
                    if (!publicSourceScope.contains(proposed)) throw new IllegalStateException("SOURCE_NOT_AUTHORIZED");
                    inputUrl = proposed;
                } else if (arguments != null && arguments.path("url").isTextual()) {
                    inputUrl = PublicSourceScope.canonical(arguments.path("url").textValue());
                }
            }
            ToolCallResponse response = (ToolCallResponse) executionGuard.apply(() -> handler.call(request));
            if (response == null || response.isError()) {
                if (inputUrl != null) {
                    sourceReads.add(new SourceRead(inputUrl,false,java.util.List.of(),clock.instant()));
                    sourceRecorded = true;
                }
                steps.add(new ToolStep(request.getToolName(), "FAILED", "工具未返回可确认的结果。"));
                recordBestEffort(request, metadata, AgentToolOutcome.TOOL_FAILED, started, 0, false);
                return ToolCallResponse.error(request.getToolCallId(), request.getToolName(), TOOL_FAILURE);
            }
            String result = response.getResult() == null ? "" : response.getResult();
            if (BarkNotificationTool.ID.equals(request.getToolName())) {
                var receipt = objectMapper.readTree(result);
                if (receipt == null || !"SENT".equals(receipt.path("status").asText())) {
                    steps.add(new ToolStep(request.getToolName(), "FAILED", "Bark 未确认接受通知，请检查手机历史；不自动重发。"));
                    recordBestEffort(request, metadata, AgentToolOutcome.TOOL_FAILED, started, 0, false);
                    return ToolCallResponse.error(request.getToolCallId(), request.getToolName(), result);
                }
            }
            result = bindEvidence(request.getToolName(), result, inputUrl);
            if (inputUrl != null) {
                String requestedUrl = inputUrl;
                sourceReads.add(new SourceRead(inputUrl,true,evidence.stream().filter(item -> requestedUrl.equals(item.inputUrl()))
                        .map(ToolEvidence::id).toList(),clock.instant()));
                sourceRecorded = true;
            }
            int remaining = maxTotalResultBytes - totalResultBytes.get();
            if (remaining <= 0) {
                steps.add(new ToolStep(request.getToolName(), "FAILED", "工具结果达到本次调用预算。"));
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
            steps.add(new ToolStep(request.getToolName(), "SUCCEEDED",
                    BarkNotificationTool.ID.equals(request.getToolName()) ? "Bark 已接受通知；手机送达和阅读需在手机确认。" :
                    bounded.truncated() ? "工具已返回，内容因长度限制截取。" : "工具已实际返回结果。"));
            recordBestEffort(request, metadata, outcome, started, bounded.bytes(), bounded.truncated());
            return new ToolCallResponse(
                    bounded.value(),
                    response.getToolName(),
                    response.getToolCallId(),
                    response.getStatus(),
                    response.getMetadata()
            );
        } catch (Exception exception) {
            if (inputUrl != null && !sourceRecorded) sourceReads.add(new SourceRead(inputUrl,false,java.util.List.of(),clock.instant()));
            steps.add(new ToolStep(request.getToolName(), "FAILED", "工具未执行或执行失败。"));
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

    private String bindEvidence(String name, String result, String inputUrl) {
        if (!PublicSourceReadTool.ID.equals(name)) return result;
        try {
            var root = objectMapper.readTree(result);
            var references = root.path("evidence");
            if (!references.isArray()) return result;
            for (var reference : references) {
                if (!reference.isObject() || evidence.size() >= 40) continue;
                String url = reference.path("url").asText("");
                var uri = java.net.URI.create(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) continue;
                String id = "E" + (evidence.size() + 1);
                String title = reference.path("title").asText("");
                String detail = reference.path("detail").asText("");
                evidence.add(new ToolEvidence(id, title, url, detail, inputUrl, clock.instant()));
                ((com.fasterxml.jackson.databind.node.ObjectNode) reference).put("id", id);
            }
            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) { return result; }
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
