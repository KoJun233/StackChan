package com.kj.stackchan.interaction;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProactiveTopicCooldownId implements Serializable {

    private UUID deviceId;
    private String topicKey;

    public ProactiveTopicCooldownId() {
    }

    public ProactiveTopicCooldownId(UUID deviceId, String topicKey) {
        this.deviceId = deviceId;
        this.topicKey = topicKey;
    }

    public UUID getDeviceId() { return deviceId; }
    public String getTopicKey() { return topicKey; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProactiveTopicCooldownId that)) return false;
        return Objects.equals(deviceId, that.deviceId) && Objects.equals(topicKey, that.topicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, topicKey);
    }
}
