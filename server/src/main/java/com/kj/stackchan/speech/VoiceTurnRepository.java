package com.kj.stackchan.speech;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceTurnRepository extends JpaRepository<VoiceTurnEntity, UUID> {

    List<VoiceTurnEntity> findByDeviceIdOrderByStartedAtDesc(UUID deviceId, Pageable pageable);

    long deleteByStartedAtBefore(Instant cutoff);

    boolean existsByDeviceIdAndStatusIn(UUID deviceId, Collection<VoiceTurnStatus> statuses);

    boolean existsByDeviceIdAndStatusInAndUpdatedAtAfter(
            UUID deviceId,
            Collection<VoiceTurnStatus> statuses,
            Instant cutoff
    );

    List<VoiceTurnEntity> findByDeviceIdAndStatusIn(UUID deviceId, Collection<VoiceTurnStatus> statuses);

    List<VoiceTurnEntity> findTop10ByStatusOrderByUpdatedAtDesc(VoiceTurnStatus status);
}
