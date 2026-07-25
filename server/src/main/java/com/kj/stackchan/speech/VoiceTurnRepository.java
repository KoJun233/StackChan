package com.kj.stackchan.speech;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceTurnRepository extends JpaRepository<VoiceTurnEntity, UUID> {

    List<VoiceTurnEntity> findByDeviceIdOrderByStartedAtDesc(UUID deviceId, Pageable pageable);

    long deleteByStartedAtBefore(Instant cutoff);
}
