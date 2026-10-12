package com.kj.stackchan.speech;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface VoiceTurnRepository extends JpaRepository<VoiceTurnEntity, UUID> {

    List<VoiceTurnEntity> findByDeviceIdOrderByStartedAtDesc(UUID deviceId, Pageable pageable);

    List<VoiceTurnEntity> findByDeviceIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
            UUID deviceId, Instant from, Instant to);

    long deleteByStartedAtBefore(Instant cutoff);

    boolean existsByDeviceIdAndStatusIn(UUID deviceId, Collection<VoiceTurnStatus> statuses);

    boolean existsByDeviceIdAndStatusInAndUpdatedAtAfter(
            UUID deviceId,
            Collection<VoiceTurnStatus> statuses,
            Instant cutoff
    );

    List<VoiceTurnEntity> findByDeviceIdAndStatusIn(UUID deviceId, Collection<VoiceTurnStatus> statuses);

    List<VoiceTurnEntity> findTop10ByStatusOrderByUpdatedAtDesc(VoiceTurnStatus status);

    @Query(value = "select exists(select 1 from voice_turn_privacy_tombstones where turn_id = :id and expires_at > :now)", nativeQuery = true)
    boolean isPrivacyErased(@Param("id") UUID id, @Param("now") Instant now);

    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(cast(:id as text), 0)) as text)", nativeQuery = true)
    String lockDiagnosticTurn(@Param("id") UUID id);

    @Modifying
    @Query(value = "delete from voice_turn_privacy_tombstones where expires_at <= :now", nativeQuery = true)
    int deleteExpiredPrivacyTombstones(@Param("now") Instant now);
}
