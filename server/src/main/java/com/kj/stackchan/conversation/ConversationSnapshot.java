package com.kj.stackchan.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationSnapshot(UUID id, String title, UUID roleId, Instant createdAt, Instant updatedAt) {
    public ConversationSnapshot(UUID id, String title, Instant createdAt, Instant updatedAt) {
        this(id, title, com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID, createdAt, updatedAt);
    }
}
