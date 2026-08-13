package com.kj.stackchan.conversation;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_voice_conversations")
@IdClass(DeviceVoiceConversationId.class)
public class DeviceVoiceConversationEntity {

    @Id
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Id
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private UUID conversationId;

    protected DeviceVoiceConversationEntity() {
    }

    public DeviceVoiceConversationEntity(UUID deviceId, UUID roleId, UUID conversationId) {
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.conversationId = conversationId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getConversationId() {
        return conversationId;
    }
    public UUID getRoleId() { return roleId; }
}
