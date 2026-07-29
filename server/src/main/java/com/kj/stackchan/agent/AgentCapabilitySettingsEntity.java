package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "agent_capability_settings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"capability_type", "capability_id"})
)
public class AgentCapabilitySettingsEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "capability_type", nullable = false, length = 32)
    private AgentCapabilityType capabilityType;

    @Column(name = "capability_id", nullable = false, length = 240)
    private String capabilityId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "source_id", length = 120)
    private String sourceId;

    @Column(name = "schema_sha256", length = 64)
    private String schemaSha256;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentCapabilitySettingsEntity() {
    }

    public AgentCapabilitySettingsEntity(
            AgentCapabilityType capabilityType,
            String capabilityId,
            boolean enabled,
            String sourceId,
            String schemaSha256,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.capabilityType = capabilityType;
        this.capabilityId = capabilityId;
        update(enabled, sourceId, schemaSha256, now);
    }

    public void update(boolean enabled, String sourceId, String schemaSha256, Instant now) {
        this.enabled = enabled;
        this.sourceId = sourceId;
        this.schemaSha256 = schemaSha256;
        this.updatedAt = now;
    }

    public AgentCapabilityType getCapabilityType() {
        return capabilityType;
    }

    public String getCapabilityId() {
        return capabilityId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSchemaSha256() {
        return schemaSha256;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
