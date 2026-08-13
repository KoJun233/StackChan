package com.kj.stackchan.conversation;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class DeviceVoiceConversationId implements Serializable {
    private UUID deviceId;
    private UUID roleId;
    public DeviceVoiceConversationId() {}
    public DeviceVoiceConversationId(UUID deviceId, UUID roleId) { this.deviceId = deviceId; this.roleId = roleId; }
    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof DeviceVoiceConversationId other)) return false;
        return Objects.equals(deviceId, other.deviceId) && Objects.equals(roleId, other.roleId);
    }
    @Override public int hashCode() { return Objects.hash(deviceId, roleId); }
}
