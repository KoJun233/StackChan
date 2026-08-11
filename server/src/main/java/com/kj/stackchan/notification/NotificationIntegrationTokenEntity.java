package com.kj.stackchan.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_integration_tokens")
public class NotificationIntegrationTokenEntity {

    @Id
    private UUID id;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationIntegrationTokenEntity() {
    }

    public NotificationIntegrationTokenEntity(UUID integrationId, String tokenHash, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.integrationId = integrationId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void markUsed(Instant now) { this.lastUsedAt = now; }
    public void revoke(Instant now) { if (this.revokedAt == null) this.revokedAt = now; }

    public UUID getId() { return id; }
    public UUID getIntegrationId() { return integrationId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
