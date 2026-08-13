package com.kj.stackchan.voiceaction;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "voice_action_proposals")
public class VoiceActionProposalEntity {
    @Id private UUID id;
    @Column(name = "actor_id", nullable = false, length = 64) private String actorId;
    @Column(name = "device_id", nullable = false) private UUID deviceId;
    @Column(name = "role_id", nullable = false) private UUID roleId;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "source_turn_id", nullable = false) private UUID sourceTurnId;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 40) private VoiceActionType actionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private VoiceActionStatus status;
    @Column(name = "confirmation_required", nullable = false) private boolean confirmationRequired;
    @Column(length = 2000) private String content;
    @Column(length = 120) private String title;
    @Column(name = "scheduled_at") private Instant scheduledAt;
    @Column(name = "zone_id", length = 80) private String zoneId;
    @Column(name = "recurrence_type", length = 16) private String recurrenceType;
    @Column(name = "recurrence_interval") private Integer recurrenceInterval;
    @Column(name = "duration_minutes") private Integer durationMinutes;
    @Column(name = "target_at") private Instant targetAt;
    @Column(name = "volume_percent") private Integer volumePercent;
    @Column(name = "memory_category", length = 24) private String memoryCategory;
    @Column(name = "result_reference") private UUID resultReference;
    @Column(name = "failure_code", length = 64) private String failureCode;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "executed_at") private Instant executedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected VoiceActionProposalEntity() { }

    public VoiceActionProposalEntity(String actorId, UUID deviceId, UUID conversationId, UUID sourceTurnId,
                                     VoiceActionDraft draft, Instant now, Instant expiresAt) {
        this(actorId, deviceId, com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID,
                conversationId, sourceTurnId, draft, now, expiresAt);
    }

    public VoiceActionProposalEntity(String actorId, UUID deviceId, UUID roleId, UUID conversationId, UUID sourceTurnId,
                                     VoiceActionDraft draft, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.conversationId = conversationId;
        this.sourceTurnId = sourceTurnId;
        this.actionType = draft.actionType();
        this.status = VoiceActionStatus.PENDING;
        this.confirmationRequired = draft.confirmationRequired();
        this.content = draft.content();
        this.title = draft.title();
        this.scheduledAt = draft.scheduledAt();
        this.zoneId = draft.zoneId();
        this.recurrenceType = draft.recurrenceType();
        this.recurrenceInterval = draft.recurrenceInterval();
        this.durationMinutes = draft.durationMinutes();
        this.targetAt = draft.targetAt();
        this.volumePercent = draft.volumePercent();
        this.memoryCategory = draft.memoryCategory();
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markExecuting(Instant now) { status = VoiceActionStatus.EXECUTING; updatedAt = now; }
    public void markExecuted(UUID reference, Instant now) { status = VoiceActionStatus.EXECUTED; resultReference = reference; executedAt = now; updatedAt = now; }
    public void markCancelled(Instant now) { status = VoiceActionStatus.CANCELLED; updatedAt = now; }
    public void markExpired(Instant now) { status = VoiceActionStatus.EXPIRED; updatedAt = now; }
    public void markFailed(String code, Instant now) { status = VoiceActionStatus.FAILED; failureCode = code; updatedAt = now; }
    public UUID getId() { return id; }
    public String getActorId() { return actorId; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getRoleId() { return roleId; }
    public UUID getConversationId() { return conversationId; }
    public UUID getSourceTurnId() { return sourceTurnId; }
    public VoiceActionType getActionType() { return actionType; }
    public VoiceActionStatus getStatus() { return status; }
    public boolean isConfirmationRequired() { return confirmationRequired; }
    public String getContent() { return content; }
    public String getTitle() { return title; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getZoneId() { return zoneId; }
    public String getRecurrenceType() { return recurrenceType; }
    public Integer getRecurrenceInterval() { return recurrenceInterval; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Instant getTargetAt() { return targetAt; }
    public Integer getVolumePercent() { return volumePercent; }
    public String getMemoryCategory() { return memoryCategory; }
    public UUID getResultReference() { return resultReference; }
    public String getFailureCode() { return failureCode; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
