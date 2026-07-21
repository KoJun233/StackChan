package com.kj.stackchan.reminder;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reminders")
public class ReminderEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ReminderStatus status;

    @Column(name = "command_id", length = 96)
    private String commandId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "audio_payload")
    private byte[] audioPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReminderEntity() {
    }

    public ReminderEntity(UUID deviceId, String content, Instant scheduledAt, String zoneId, Instant now) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.content = content;
        this.scheduledAt = scheduledAt;
        this.zoneId = zoneId;
        this.status = ReminderStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(UUID deviceId, String content, Instant scheduledAt, String zoneId, Instant now) {
        this.deviceId = deviceId;
        this.content = content;
        this.scheduledAt = scheduledAt;
        this.zoneId = zoneId;
        this.status = ReminderStatus.PENDING;
        this.commandId = null;
        this.failureCode = null;
        this.audioPayload = null;
        this.updatedAt = now;
    }

    public void markDispatched(String commandId, byte[] audioPayload, Instant now) {
        this.status = ReminderStatus.DISPATCHED;
        this.commandId = commandId;
        this.audioPayload = audioPayload.clone();
        this.attemptCount++;
        this.lastAttemptAt = now;
        this.failureCode = null;
        this.updatedAt = now;
    }

    public void returnToPending(Instant now) {
        this.status = ReminderStatus.PENDING;
        this.commandId = null;
        this.audioPayload = null;
        this.updatedAt = now;
    }

    public void markDelivered(Instant now) {
        this.status = ReminderStatus.DELIVERED;
        this.audioPayload = null;
        this.failureCode = null;
        this.updatedAt = now;
    }

    public void markFailed(String failureCode, Instant now) {
        this.status = ReminderStatus.FAILED;
        this.audioPayload = null;
        this.failureCode = failureCode;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public String getContent() {
        return content;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getZoneId() {
        return zoneId;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public String getCommandId() {
        return commandId;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public byte[] getAudioPayload() {
        return audioPayload == null ? null : audioPayload.clone();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
