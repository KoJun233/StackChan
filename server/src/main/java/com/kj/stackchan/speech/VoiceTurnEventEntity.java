package com.kj.stackchan.speech;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "voice_turn_events",
        uniqueConstraints = @UniqueConstraint(columnNames = {"turn_id", "source", "stage"})
)
public class VoiceTurnEventEntity {

    @Id
    private UUID id;

    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VoiceTurnStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoiceTurnStageSource source;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "elapsed_ms")
    private Integer elapsedMs;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "diagnostic_code", length = 80)
    private String diagnosticCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 40)
    private VoiceTurnFailureCode failureCode;

    protected VoiceTurnEventEntity() {
    }

    VoiceTurnEventEntity(
            UUID turnId,
            VoiceTurnStage stage,
            VoiceTurnStageSource source,
            Instant occurredAt,
            Integer elapsedMs,
            VoiceTurnFailureCode failureCode
    ) {
        this(turnId, stage, source, occurredAt, elapsedMs, failureCode, null, null);
    }

    VoiceTurnEventEntity(UUID turnId, VoiceTurnStage stage, VoiceTurnStageSource source,
                         Instant occurredAt, Integer elapsedMs, VoiceTurnFailureCode failureCode,
                         Integer durationMs, String diagnosticCode) {
        this.id = UUID.randomUUID();
        this.turnId = turnId;
        this.stage = stage;
        this.source = source;
        this.occurredAt = occurredAt;
        this.elapsedMs = elapsedMs;
        this.failureCode = failureCode;
        this.durationMs = durationMs;
        this.diagnosticCode = diagnosticCode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public VoiceTurnStage getStage() {
        return stage;
    }

    public VoiceTurnStageSource getSource() {
        return source;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Integer getElapsedMs() {
        return elapsedMs;
    }

    public VoiceTurnFailureCode getFailureCode() {
        return failureCode;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getDiagnosticCode() {
        return diagnosticCode;
    }
}
