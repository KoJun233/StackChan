package com.kj.stackchan.device;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "body_motion_auto_settings")
public class BodyMotionAutoSettingsEntity {
    @Id
    @Column(name = "device_id")
    private UUID deviceId;
    @Column(nullable = false)
    private boolean enabled;

    protected BodyMotionAutoSettingsEntity() { }

    BodyMotionAutoSettingsEntity(UUID deviceId, boolean enabled) {
        this.deviceId = deviceId;
        this.enabled = enabled;
    }

    public UUID getDeviceId() { return deviceId; }
    public boolean isEnabled() { return enabled; }
    void setEnabled(boolean enabled) { this.enabled = enabled; }
}
