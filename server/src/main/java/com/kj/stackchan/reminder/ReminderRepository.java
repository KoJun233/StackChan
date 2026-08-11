package com.kj.stackchan.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID>, JpaSpecificationExecutor<ReminderEntity> {

    List<ReminderEntity> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            ReminderStatus status,
            Instant scheduledAt
    );

    List<ReminderEntity> findAllByStatusAndLastAttemptAtBefore(ReminderStatus status, Instant cutoff);

    Optional<ReminderEntity> findByCommandId(String commandId);

    Optional<ReminderEntity> findByIdAndDeviceId(UUID id, UUID deviceId);

    Optional<ReminderEntity> findByIdAndNotificationIntegrationId(UUID id, UUID notificationIntegrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reminder from ReminderEntity reminder "
            + "where reminder.id = :id and reminder.source = :source")
    Optional<ReminderEntity> findByIdAndSourceForUpdate(
            @Param("id") UUID id,
            @Param("source") ReminderSource source
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reminder from ReminderEntity reminder "
            + "where reminder.notificationIntegrationId = :integrationId")
    List<ReminderEntity> findAllByNotificationIntegrationIdForUpdate(
            @Param("integrationId") UUID integrationId
    );

    Optional<ReminderEntity> findByNotificationIntegrationIdAndIdempotencyKey(
            UUID notificationIntegrationId,
            String idempotencyKey
    );

    Optional<ReminderEntity> findFirstByStatusOrderByScheduledAtAscIdAsc(ReminderStatus status);

    Optional<ReminderEntity> findFirstByDeviceIdAndStatusOrderByScheduledAtAscIdAsc(
            UUID deviceId,
            ReminderStatus status
    );

    boolean existsByDeviceIdAndStatus(UUID deviceId, ReminderStatus status);

    boolean existsByDeviceIdAndSourceAndStatus(UUID deviceId, ReminderSource source, ReminderStatus status);

    long countByStatusIn(java.util.Collection<ReminderStatus> statuses);

    long countByNotificationIntegrationIdAndStatusIn(
            UUID notificationIntegrationId,
            java.util.Collection<ReminderStatus> statuses
    );

    long countBySourceAndStatusIn(ReminderSource source, java.util.Collection<ReminderStatus> statuses);

    long countBySourceAndStatusInAndUpdatedAtAfter(
            ReminderSource source,
            java.util.Collection<ReminderStatus> statuses,
            Instant updatedAt
    );

    List<ReminderEntity> findTop100BySourceAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
            ReminderSource source,
            ReminderStatus status,
            Instant expiresAt
    );

    List<ReminderEntity> findTop10BySourceAndStatusInOrderByUpdatedAtDesc(
            ReminderSource source,
            java.util.Collection<ReminderStatus> statuses
    );
}
