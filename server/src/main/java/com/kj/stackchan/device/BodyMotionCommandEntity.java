package com.kj.stackchan.device;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "body_motion_commands")
public class BodyMotionCommandEntity {
    @Id
    private UUID id;
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;
    @Column(nullable = false, length = 16)
    private String motion;
    @Column(nullable = false)
    private boolean automatic;
    @Column(name = "event_key", length = 120)
    private String eventKey;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "failure_code", length = 32)
    private String failureCode;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BodyMotionCommandEntity() { }

    BodyMotionCommandEntity(UUID id, UUID deviceId, String motion, Instant now) {
        this(id, deviceId, motion, false, null, now);
    }

    BodyMotionCommandEntity(UUID id, UUID deviceId, String motion,
                            boolean automatic, String eventKey, Instant now) {
        this.id = id;
        this.deviceId = deviceId;
        this.motion = motion;
        this.automatic = automatic;
        this.eventKey = eventKey;
        this.status = "SENT";
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public String getMotion() { return motion; }
    public boolean isAutomatic() { return automatic; }
    public String getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    void transition(String nextStatus, String failure, Instant now) {
        this.status = nextStatus;
        this.failureCode = failure;
        this.updatedAt = now;
    }
}
