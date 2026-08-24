package com.kj.stackchan.calendar;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "icloud_calendars")
public class ICloudCalendarEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "calendar_key", nullable = false, length = 64)
    private String calendarKey;

    @Column(name = "calendar_href", nullable = false, length = 2048)
    private String calendarHref;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ICloudCalendarEntity() {
    }

    public ICloudCalendarEntity(
            UUID id,
            UUID deviceId,
            String calendarKey,
            String calendarHref,
            String displayName,
            Instant now
    ) {
        this.id = id;
        this.deviceId = deviceId;
        this.calendarKey = calendarKey;
        this.allowed = false;
        updateDiscovery(calendarHref, displayName, now);
        this.discoveredAt = now;
    }

    public void updateDiscovery(String calendarHref, String displayName, Instant now) {
        this.calendarHref = calendarHref;
        this.displayName = displayName;
        this.updatedAt = now;
    }

    public void setAllowed(boolean allowed, Instant now) {
        this.allowed = allowed;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public String getCalendarKey() { return calendarKey; }
    public String getCalendarHref() { return calendarHref; }
    public String getDisplayName() { return displayName; }
    public boolean isAllowed() { return allowed; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
