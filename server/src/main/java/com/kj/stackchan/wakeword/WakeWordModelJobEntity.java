package com.kj.stackchan.wakeword;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "wake_word_model_jobs")
public class WakeWordModelJobEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false, length = 80)
    private String phrase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WakeWordModelJobStatus status;

    @Column(name = "model_name", length = 32)
    private String modelName;

    @Column(name = "artifact_sha256", length = 64)
    private String artifactSha256;

    @Column(name = "artifact_size")
    private Integer artifactSize;

    @Column(name = "artifact")
    private byte[] artifact;

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

    @Column(name = "installed_at")
    private Instant installedAt;

    protected WakeWordModelJobEntity() {
    }

    WakeWordModelJobEntity(UUID deviceId, String phrase, GeneratedWakeWordModel model, Instant now) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.phrase = phrase;
        this.createdAt = now;
        this.updatedAt = now;
        markReady(model, now);
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public String getPhrase() { return phrase; }
    public WakeWordModelJobStatus getStatus() { return status; }
    public String getModelName() { return modelName; }
    public String getArtifactSha256() { return artifactSha256; }
    public Integer getArtifactSize() { return artifactSize; }
    public byte[] getArtifact() { return artifact == null ? null : artifact.clone(); }
    public String getCommandId() { return commandId; }
    public Boolean getCommandAccepted() { return commandAccepted; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getInstalledAt() { return installedAt; }

    void markReady(GeneratedWakeWordModel generated, Instant now) {
        status = WakeWordModelJobStatus.READY;
        modelName = generated.modelName();
        artifactSha256 = generated.sha256();
        artifact = generated.artifact().clone();
        artifactSize = artifact.length;
        commandId = null;
        commandAccepted = null;
        failureCode = null;
        updatedAt = now;
    }

    void markInstalling(String commandId, Instant now) {
        status = WakeWordModelJobStatus.INSTALLING;
        this.commandId = commandId;
        commandAccepted = null;
        updatedAt = now;
    }

    void markCommandAccepted(Instant now) {
        commandAccepted = true;
        updatedAt = now;
    }

    void returnToReady(Instant now) {
        status = WakeWordModelJobStatus.READY;
        commandId = null;
        commandAccepted = null;
        updatedAt = now;
    }

    void markInstalled(Instant now) {
        status = WakeWordModelJobStatus.INSTALLED;
        failureCode = null;
        installedAt = now;
        updatedAt = now;
        artifact = null;
    }

    void markFailed(String code, Instant now) {
        status = WakeWordModelJobStatus.FAILED;
        failureCode = code;
        updatedAt = now;
        artifact = null;
    }

    void markRolledBack(Instant now) {
        status = WakeWordModelJobStatus.ROLLED_BACK;
        failureCode = "device_rollback";
        updatedAt = now;
        artifact = null;
    }
}
