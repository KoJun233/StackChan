package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_tool_invocations")
public class AgentToolInvocationEntity {

    @Id
    private UUID id;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentChannel channel;

    @Column(name = "skill_id", length = 64)
    private String skillId;

    @Column(name = "tool_name", nullable = false, length = 240)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 24)
    private AgentToolSource sourceType;

    @Column(name = "source_id", length = 120)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentToolOutcome outcome;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "result_bytes", nullable = false)
    private int resultBytes;

    @Column(nullable = false)
    private boolean truncated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentToolInvocationEntity() {
    }

    public AgentToolInvocationEntity(
            AgentInvocationContext context,
            String skillId,
            String toolName,
            AgentToolSource sourceType,
            String sourceId,
            AgentToolOutcome outcome,
            long durationMs,
            int resultBytes,
            boolean truncated,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.turnId = context.turnId();
        this.conversationId = context.conversationId();
        this.deviceId = context.deviceId();
        this.channel = context.channel();
        this.skillId = skillId;
        this.toolName = toolName;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.outcome = outcome;
        this.durationMs = durationMs;
        this.resultBytes = resultBytes;
        this.truncated = truncated;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTurnId() { return turnId; }
    public UUID getConversationId() { return conversationId; }
    public UUID getDeviceId() { return deviceId; }
    public AgentChannel getChannel() { return channel; }
    public String getSkillId() { return skillId; }
    public String getToolName() { return toolName; }
    public AgentToolSource getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public AgentToolOutcome getOutcome() { return outcome; }
    public long getDurationMs() { return durationMs; }
    public int getResultBytes() { return resultBytes; }
    public boolean isTruncated() { return truncated; }
    public Instant getCreatedAt() { return createdAt; }
}
