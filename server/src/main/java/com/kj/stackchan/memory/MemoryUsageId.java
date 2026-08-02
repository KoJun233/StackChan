package com.kj.stackchan.memory;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class MemoryUsageId implements Serializable {

    private UUID turnId;
    private UUID memoryId;

    public MemoryUsageId() {
    }

    public MemoryUsageId(UUID turnId, UUID memoryId) {
        this.turnId = turnId;
        this.memoryId = memoryId;
    }

    public UUID getTurnId() {
        return turnId;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemoryUsageId that)) {
            return false;
        }
        return Objects.equals(turnId, that.turnId) && Objects.equals(memoryId, that.memoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(turnId, memoryId);
    }
}
