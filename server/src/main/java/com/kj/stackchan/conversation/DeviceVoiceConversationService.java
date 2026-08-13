package com.kj.stackchan.conversation;

import java.util.UUID;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleService;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class DeviceVoiceConversationService {

    private final DeviceVoiceConversationRepository repository;
    private final ConversationService conversationService;
    private final CompanionRoleService roleService;

    @Autowired
    public DeviceVoiceConversationService(
            DeviceVoiceConversationRepository repository,
            ConversationService conversationService,
            CompanionRoleService roleService
    ) {
        this.repository = repository;
        this.conversationService = conversationService;
        this.roleService = roleService;
    }

    public DeviceVoiceConversationService(DeviceVoiceConversationRepository repository,
                                          ConversationService conversationService) {
        this.repository = repository; this.conversationService = conversationService; this.roleService = null;
    }

    @Transactional
    public UUID getOrCreateConversationId(UUID deviceId) {
        UUID roleId = roleService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : roleService.getActive(deviceId).id();
        return getOrCreateConversationId(deviceId, roleId);
    }

    @Transactional
    public UUID getOrCreateConversationId(UUID deviceId, UUID roleId) {
        return repository.findByDeviceIdAndRoleId(deviceId, roleId)
                .map(DeviceVoiceConversationEntity::getConversationId)
                .orElseGet(() -> {
                    UUID conversationId = conversationService.createConversation(roleId).id();
                    repository.save(new DeviceVoiceConversationEntity(deviceId, roleId, conversationId));
                    return conversationId;
                });
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findDeviceIdByConversationId(UUID conversationId) {
        return repository.findByConversationId(conversationId)
                .map(DeviceVoiceConversationEntity::getDeviceId);
    }
}
