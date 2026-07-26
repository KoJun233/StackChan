package com.kj.stackchan.conversation;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceVoiceConversationRepository extends JpaRepository<DeviceVoiceConversationEntity, UUID> {

    Optional<DeviceVoiceConversationEntity> findByConversationId(UUID conversationId);
}
