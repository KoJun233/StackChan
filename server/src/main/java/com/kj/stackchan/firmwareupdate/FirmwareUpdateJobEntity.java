package com.kj.stackchan.firmwareupdate;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "firmware_update_jobs")
public class FirmwareUpdateJobEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Column(name = "from_version", nullable = false, length = 80)
    private String fromVersion;

    @Column(name = "target_version", nullable = false, length = 32)
    private String targetVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FirmwareUpdateStatus status;

    @Column(name = "command_id", length = 96)
    private String commandId;

    @Column(name = "command_accepted")
    private Boolean commandAccepted;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected FirmwareUpdateJobEntity() {
    }

    FirmwareUpdateJobEntity(UUID deviceId, FirmwareReleaseEntity release, String fromVersion, Instant now) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.releaseId = release.getId();
        this.fromVersion = fromVersion;
        this.targetVersion = release.getVersion();
        this.status = FirmwareUpdateStatus.READY;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getReleaseId() { return releaseId; }
    public String getFromVersion() { return fromVersion; }
    public String getTargetVersion() { return targetVersion; }
    public FirmwareUpdateStatus getStatus() { return status; }
    public String getCommandId() { return commandId; }
    public Boolean getCommandAccepted() { return commandAccepted; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }

    void markInstalling(String commandId, Instant now) {
        status = FirmwareUpdateStatus.INSTALLING;
        this.commandId = commandId;
        commandAccepted = null;
        failureCode = null;
        updatedAt = now;
    }

    void markCommandAccepted(Instant now) {
        commandAccepted = true;
        updatedAt = now;
    }

    void returnToReady(Instant now) {
        status = FirmwareUpdateStatus.READY;
        commandId = null;
        commandAccepted = null;
        updatedAt = now;
    }

    void markInstalled(Instant now) {
        status = FirmwareUpdateStatus.INSTALLED;
        failureCode = null;
        completedAt = now;
        updatedAt = now;
    }

    void markFailed(String failureCode, Instant now) {
        status = FirmwareUpdateStatus.FAILED;
        this.failureCode = failureCode;
        completedAt = now;
        updatedAt = now;
    }

    void markRolledBack(Instant now) {
        status = FirmwareUpdateStatus.ROLLED_BACK;
        failureCode = "device_rollback";
        completedAt = now;
        updatedAt = now;
    }
}
