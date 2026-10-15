package com.kj.stackchan.delegation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Executes a persisted task using its assigned device/role and authorised capabilities. */
public interface DelegationTaskExecutor {
    TaskExecutionResult execute(TaskExecutionRequest request);

    record TaskExecutionRequest(UUID taskId, UUID runId, int attempt, UUID deviceId, UUID roleId,
                                String title, String description, String understanding,
                                Instant scheduledAt, boolean requiresToolEvidence,
                                List<String> allowedToolNames, List<String> requiredToolNames, LegacyHints legacyHints,
                                @com.fasterxml.jackson.annotation.JsonIgnore ExecutionGuard guard,
                                ProjectExecutionContext projectContext) {
        public TaskExecutionRequest(UUID taskId, UUID runId, int attempt, UUID deviceId, UUID roleId,
                                    String title, String description, String understanding,
                                    Instant scheduledAt, boolean requiresToolEvidence,
                                    List<String> allowedToolNames, List<String> requiredToolNames, LegacyHints legacyHints,
                                    ExecutionGuard guard) {
            this(taskId,runId,attempt,deviceId,roleId,title,description,understanding,scheduledAt,
                    requiresToolEvidence,allowedToolNames,requiredToolNames,legacyHints,guard,null);
        }
    }
    record ProjectExecutionContext(long taskRevision, List<String> sourceUrls, String sourceMode,
                                   UUID supersedesRunId, String preferenceFeedback, String previousOutput) {}
    record LegacyHints(List<String> sourceUrls, int maxItems) {}
    record TaskExecutionResult(String output, DelegationService.RunStatus status,
                               List<ExecutionStep> steps, List<ExecutionEvidence> evidence) {}
    record ExecutionStep(String toolName, String status, String summary) {}
    record ExecutionEvidence(String title, String url, String detail, String inputUrl, Instant readAt) {
        public ExecutionEvidence(String title,String url,String detail) { this(title,url,detail,null,null); }
    }
    @FunctionalInterface interface ExecutionGuard {
        Object execute(java.util.function.Supplier<Object> operation);
    }
}
