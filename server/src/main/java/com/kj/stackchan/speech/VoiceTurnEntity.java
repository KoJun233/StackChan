package com.kj.stackchan.speech;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "voice_turns")
public class VoiceTurnEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VoiceTurnStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 40)
    private VoiceTurnFailureCode failureCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VoiceTurnEntity() {
    }

    VoiceTurnEntity(UUID id, UUID deviceId, Instant now) {
        this.id = id;
        this.deviceId = deviceId;
        this.status = VoiceTurnStatus.IN_PROGRESS;
        this.startedAt = now;
        this.updatedAt = now;
    }

    void apply(VoiceTurnStage stage, VoiceTurnFailureCode newFailureCode, Instant now) {
        if (status != VoiceTurnStatus.COMPLETED &&
                status != VoiceTurnStatus.CANCELLED &&
                status != VoiceTurnStatus.FAILED) {
            if (stage == VoiceTurnStage.FAILED) {
                status = VoiceTurnStatus.FAILED;
                failureCode = newFailureCode;
            } else if (stage == VoiceTurnStage.CANCELLED) {
                status = VoiceTurnStatus.CANCELLED;
                failureCode = null;
            } else if (stage == VoiceTurnStage.LISTENING_RESUMED
                    || stage == VoiceTurnStage.FOLLOW_UP_TIMEOUT
                    || stage == VoiceTurnStage.CONVERSATION_ENDED) {
                status = VoiceTurnStatus.COMPLETED;
                failureCode = null;
            } else if (stage == VoiceTurnStage.TTS_COMPLETED) {
                status = VoiceTurnStatus.RESPONSE_READY;
            }
        }
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public VoiceTurnStatus getStatus() {
        return status;
    }

    public VoiceTurnFailureCode getFailureCode() {
        return failureCode;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
