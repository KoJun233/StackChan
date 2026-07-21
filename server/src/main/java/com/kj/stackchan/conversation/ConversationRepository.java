package com.kj.stackchan.conversation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    List<ConversationEntity> findAllByOrderByUpdatedAtDescIdDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from ConversationEntity conversation where conversation.id = :conversationId")
    Optional<ConversationEntity> findByIdForUpdate(@Param("conversationId") UUID conversationId);
}
