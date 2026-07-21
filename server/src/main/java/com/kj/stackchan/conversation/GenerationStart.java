package com.kj.stackchan.conversation;

import java.util.UUID;

public record GenerationStart(
        UUID conversationId,
        UUID userMessageId,
        UUID assistantMessageId,
        boolean duplicate,
        GenerationStatus assistantStatus,
        String assistantContent
) {
}
