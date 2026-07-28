package com.kj.stackchan.expression;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ExpressionPackStateId implements Serializable {
    private UUID packId;
    private String stateName;

    public ExpressionPackStateId() {
    }

    ExpressionPackStateId(UUID packId, String stateName) {
        this.packId = packId;
        this.stateName = stateName;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExpressionPackStateId id &&
                Objects.equals(packId, id.packId) && Objects.equals(stateName, id.stateName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packId, stateName);
    }
}
