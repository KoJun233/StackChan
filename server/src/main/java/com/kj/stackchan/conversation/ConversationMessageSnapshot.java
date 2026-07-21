package com.kj.stackchan.conversation;

import java.time.Instant;
import java.util.UUID;

public record ConversationMessageSnapshot(
        UUID id,
        MessageRole role,
        String content,
        GenerationStatus generationStatus,
        Instant createdAt,
        Instant completedAt
) {
}
