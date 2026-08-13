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
    @Id @Column(name = "device_id", nullable = false) private UUID deviceId;
    @Column(name = "role_id", nullable = false) private UUID roleId;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected DeviceActiveRoleEntity() {}
    public DeviceActiveRoleEntity(UUID deviceId, UUID roleId, Instant updatedAt) {
        this.deviceId = deviceId; this.roleId = roleId; this.updatedAt = updatedAt;
    }
    public void switchTo(UUID roleId, Instant now) { this.roleId = roleId; this.updatedAt = now; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getRoleId() { return roleId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
