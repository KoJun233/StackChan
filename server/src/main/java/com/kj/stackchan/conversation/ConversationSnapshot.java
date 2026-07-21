package com.kj.stackchan.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationSnapshot(UUID id, String title, Instant createdAt, Instant updatedAt) {
}
