package com.kj.stackchan.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private static final String DEFAULT_TITLE = "新对话";

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final Clock clock;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMessageRepository messageRepository,
            Clock clock
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.clock = clock;
    }

    @Transactional
    public ConversationSnapshot createConversation() {
        ConversationEntity conversation = conversationRepository.save(new ConversationEntity(DEFAULT_TITLE, clock.instant()));
        return toSnapshot(conversation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSnapshot> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDescIdDesc().stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageSnapshot> getMessages(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return messageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversationId).stream()
                .map(this::toSnapshot)
                .toList();
    }

    @Transactional
    public GenerationStart startGeneration(UUID conversationId, UUID clientMessageId, String content) {
        ConversationEntity conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
        ConversationMessageEntity duplicateUserMessage = messageRepository
                .findByConversationIdAndClientMessageId(conversationId, clientMessageId)
                .orElse(null);
        if (duplicateUserMessage != null) {
            ConversationMessageEntity assistantMessage = messageRepository
                    .findByInReplyToMessageId(duplicateUserMessage.getId())
                    .orElseThrow();
            return new GenerationStart(
                    conversationId,
                    duplicateUserMessage.getId(),
                    assistantMessage.getId(),
                    true,
                    assistantMessage.getGenerationStatus(),
                    assistantMessage.getContent()
            );
        }

        Instant now = clock.instant();
        ConversationMessageEntity userMessage = ConversationMessageEntity.user(conversationId, clientMessageId, content, now);
        ConversationMessageEntity assistantMessage = ConversationMessageEntity.streamingAssistant(
                conversationId,
                userMessage.getId(),
                now.plusMillis(1)
        );
        messageRepository.save(userMessage);
        messageRepository.save(assistantMessage);
        conversation.touch(now);
        return new GenerationStart(
                conversationId,
                userMessage.getId(),
                assistantMessage.getId(),
                false,
                GenerationStatus.STREAMING,
                assistantMessage.getContent()
        );
    }

    @Transactional
    public void completeGeneration(UUID assistantMessageId, String content) {
        ConversationMessageEntity assistantMessage = findAssistantMessage(assistantMessageId);
        finalizeGeneration(assistantMessage, GenerationStatus.COMPLETED, null, content);
    }

    @Transactional
    public void failGeneration(UUID assistantMessageId, String failureCode) {
        ConversationMessageEntity assistantMessage = findAssistantMessage(assistantMessageId);
        failGeneration(assistantMessage, failureCode, assistantMessage.getContent());
    }

    @Transactional
    public void failGeneration(UUID assistantMessageId, String failureCode, String content) {
        ConversationMessageEntity assistantMessage = findAssistantMessage(assistantMessageId);
        failGeneration(assistantMessage, failureCode, content);
    }

    @Transactional
    public void interruptGeneration(UUID assistantMessageId, String content) {
        ConversationMessageEntity assistantMessage = findAssistantMessage(assistantMessageId);
        finalizeGeneration(assistantMessage, GenerationStatus.INTERRUPTED, null, content);
    }

    @Transactional
    public int recoverStreamingGenerations() {
        List<ConversationMessageEntity> streamingAssistants = messageRepository
                .findAllByRoleAndGenerationStatus(MessageRole.ASSISTANT, GenerationStatus.STREAMING);
        Instant now = clock.instant();
        Set<UUID> conversationIds = new HashSet<>();
        int recoveredCount = 0;
        for (ConversationMessageEntity assistantMessage : streamingAssistants) {
            int updated = messageRepository.finalizeStreamingAssistant(
                    assistantMessage.getId(),
                    MessageRole.ASSISTANT,
                    GenerationStatus.STREAMING,
                    GenerationStatus.INTERRUPTED,
                    null,
                    assistantMessage.getContent(),
                    now
            );
            if (updated == 1) {
                recoveredCount++;
                conversationIds.add(assistantMessage.getConversationId());
            }
        }
        List<ConversationEntity> affectedConversations = conversationRepository.findAllById(conversationIds);
        Set<UUID> foundConversationIds = affectedConversations.stream()
                .map(ConversationEntity::getId)
                .collect(Collectors.toSet());
        if (!foundConversationIds.equals(conversationIds)) {
            throw new IllegalStateException("Recovered generation references a missing conversation");
        }
        affectedConversations.forEach(conversation -> conversation.touch(now));
        return recoveredCount;
    }

    private void failGeneration(ConversationMessageEntity assistantMessage, String failureCode, String content) {
        finalizeGeneration(assistantMessage, GenerationStatus.FAILED, failureCode, content);
    }

    private void finalizeGeneration(
            ConversationMessageEntity assistantMessage,
            GenerationStatus terminalStatus,
            String failureCode,
            String content
    ) {
        Instant now = clock.instant();
        int updated = messageRepository.finalizeStreamingAssistant(
                assistantMessage.getId(),
                MessageRole.ASSISTANT,
                GenerationStatus.STREAMING,
                terminalStatus,
                failureCode,
                content,
                now
        );
        if (updated == 1) {
            conversationRepository.findById(assistantMessage.getConversationId()).orElseThrow().touch(now);
        }
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageSnapshot> loadHistory(UUID conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        List<ConversationMessageSnapshot> completedMessages = getMessages(conversationId).stream()
                .filter(message -> message.generationStatus() == GenerationStatus.COMPLETED)
                .filter(message -> message.role() == MessageRole.USER || message.role() == MessageRole.ASSISTANT)
                .toList();
        int firstIncludedIndex = Math.max(0, completedMessages.size() - 20);
        return completedMessages.subList(firstIncludedIndex, completedMessages.size());
    }

    private ConversationMessageEntity findAssistantMessage(UUID assistantMessageId) {
        ConversationMessageEntity message = messageRepository.findById(assistantMessageId).orElseThrow();
        if (message.getRole() != MessageRole.ASSISTANT) {
            throw new IllegalArgumentException("Only assistant messages can be finalized");
        }
        return message;
    }

    private ConversationSnapshot toSnapshot(ConversationEntity conversation) {
        return new ConversationSnapshot(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt()
        );
    }

    private ConversationMessageSnapshot toSnapshot(ConversationMessageEntity message) {
        return new ConversationMessageSnapshot(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getGenerationStatus(),
                message.getCreatedAt(),
                message.getCompletedAt()
        );
    }
}
