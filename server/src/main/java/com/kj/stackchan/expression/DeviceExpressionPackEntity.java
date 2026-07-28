package com.kj.stackchan.expression;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_expression_packs")
public class DeviceExpressionPackEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "pack_id")
    private UUID packId;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DeviceExpressionPackStatus status;

    @Column(name = "command_id", length = 96)
    private String commandId;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "installed_at")
    private Instant installedAt;

    protected DeviceExpressionPackEntity() {
    }

    DeviceExpressionPackEntity(UUID deviceId, Instant now) {
        this.deviceId = deviceId;
        disable(now);
    }

    public UUID getDeviceId() { return deviceId; }
    public UUID getPackId() { return packId; }
    public boolean isEnabled() { return enabled; }
    public DeviceExpressionPackStatus getStatus() { return status; }
    public String getCommandId() { return commandId; }
    public String getFailureCode() { return failureCode; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getInstalledAt() { return installedAt; }

    void enable(UUID packId, Instant now) {
        this.packId = packId;
        enabled = true;
        status = DeviceExpressionPackStatus.READY;
        commandId = null;
        failureCode = null;
        installedAt = null;
        updatedAt = now;
    }

    void markInstalling(String commandId, Instant now) {
        status = DeviceExpressionPackStatus.INSTALLING;
        this.commandId = commandId;
        failureCode = null;
        updatedAt = now;
    }

    void returnToReady(Instant now) {
        status = DeviceExpressionPackStatus.READY;
        commandId = null;
        updatedAt = now;
    }

    void markActive(Instant now) {
        status = DeviceExpressionPackStatus.ACTIVE;
        commandId = null;
        failureCode = null;
        installedAt = now;
        updatedAt = now;
    }

    void markFailed(String code, Instant now) {
        status = DeviceExpressionPackStatus.FAILED;
        commandId = null;
        failureCode = code;
        updatedAt = now;
    }

    void disable(Instant now) {
        packId = null;
        enabled = false;
        status = DeviceExpressionPackStatus.DISABLED;
        commandId = null;
        failureCode = null;
        installedAt = null;
        updatedAt = now;
    }
}
