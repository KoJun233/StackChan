package com.kj.stackchan.memory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "long_term_memories")
public class LongTermMemoryEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 24)
    private MemoryScopeType scopeType;

    @Column(name = "device_id")
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MemoryCategory category;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MemorySource source;

    @Column(name = "source_detail", nullable = false, length = 500)
    private String sourceDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", nullable = false, length = 24)
    private MemoryConfirmationStatus confirmationStatus;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LongTermMemoryEntity() {
    }

    public LongTermMemoryEntity(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content,
            MemorySource source,
            String sourceDetail,
            MemoryConfirmationStatus confirmationStatus,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.scopeType = scopeType;
        this.deviceId = deviceId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.source = source;
        this.sourceDetail = sourceDetail;
        this.confirmationStatus = confirmationStatus;
        this.enabled = confirmationStatus == MemoryConfirmationStatus.CONFIRMED;
        this.confirmedAt = confirmationStatus == MemoryConfirmationStatus.CONFIRMED ? now : null;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content,
            String sourceDetail,
            Instant now
    ) {
        this.scopeType = scopeType;
        this.deviceId = deviceId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.sourceDetail = sourceDetail;
        this.updatedAt = now;
    }

    public void confirm(Instant now) {
        this.confirmationStatus = MemoryConfirmationStatus.CONFIRMED;
        this.enabled = true;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    public void reject(Instant now) {
        this.confirmationStatus = MemoryConfirmationStatus.REJECTED;
        this.enabled = false;
        this.confirmedAt = null;
        this.updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public MemoryScopeType getScopeType() {
        return scopeType;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public MemoryCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public MemorySource getSource() {
        return source;
    }

    public String getSourceDetail() {
        return sourceDetail;
    }

    public MemoryConfirmationStatus getConfirmationStatus() {
        return confirmationStatus;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
