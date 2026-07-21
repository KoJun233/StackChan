package com.kj.stackchan.conversation;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceVoiceConversationService {

    private final DeviceVoiceConversationRepository repository;
    private final ConversationService conversationService;

    public DeviceVoiceConversationService(
            DeviceVoiceConversationRepository repository,
            ConversationService conversationService
    ) {
        this.repository = repository;
        this.conversationService = conversationService;
    }

    @Transactional
    public UUID getOrCreateConversationId(UUID deviceId) {
        return repository.findById(deviceId)
                .map(DeviceVoiceConversationEntity::getConversationId)
                .orElseGet(() -> {
                    UUID conversationId = conversationService.createConversation().id();
                    repository.save(new DeviceVoiceConversationEntity(deviceId, conversationId));
                    return conversationId;
                });
    }
}
