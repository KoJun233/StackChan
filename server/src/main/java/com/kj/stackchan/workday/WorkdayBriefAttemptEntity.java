package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workday_brief_attempts")
public class WorkdayBriefAttemptEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WorkdayBriefStatus status;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkdayBriefAttemptEntity() {
    }

    public WorkdayBriefAttemptEntity(UUID id, UUID deviceId, LocalDate workDate, Instant now) {
        this.id = id;
        this.deviceId = deviceId;
        this.workDate = workDate;
        this.status = WorkdayBriefStatus.PENDING;
        this.claimedAt = now;
        this.updatedAt = now;
    }

    public boolean complete(WorkdayBriefStatus result, Instant now) {
        if (status != WorkdayBriefStatus.PENDING || result == WorkdayBriefStatus.PENDING) {
            return false;
        }
        status = result;
        completedAt = now;
        updatedAt = now;
        return true;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public LocalDate getWorkDate() { return workDate; }
    public WorkdayBriefStatus getStatus() { return status; }
    public Instant getClaimedAt() { return claimedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
