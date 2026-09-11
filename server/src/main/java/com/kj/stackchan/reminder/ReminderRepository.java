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
import org.springframework.data.jpa.repository.Modifying;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID>, JpaSpecificationExecutor<ReminderEntity> {

    @Query("""
            select r.content as content, r.proactiveSourceName as sourceName,
                   r.proactiveSourceTitle as sourceTitle, r.proactiveSourceUrl as sourceUrl,
                   r.lastCompletedAt as completedAt
            from ReminderEntity r
            where r.deviceId = :deviceId and r.roleId = :roleId
              and r.source = com.kj.stackchan.reminder.ReminderSource.PROACTIVE
              and r.status = com.kj.stackchan.reminder.ReminderStatus.DELIVERED
              and r.lastCompletedAt >= :cutoff and r.lastCompletedAt <= :now
            order by r.lastCompletedAt desc, r.id desc
            """)
    List<DeliveredProactiveContext> findDeliveredProactiveContext(
            @Param("deviceId") UUID deviceId, @Param("roleId") UUID roleId,
            @Param("cutoff") Instant cutoff, @Param("now") Instant now,
            org.springframework.data.domain.Pageable pageable);

    interface DeliveredProactiveContext {
        String getContent();
        String getSourceName();
        String getSourceTitle();
        String getSourceUrl();
        Instant getCompletedAt();
    }

    List<ReminderEntity> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            ReminderStatus status,
            Instant scheduledAt
    );

    List<ReminderEntity> findTop10ByNotificationIntegrationIdAndDeviceIdAndRoleIdAndSourceAndStatusAndScheduledAtLessThanEqualAndDeliveryGroupIdIsNullOrderByCreatedAtAscIdAsc(
            UUID notificationIntegrationId,
            UUID deviceId,
            UUID roleId,
            ReminderSource source,
            ReminderStatus status,
            Instant scheduledAt
    );

    List<ReminderEntity> findAllByStatusAndLastAttemptAtBefore(ReminderStatus status, Instant cutoff);

    Optional<ReminderEntity> findByCommandId(String commandId);

    List<ReminderEntity> findAllByDeliveryGroupId(UUID deliveryGroupId);

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
    Optional<ReminderEntity> findFirstByDeviceIdAndRoleIdAndStatusAndDeliveryGroupIdIsNullOrderByScheduledAtAscIdAsc(
            UUID deviceId, UUID roleId, ReminderStatus status);

    boolean existsByDeviceIdAndStatus(UUID deviceId, ReminderStatus status);

    boolean existsByDeviceIdAndSourceAndStatus(UUID deviceId, ReminderSource source, ReminderStatus status);

    boolean existsByDeviceIdAndSourceAndProactiveSourceUrl(UUID deviceId, ReminderSource source, String proactiveSourceUrl);

    Optional<ReminderEntity> findFirstByDeviceIdAndSourceAndProactiveTopicKeyStartingWithOrderByCreatedAtDesc(
            UUID deviceId,
            ReminderSource source,
            String proactiveTopicKey
    );

    List<ReminderEntity> findAllByDeviceIdAndSourceAndProactiveTopicKeyStartingWith(
            UUID deviceId,
            ReminderSource source,
            String proactiveTopicKey
    );

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

    List<ReminderEntity> findTop20ByDeviceIdAndRoleIdAndSourceAndStatusAndLastCompletedAtAfterOrderByLastCompletedAtDescIdDesc(
            UUID deviceId,
            UUID roleId,
            ReminderSource source,
            ReminderStatus status,
            Instant lastCompletedAt
    );

    @Modifying
    @Query("update ReminderEntity reminder set reminder.status = com.kj.stackchan.reminder.ReminderStatus.CANCELLED, "
            + "reminder.updatedAt = :now where reminder.roleId = :roleId "
            + "and reminder.status = com.kj.stackchan.reminder.ReminderStatus.PENDING "
            + "and reminder.deliveryGroupId is null")
    int cancelFutureByRoleId(@Param("roleId") UUID roleId, @Param("now") Instant now);
}
