package com.kj.stackchan.device;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface BodyMotionCommandRepository extends JpaRepository<BodyMotionCommandEntity, UUID> {
    Optional<BodyMotionCommandEntity> findByIdAndDeviceId(UUID id, UUID deviceId);
    boolean existsByIdAndDeviceId(UUID id, UUID deviceId);
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BodyMotionCommandEntity command
            set command.status = 'UNCONFIRMED', command.updatedAt = :now
            where command.id = :id and command.deviceId = :deviceId
              and command.status in ('SENT', 'ACCEPTED') and command.createdAt < :cutoff
            """)
    int markUnconfirmed(@Param("deviceId") UUID deviceId, @Param("id") UUID id,
                        @Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update BodyMotionCommandEntity command
            set command.status = 'DELIVERY_FAILED', command.updatedAt = :now
            where command.id = :id and command.deviceId = :deviceId and command.status = 'SENT'
            """)
    int markDeliveryFailed(@Param("deviceId") UUID deviceId, @Param("id") UUID id,
                           @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BodyMotionCommandEntity command
            set command.status = :status, command.updatedAt = :now
            where command.id = :id and command.deviceId = :deviceId
              and command.status in ('SENT', 'ACCEPTED')
            """)
    int markAcknowledged(@Param("deviceId") UUID deviceId, @Param("id") UUID id,
                         @Param("status") String status, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update BodyMotionCommandEntity command
            set command.status = :status, command.failureCode = :failure,
                command.updatedAt = :now
            where command.id = :id and command.deviceId = :deviceId
              and command.motion = :motion
              and command.status in ('SENT', 'ACCEPTED', 'UNCONFIRMED')
            """)
    int markFinal(@Param("deviceId") UUID deviceId, @Param("id") UUID id,
                  @Param("motion") String motion, @Param("status") String status,
                  @Param("failure") String failure, @Param("now") Instant now);
    boolean existsByDeviceIdAndEventKey(UUID deviceId, String eventKey);
    boolean existsByDeviceIdAndAutomaticTrueAndStatusIn(UUID deviceId, java.util.Collection<String> statuses);
    java.util.List<BodyMotionCommandEntity> findAllByDeviceIdAndAutomaticTrueAndStatusIn(
            UUID deviceId, java.util.Collection<String> statuses);
    java.util.List<BodyMotionCommandEntity> findTop100ByAutomaticTrueAndStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
            java.util.Collection<String> statuses, Instant cutoff);
    long countByDeviceIdAndAutomaticTrueAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
            UUID deviceId, String status, Instant start, Instant end);
    Optional<BodyMotionCommandEntity> findFirstByDeviceIdAndAutomaticTrueAndStatusOrderByUpdatedAtDesc(
            UUID deviceId, String status);
}
