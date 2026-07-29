package com.kj.stackchan.agent;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_runtime_settings")
public class AgentRuntimeSettingsEntity {

    static final short SINGLETON_ID = 1;

    @Id
    private short id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentRuntimeSettingsEntity() {
    }

    public AgentRuntimeSettingsEntity(boolean enabled, Instant now) {
        this.id = SINGLETON_ID;
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void update(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
