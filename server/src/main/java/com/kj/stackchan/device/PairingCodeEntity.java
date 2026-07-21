package com.kj.stackchan.device;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pairing_codes")
public class PairingCodeEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String value;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean consumed;

    @Column(name = "consumed_by_device_id")
    private UUID consumedByDeviceId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PairingCodeEntity() {
    }

    public PairingCodeEntity(String value, String createdBy, Instant expiresAt, Instant createdAt) {
        this.value = value;
        this.createdBy = createdBy;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isConsumed() {
        return consumed;
    }

    public UUID getConsumedByDeviceId() {
        return consumedByDeviceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markConsumed(UUID deviceId) {
        consumed = true;
        consumedByDeviceId = deviceId;
    }
}
