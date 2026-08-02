package com.kj.stackchan.memory;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(MemoryUsageId.class)
@Table(name = "memory_usage_records")
public class MemoryUsageEntity {

    @Id
    @Column(name = "turn_id", nullable = false)
    private UUID turnId;

    @Id
    @Column(name = "memory_id", nullable = false)
    private UUID memoryId;

    @Column(name = "used_at", nullable = false)
    private Instant usedAt;

    protected MemoryUsageEntity() {
    }

    public MemoryUsageEntity(UUID turnId, UUID memoryId, Instant usedAt) {
        this.turnId = turnId;
        this.memoryId = memoryId;
        this.usedAt = usedAt;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
