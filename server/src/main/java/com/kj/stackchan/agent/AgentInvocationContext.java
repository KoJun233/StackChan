package com.kj.stackchan.agent;

import java.util.UUID;

public record AgentInvocationContext(
        UUID turnId,
        UUID conversationId,
        UUID deviceId,
        UUID roleId,
        AgentChannel channel,
        UUID delegationRunId,
        Integer delegationAttempt
) {
    public AgentInvocationContext(UUID turnId, UUID conversationId, UUID deviceId, UUID roleId, AgentChannel channel) {
        this(turnId, conversationId, deviceId, roleId, channel, null, null);
    }
    public AgentInvocationContext(UUID turnId, UUID conversationId, UUID deviceId, AgentChannel channel) {
        this(turnId, conversationId, deviceId,
                com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID, channel);
    }
}
