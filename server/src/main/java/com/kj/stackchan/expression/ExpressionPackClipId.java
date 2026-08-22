package com.kj.stackchan.expression;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ExpressionPackClipId implements Serializable {
    private UUID packId;
    private String clipName;

    public ExpressionPackClipId() {}

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ExpressionPackClipId value)) return false;
        return Objects.equals(packId, value.packId) && Objects.equals(clipName, value.clipName);
    }

    @Override
    public int hashCode() { return Objects.hash(packId, clipName); }
}
