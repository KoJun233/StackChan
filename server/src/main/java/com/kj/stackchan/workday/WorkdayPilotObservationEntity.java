package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workday_pilot_observations")
public class WorkdayPilotObservationEntity {

    @Id
    private UUID deviceId;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Column(name = "work_days_mask", nullable = false)
    private int workDaysMask;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkdayPilotObservationEntity() {
    }

    public WorkdayPilotObservationEntity(
            UUID deviceId,
            LocalDate startedOn,
            String zoneId,
            int workDaysMask,
            Instant now
    ) {
        this.deviceId = deviceId;
        reset(startedOn, zoneId, workDaysMask, now);
    }

    public void reset(LocalDate startedOn, String zoneId, int workDaysMask, Instant now) {
        this.startedOn = startedOn;
        this.endsOn = startedOn.plusDays(13);
        this.zoneId = zoneId;
        this.workDaysMask = workDaysMask;
        this.startedAt = now;
        this.updatedAt = now;
    }

    public UUID getDeviceId() { return deviceId; }
    public LocalDate getStartedOn() { return startedOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public String getZoneId() { return zoneId; }
    public int getWorkDaysMask() { return workDaysMask; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
