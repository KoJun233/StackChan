package com.kj.stackchan.task;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "personal_tasks")
public class PersonalTaskEntity {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PersonalTaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PersonalTaskStatus status;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId;

    @Column(name = "reminder_id")
    private UUID reminderId;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonalTaskEntity() {
    }

    public PersonalTaskEntity(
            UUID deviceId,
            UUID roleId,
            String title,
            String notes,
            PersonalTaskPriority priority,
            Instant dueAt,
            String zoneId,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.title = title;
        this.notes = notes;
        this.priority = priority;
        this.status = PersonalTaskStatus.OPEN;
        this.dueAt = dueAt;
        this.zoneId = zoneId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String title,
            String notes,
            PersonalTaskPriority priority,
            Instant dueAt,
            String zoneId,
            Instant now
    ) {
        this.title = title;
        this.notes = notes;
        this.priority = priority;
        this.dueAt = dueAt;
        this.zoneId = zoneId;
        this.updatedAt = now;
    }

    public void linkReminder(UUID reminderId, Instant now) {
        this.reminderId = reminderId;
        this.updatedAt = now;
    }

    public void clearReminder(Instant now) {
        this.reminderId = null;
        this.updatedAt = now;
    }

    public void complete(Instant now) {
        this.status = PersonalTaskStatus.COMPLETED;
        this.completedAt = now;
        this.updatedAt = now;
    }

    public void reopen(Instant now) {
        this.status = PersonalTaskStatus.OPEN;
        this.completedAt = null;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDeviceId() { return deviceId; }
    public UUID getRoleId() { return roleId; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public PersonalTaskPriority getPriority() { return priority; }
    public PersonalTaskStatus getStatus() { return status; }
    public Instant getDueAt() { return dueAt; }
    public String getZoneId() { return zoneId; }
    public UUID getReminderId() { return reminderId; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
