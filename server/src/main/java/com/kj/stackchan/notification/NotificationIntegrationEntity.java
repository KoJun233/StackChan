package com.kj.stackchan.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_integrations")
public class NotificationIntegrationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationIntegrationEntity() {
    }

    public NotificationIntegrationEntity(String name, UUID deviceId, boolean enabled, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.deviceId = deviceId;
        this.enabled = enabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, UUID deviceId, boolean enabled, Instant now) {
        this.name = name;
        this.deviceId = deviceId;
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public UUID getDeviceId() { return deviceId; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
