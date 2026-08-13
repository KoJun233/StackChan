package com.kj.stackchan.interaction;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import com.kj.stackchan.role.CompanionRoleEntity;

public class ProactiveTopicCooldownId implements Serializable {

    private UUID deviceId;
    private UUID roleId;
    private String topicKey;

    public ProactiveTopicCooldownId() {
    }

    public ProactiveTopicCooldownId(UUID deviceId, UUID roleId, String topicKey) {
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.topicKey = topicKey;
    }

    public ProactiveTopicCooldownId(UUID deviceId, String topicKey) {
        this(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, topicKey);
    }

    public UUID getDeviceId() { return deviceId; }
    public UUID getRoleId() { return roleId; }
    public String getTopicKey() { return topicKey; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProactiveTopicCooldownId that)) return false;
        return Objects.equals(deviceId, that.deviceId) && Objects.equals(roleId, that.roleId)
                && Objects.equals(topicKey, that.topicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, roleId, topicKey);
    }
}
