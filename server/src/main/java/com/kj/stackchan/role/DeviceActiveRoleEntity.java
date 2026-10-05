package com.kj.stackchan.role;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_active_roles")
public class DeviceActiveRoleEntity {
    public static final UUID IMPLICIT_DEFAULT_CONSENT_EPOCH = new UUID(0, 0);
    @Id @Column(name = "device_id", nullable = false) private UUID deviceId;
    @Column(name = "role_id", nullable = false) private UUID roleId;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "consent_epoch", nullable = false) private UUID consentEpoch;
    protected DeviceActiveRoleEntity() {}
    public DeviceActiveRoleEntity(UUID deviceId, UUID roleId, Instant updatedAt) {
        this.deviceId = deviceId; this.roleId = roleId; this.updatedAt = updatedAt;
        this.consentEpoch = CompanionRoleEntity.DEFAULT_ROLE_ID.equals(roleId)
                ? IMPLICIT_DEFAULT_CONSENT_EPOCH : UUID.randomUUID();
    }
    public void switchTo(UUID roleId, Instant now) {
        if (!this.roleId.equals(roleId)) this.consentEpoch = UUID.randomUUID();
        this.roleId = roleId; this.updatedAt = now;
    }
    public UUID getDeviceId() { return deviceId; }
    public UUID getRoleId() { return roleId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getConsentEpoch() { return consentEpoch; }
}
