package com.kj.stackchan.conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessageEntity, UUID> {

    Optional<ConversationMessageEntity> findByConversationIdAndClientMessageId(UUID conversationId, UUID clientMessageId);

    Optional<ConversationMessageEntity> findByInReplyToMessageId(UUID inReplyToMessageId);

    List<ConversationMessageEntity> findAllByRoleAndGenerationStatus(
            MessageRole role,
            GenerationStatus generationStatus
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ConversationMessageEntity message
            set message.content = :content,
                message.generationStatus = :terminalStatus,
                message.failureCode = :failureCode,
                message.completedAt = :completedAt
            where message.id = :assistantMessageId
              and message.role = :assistantRole
              and message.generationStatus = :streamingStatus
            """)
    int finalizeStreamingAssistant(
            @Param("assistantMessageId") UUID assistantMessageId,
            @Param("assistantRole") MessageRole assistantRole,
            @Param("streamingStatus") GenerationStatus streamingStatus,
            @Param("terminalStatus") GenerationStatus terminalStatus,
            @Param("failureCode") String failureCode,
            @Param("content") String content,
            @Param("completedAt") Instant completedAt
    );

    List<ConversationMessageEntity> findAllByConversationIdOrderByCreatedAtAscIdAsc(UUID conversationId);

    Optional<ConversationMessageEntity> findByIdAndConversationId(UUID id, UUID conversationId);

    long countByConversationIdAndGenerationStatus(UUID conversationId, GenerationStatus generationStatus);
}
