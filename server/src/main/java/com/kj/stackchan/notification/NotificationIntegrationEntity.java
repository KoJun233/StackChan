package com.kj.stackchan.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.kj.stackchan.role.CompanionRoleEntity;

@Entity
@Table(name = "notification_integrations")
public class NotificationIntegrationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "digest_window_seconds", nullable = false)
    private int digestWindowSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationIntegrationEntity() {
    }

    public NotificationIntegrationEntity(String name, UUID deviceId, boolean enabled, Instant now) {
        this(name, deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, enabled, now);
    }

    public NotificationIntegrationEntity(String name, UUID deviceId, UUID roleId, boolean enabled, Instant now) {
        this(name, deviceId, roleId, enabled, 0, now);
    }

    public NotificationIntegrationEntity(
            String name, UUID deviceId, UUID roleId, boolean enabled, int digestWindowSeconds, Instant now
    ) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.enabled = enabled;
        this.digestWindowSeconds = digestWindowSeconds;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, UUID deviceId, boolean enabled, Instant now) {
        update(name, deviceId, enabled, digestWindowSeconds, now);
    }

    public void update(String name, UUID deviceId, boolean enabled, int digestWindowSeconds, Instant now) {
        this.name = name;
        this.deviceId = deviceId;
        this.enabled = enabled;
        this.digestWindowSeconds = digestWindowSeconds;
        this.updatedAt = now;
    }

    public void disable(Instant now) { this.enabled = false; this.updatedAt = now; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getRoleId() { return roleId; }
    public boolean isEnabled() { return enabled; }
    public int getDigestWindowSeconds() { return digestWindowSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
