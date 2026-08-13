package com.kj.stackchan.conversation;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceVoiceConversationRepository extends JpaRepository<DeviceVoiceConversationEntity, DeviceVoiceConversationId> {

    Optional<DeviceVoiceConversationEntity> findByConversationId(UUID conversationId);
    Optional<DeviceVoiceConversationEntity> findByDeviceIdAndRoleId(UUID deviceId, UUID roleId);
}
