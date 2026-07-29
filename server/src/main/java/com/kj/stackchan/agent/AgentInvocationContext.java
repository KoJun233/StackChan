package com.kj.stackchan.agent;

import java.util.UUID;

public record AgentInvocationContext(
        UUID turnId,
        UUID conversationId,
        UUID deviceId,
        AgentChannel channel
) {
}
