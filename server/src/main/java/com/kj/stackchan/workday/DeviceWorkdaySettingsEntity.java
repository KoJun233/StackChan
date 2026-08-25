package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_workday_settings")
public class DeviceWorkdaySettingsEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "work_days_mask", nullable = false)
    private int workDaysMask;

    @Column(name = "work_start", nullable = false)
    private LocalTime workStart;

    @Column(name = "work_end", nullable = false)
    private LocalTime workEnd;

    @Column(name = "focus_minutes", nullable = false)
    private int focusMinutes;

    @Column(name = "rest_minutes", nullable = false)
    private int restMinutes;

    @Column(name = "absence_suspend_minutes", nullable = false)
    private int absenceSuspendMinutes;

    @Column(name = "rearrival_minutes", nullable = false)
    private int rearrivalMinutes;

    @Column(name = "location_name", nullable = false, length = 120)
    private String locationName;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceWorkdaySettingsEntity() {
    }

    public DeviceWorkdaySettingsEntity(UUID deviceId, Instant now) {
        this.deviceId = deviceId;
        this.enabled = false;
        this.workDaysMask = 31;
        this.workStart = LocalTime.of(9, 0);
        this.workEnd = LocalTime.of(18, 0);
        this.focusMinutes = 50;
        this.restMinutes = 10;
        this.absenceSuspendMinutes = 10;
        this.rearrivalMinutes = 45;
        this.locationName = "";
        this.zoneId = "Asia/Shanghai";
        this.updatedAt = now;
    }

    public void update(
            boolean enabled,
            int workDaysMask,
            LocalTime workStart,
            LocalTime workEnd,
            int focusMinutes,
            int restMinutes,
            int absenceSuspendMinutes,
            int rearrivalMinutes,
            String locationName,
            Double latitude,
            Double longitude,
            String zoneId,
            Instant now
    ) {
        this.enabled = enabled;
        this.workDaysMask = workDaysMask;
        this.workStart = workStart;
        this.workEnd = workEnd;
        this.focusMinutes = focusMinutes;
        this.restMinutes = restMinutes;
        this.absenceSuspendMinutes = absenceSuspendMinutes;
        this.rearrivalMinutes = rearrivalMinutes;
        this.locationName = locationName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.zoneId = zoneId;
        this.updatedAt = now;
    }

    public UUID getDeviceId() { return deviceId; }
    public boolean isEnabled() { return enabled; }
    public int getWorkDaysMask() { return workDaysMask; }
    public LocalTime getWorkStart() { return workStart; }
    public LocalTime getWorkEnd() { return workEnd; }
    public int getFocusMinutes() { return focusMinutes; }
    public int getRestMinutes() { return restMinutes; }
    public int getAbsenceSuspendMinutes() { return absenceSuspendMinutes; }
    public int getRearrivalMinutes() { return rearrivalMinutes; }
    public String getLocationName() { return locationName; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getZoneId() { return zoneId; }
    public Instant getUpdatedAt() { return updatedAt; }
}
