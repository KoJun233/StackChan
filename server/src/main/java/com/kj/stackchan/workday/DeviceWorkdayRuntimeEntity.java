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
@Table(name = "device_workday_runtime")
public class DeviceWorkdayRuntimeEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private WorkdayRuntimeState state;

    @Column(name = "work_date")
    private LocalDate workDate;

    @Column(name = "present", nullable = false)
    private boolean present;

    @Column(name = "focus_seconds", nullable = false)
    private long focusSeconds;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "state_changed_at", nullable = false)
    private Instant stateChangedAt;

    @Column(name = "focus_updated_at")
    private Instant focusUpdatedAt;

    @Column(name = "absence_started_at")
    private Instant absenceStartedAt;

    @Column(name = "rest_until")
    private Instant restUntil;

    @Column(name = "snoozed_until")
    private Instant snoozedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceWorkdayRuntimeEntity() {
    }

    public DeviceWorkdayRuntimeEntity(UUID deviceId, Instant now) {
        this.deviceId = deviceId;
        this.state = WorkdayRuntimeState.OFF;
        this.present = false;
        this.focusSeconds = 0;
        this.stateChangedAt = now;
        this.updatedAt = now;
    }

    public void start(LocalDate workDate, boolean present, Instant now) {
        this.workDate = workDate;
        this.present = present;
        this.focusSeconds = 0;
        this.startedAt = now;
        this.focusUpdatedAt = present ? now : null;
        this.absenceStartedAt = present ? null : now;
        this.restUntil = null;
        this.snoozedUntil = null;
        transition(present ? WorkdayRuntimeState.ACTIVE_PRESENT : WorkdayRuntimeState.STARTING, now);
    }

    public void stop(Instant now) {
        workDate = null;
        present = false;
        focusSeconds = 0;
        startedAt = null;
        focusUpdatedAt = null;
        absenceStartedAt = null;
        restUntil = null;
        snoozedUntil = null;
        transition(WorkdayRuntimeState.OFF, now);
    }

    public void transition(WorkdayRuntimeState next, Instant now) {
        state = next;
        stateChangedAt = now;
        updatedAt = now;
    }

    public void markPresent(boolean nextPresent, Instant now) {
        present = nextPresent;
        if (nextPresent) {
            absenceStartedAt = null;
            focusUpdatedAt = now;
        } else {
            absenceStartedAt = now;
            focusUpdatedAt = null;
        }
        updatedAt = now;
    }

    public void addFocusSeconds(long seconds, Instant now) {
        focusSeconds += Math.max(0, seconds);
        focusUpdatedAt = now;
        updatedAt = now;
    }

    public void beginRest(Instant restUntil, Instant now) {
        focusSeconds = 0;
        focusUpdatedAt = null;
        this.restUntil = restUntil;
        snoozedUntil = null;
        transition(WorkdayRuntimeState.RESTING, now);
    }

    public void finishRest(Instant now) {
        restUntil = null;
        focusUpdatedAt = present ? now : null;
        transition(present ? WorkdayRuntimeState.ACTIVE_PRESENT : WorkdayRuntimeState.ACTIVE_ABSENT, now);
    }

    public void snooze(Instant until, Instant now) {
        snoozedUntil = until;
        focusUpdatedAt = present ? now : null;
        transition(present ? WorkdayRuntimeState.ACTIVE_PRESENT : WorkdayRuntimeState.ACTIVE_ABSENT, now);
    }

    public void skipForDay(Instant now) {
        snoozedUntil = null;
        focusUpdatedAt = present ? now : null;
        transition(WorkdayRuntimeState.SKIPPED_FOR_DAY, now);
    }

    public UUID getDeviceId() { return deviceId; }
    public WorkdayRuntimeState getState() { return state; }
    public LocalDate getWorkDate() { return workDate; }
    public boolean isPresent() { return present; }
    public long getFocusSeconds() { return focusSeconds; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getStateChangedAt() { return stateChangedAt; }
    public Instant getFocusUpdatedAt() { return focusUpdatedAt; }
    public Instant getAbsenceStartedAt() { return absenceStartedAt; }
    public Instant getRestUntil() { return restUntil; }
    public Instant getSnoozedUntil() { return snoozedUntil; }
    public Instant getUpdatedAt() { return updatedAt; }
}
