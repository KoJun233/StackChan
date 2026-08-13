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

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

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

    @Column(name = "topic_key", nullable = false, length = 120)
    private String topicKey;

    @Column(nullable = false)
    private int importance;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "source_turn_id")
    private UUID sourceTurnId;

    @Column(name = "replaces_memory_id")
    private UUID replacesMemoryId;

    @Column(name = "superseded_by_memory_id")
    private UUID supersededByMemoryId;

    @Column(name = "allow_proactive_mention", nullable = false)
    private boolean allowProactiveMention;

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

    public void assignRole(UUID roleId) { this.roleId = roleId; }

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
        this(scopeType, deviceId, category, title, content, source, sourceDetail,
                confirmationStatus, title, 3, null, null, false, now);
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
            String topicKey,
            int importance,
            UUID sourceTurnId,
            UUID replacesMemoryId,
            boolean allowProactiveMention,
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
        this.topicKey = topicKey;
        this.importance = importance;
        this.sourceTurnId = sourceTurnId;
        this.replacesMemoryId = replacesMemoryId;
        this.allowProactiveMention = allowProactiveMention;
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
        update(scopeType, deviceId, category, title, content, sourceDetail,
                topicKey, importance, allowProactiveMention, now);
    }

    public void update(
            MemoryScopeType scopeType,
            UUID deviceId,
            MemoryCategory category,
            String title,
            String content,
            String sourceDetail,
            String topicKey,
            int importance,
            boolean allowProactiveMention,
            Instant now
    ) {
        this.scopeType = scopeType;
        this.deviceId = deviceId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.sourceDetail = sourceDetail;
        this.topicKey = topicKey;
        this.importance = importance;
        this.allowProactiveMention = allowProactiveMention;
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

    public void setReplacementCandidate(UUID replacesMemoryId, Instant now) {
        this.replacesMemoryId = replacesMemoryId;
        this.updatedAt = now;
    }

    public void markSuperseded(UUID replacementId, Instant now) {
        this.supersededByMemoryId = replacementId;
        this.enabled = false;
        this.updatedAt = now;
    }

    public void markUsed(Instant now) {
        this.lastUsedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoleId() { return roleId; }

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

    public String getTopicKey() {
        return topicKey;
    }

    public int getImportance() {
        return importance;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public UUID getSourceTurnId() {
        return sourceTurnId;
    }

    public UUID getReplacesMemoryId() {
        return replacesMemoryId;
    }

    public UUID getSupersededByMemoryId() {
        return supersededByMemoryId;
    }

    public boolean isAllowProactiveMention() {
        return allowProactiveMention;
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
