package com.kj.stackchan.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_responses")
public class NotificationResponseEntity {
    @Id
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "notification_integration_id", nullable = false)
    private UUID notificationIntegrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private NotificationResponseAction action;

    @Column(name = "snooze_minutes")
    private Integer snoozeMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationResponseEntity() {
    }

    public NotificationResponseEntity(
            UUID notificationId,
            UUID notificationIntegrationId,
            NotificationResponseAction action,
            Integer snoozeMinutes,
            Instant createdAt
    ) {
        this.id = UUID.randomUUID();
        this.notificationId = notificationId;
        this.notificationIntegrationId = notificationIntegrationId;
        this.action = action;
        this.snoozeMinutes = snoozeMinutes;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getNotificationId() { return notificationId; }
    public UUID getNotificationIntegrationId() { return notificationIntegrationId; }
    public NotificationResponseAction getAction() { return action; }
    public Integer getSnoozeMinutes() { return snoozeMinutes; }
    public Instant getCreatedAt() { return createdAt; }
}
