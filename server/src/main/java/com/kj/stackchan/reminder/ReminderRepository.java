package com.kj.stackchan.reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ReminderRepository extends JpaRepository<ReminderEntity, UUID>, JpaSpecificationExecutor<ReminderEntity> {

    List<ReminderEntity> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
            ReminderStatus status,
            Instant scheduledAt
    );

    List<ReminderEntity> findAllByStatusAndLastAttemptAtBefore(ReminderStatus status, Instant cutoff);

    Optional<ReminderEntity> findByCommandId(String commandId);

    Optional<ReminderEntity> findByIdAndDeviceId(UUID id, UUID deviceId);

    Optional<ReminderEntity> findFirstByStatusOrderByScheduledAtAscIdAsc(ReminderStatus status);

    Optional<ReminderEntity> findFirstByDeviceIdAndStatusOrderByScheduledAtAscIdAsc(
            UUID deviceId,
            ReminderStatus status
    );

    boolean existsByDeviceIdAndStatus(UUID deviceId, ReminderStatus status);

    boolean existsByDeviceIdAndSourceAndStatus(UUID deviceId, ReminderSource source, ReminderStatus status);

    long countByStatusIn(java.util.Collection<ReminderStatus> statuses);
}
