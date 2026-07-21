package com.kj.stackchan.conversation;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceVoiceConversationRepository extends JpaRepository<DeviceVoiceConversationEntity, UUID> {
}
