package com.kj.stackchan.speech;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceTurnEventRepository extends JpaRepository<VoiceTurnEventEntity, UUID> {

    boolean existsByTurnIdAndSourceAndStage(
            UUID turnId,
            VoiceTurnStageSource source,
            VoiceTurnStage stage
    );

    List<VoiceTurnEventEntity> findByTurnIdInOrderByOccurredAtAscIdAsc(Collection<UUID> turnIds);

    Optional<VoiceTurnEventEntity> findByTurnIdAndSourceAndStage(
            UUID turnId, VoiceTurnStageSource source, VoiceTurnStage stage);
}
