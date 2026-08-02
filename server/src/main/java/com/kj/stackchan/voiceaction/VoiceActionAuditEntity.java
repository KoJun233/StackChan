package com.kj.stackchan.voiceaction;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "voice_action_audits")
public class VoiceActionAuditEntity {
    @Id private UUID id;
    @Column(name = "proposal_id", nullable = false) private UUID proposalId;
    @Column(name = "actor_id", nullable = false, length = 64) private String actorId;
    @Column(name = "device_id") private UUID deviceId;
    @Column(name = "conversation_id") private UUID conversationId;
    @Column(name = "turn_id", nullable = false) private UUID turnId;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 40) private VoiceActionType actionType;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 24) private VoiceActionAuditEvent eventType;
    @Column(name = "failure_code", length = 64) private String failureCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected VoiceActionAuditEntity() { }
    public VoiceActionAuditEntity(VoiceActionProposalEntity proposal, VoiceActionAuditEvent event, String failureCode, Instant now) {
        this.id = UUID.randomUUID(); this.proposalId = proposal.getId(); this.actorId = proposal.getActorId();
        this.deviceId = proposal.getDeviceId(); this.conversationId = proposal.getConversationId();
        this.turnId = proposal.getSourceTurnId(); this.actionType = proposal.getActionType(); this.eventType = event;
        this.failureCode = failureCode; this.createdAt = now;
    }
}
