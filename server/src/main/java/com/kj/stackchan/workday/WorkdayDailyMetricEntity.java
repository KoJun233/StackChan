package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workday_daily_metrics")
public class WorkdayDailyMetricEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "session_start_count", nullable = false)
    private int sessionStartCount;

    @Column(name = "session_end_count", nullable = false)
    private int sessionEndCount;

    @Column(name = "focus_seconds", nullable = false)
    private long focusSeconds;

    @Column(name = "brief_success_count", nullable = false)
    private int briefSuccessCount;

    @Column(name = "brief_partial_count", nullable = false)
    private int briefPartialCount;

    @Column(name = "brief_failed_count", nullable = false)
    private int briefFailedCount;

    @Column(name = "brief_cancelled_count", nullable = false)
    private int briefCancelledCount;

    @Column(name = "rest_started_count", nullable = false)
    private int restStartedCount;

    @Column(name = "rest_snoozed_count", nullable = false)
    private int restSnoozedCount;

    @Column(name = "rest_skipped_count", nullable = false)
    private int restSkippedCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkdayDailyMetricEntity() {
    }

    public WorkdayDailyMetricEntity(UUID id, UUID deviceId, LocalDate workDate, Instant now) {
        this.id = id;
        this.deviceId = deviceId;
        this.workDate = workDate;
        this.updatedAt = now;
    }

    public void sessionStarted(Instant now) { sessionStartCount++; updatedAt = now; }
    public void sessionEnded(Instant now) { sessionEndCount++; updatedAt = now; }
    public void addFocusSeconds(long seconds, Instant now) { focusSeconds += Math.max(0, seconds); updatedAt = now; }
    public void restStarted(Instant now) { restStartedCount++; updatedAt = now; }
    public void restSnoozed(Instant now) { restSnoozedCount++; updatedAt = now; }
    public void restSkipped(Instant now) { restSkippedCount++; updatedAt = now; }

    public void briefCompleted(WorkdayBriefStatus status, Instant now) {
        switch (status) {
            case SUCCESS -> briefSuccessCount++;
            case PARTIAL -> briefPartialCount++;
            case FAILED -> briefFailedCount++;
            case CANCELLED -> briefCancelledCount++;
            case PENDING -> throw new IllegalArgumentException("Pending brief is not a completed metric");
        }
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public LocalDate getWorkDate() { return workDate; }
    public int getSessionStartCount() { return sessionStartCount; }
    public int getSessionEndCount() { return sessionEndCount; }
    public long getFocusSeconds() { return focusSeconds; }
    public int getBriefSuccessCount() { return briefSuccessCount; }
    public int getBriefPartialCount() { return briefPartialCount; }
    public int getBriefFailedCount() { return briefFailedCount; }
    public int getBriefCancelledCount() { return briefCancelledCount; }
    public int getRestStartedCount() { return restStartedCount; }
    public int getRestSnoozedCount() { return restSnoozedCount; }
    public int getRestSkippedCount() { return restSkippedCount; }
    public Instant getUpdatedAt() { return updatedAt; }
}
