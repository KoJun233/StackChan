package com.kj.stackchan.calendar;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "workday_calendar_events")
public class WorkdayCalendarEventEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "calendar_id", nullable = false)
    private UUID calendarId;

    @Column(name = "event_key", nullable = false, length = 64)
    private String eventKey;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(nullable = false)
    private boolean busy;

    @Column(name = "private_event", nullable = false)
    private boolean privateEvent;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 512)
    private String location;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected WorkdayCalendarEventEntity() {
    }

    public WorkdayCalendarEventEntity(
            UUID id,
            UUID deviceId,
            UUID calendarId,
            String eventKey,
            Instant startsAt,
            Instant endsAt,
            boolean allDay,
            boolean busy,
            boolean privateEvent,
            String title,
            String location,
            Instant fetchedAt,
            Instant expiresAt
    ) {
        this.id = id;
        this.deviceId = deviceId;
        this.calendarId = calendarId;
        this.eventKey = eventKey;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.allDay = allDay;
        this.busy = busy;
        this.privateEvent = privateEvent;
        this.title = title;
        this.location = location;
        this.fetchedAt = fetchedAt;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getCalendarId() { return calendarId; }
    public String getEventKey() { return eventKey; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isAllDay() { return allDay; }
    public boolean isBusy() { return busy; }
    public boolean isPrivateEvent() { return privateEvent; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public Instant getFetchedAt() { return fetchedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
