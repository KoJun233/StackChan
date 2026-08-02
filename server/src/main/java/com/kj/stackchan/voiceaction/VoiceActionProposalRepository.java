package com.kj.stackchan.voiceaction;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VoiceActionProposalRepository extends JpaRepository<VoiceActionProposalEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select proposal from VoiceActionProposalEntity proposal where proposal.id = :id")
    Optional<VoiceActionProposalEntity> findByIdForUpdate(@Param("id") UUID id);
    Optional<VoiceActionProposalEntity> findFirstByActorIdAndDeviceIdAndConversationIdAndStatusOrderByCreatedAtDesc(
            String actorId, UUID deviceId, UUID conversationId, VoiceActionStatus status);
}
