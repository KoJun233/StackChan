package com.kj.stackchan.conversation;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation_messages")
public class ConversationMessageEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    @Column(name = "in_reply_to_message_id")
    private UUID inReplyToMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false)
    private GenerationStatus generationStatus;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ConversationMessageEntity() {
    }

    private ConversationMessageEntity(
            UUID conversationId,
            UUID clientMessageId,
            UUID inReplyToMessageId,
            MessageRole role,
            String content,
            GenerationStatus generationStatus,
            Instant createdAt,
            Instant completedAt
    ) {
        this.conversationId = conversationId;
        this.clientMessageId = clientMessageId;
        this.inReplyToMessageId = inReplyToMessageId;
        this.role = role;
        this.content = content;
        this.generationStatus = generationStatus;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    static ConversationMessageEntity user(UUID conversationId, UUID clientMessageId, String content, Instant createdAt) {
        return new ConversationMessageEntity(
                conversationId,
                clientMessageId,
                null,
                MessageRole.USER,
                content,
                GenerationStatus.COMPLETED,
                createdAt,
                createdAt
        );
    }

    static ConversationMessageEntity streamingAssistant(UUID conversationId, UUID inReplyToMessageId, Instant createdAt) {
        return new ConversationMessageEntity(
                conversationId,
                null,
                inReplyToMessageId,
                MessageRole.ASSISTANT,
                "",
                GenerationStatus.STREAMING,
                createdAt,
                null
        );
    }

    static ConversationMessageEntity streamingAssistant(UUID conversationId, Instant createdAt) {
        return streamingAssistant(conversationId, null, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public UUID getInReplyToMessageId() {
        return inReplyToMessageId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public GenerationStatus getGenerationStatus() {
        return generationStatus;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    void complete(String content, Instant completedAt) {
        this.content = content;
        this.generationStatus = GenerationStatus.COMPLETED;
        this.failureCode = null;
        this.completedAt = completedAt;
    }

    void fail(String failureCode, String content, Instant completedAt) {
        this.generationStatus = GenerationStatus.FAILED;
        this.failureCode = failureCode;
        this.content = content;
        this.completedAt = completedAt;
    }

    void interrupt(String content, Instant completedAt) {
        this.generationStatus = GenerationStatus.INTERRUPTED;
        this.failureCode = null;
        this.content = content;
        this.completedAt = completedAt;
    }
}
