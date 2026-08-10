package com.kj.stackchan.wakeword;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WakeWordModelJobRepository extends JpaRepository<WakeWordModelJobEntity, UUID> {

    List<WakeWordModelJobEntity> findTop20ByDeviceIdOrderByCreatedAtDesc(UUID deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<WakeWordModelJobEntity> findTop10ByStatusOrderByCreatedAtAsc(WakeWordModelJobStatus status);

    List<WakeWordModelJobEntity> findAllByStatusAndUpdatedAtBefore(
            WakeWordModelJobStatus status,
            Instant updatedBefore
    );

    boolean existsByDeviceIdAndStatusIn(UUID deviceId, Collection<WakeWordModelJobStatus> statuses);

    long countByStatusIn(Collection<WakeWordModelJobStatus> statuses);

    Optional<WakeWordModelJobEntity> findByCommandId(String commandId);

    Optional<WakeWordModelJobEntity> findByIdAndDeviceId(UUID id, UUID deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from WakeWordModelJobEntity job where job.id = :id")
    Optional<WakeWordModelJobEntity> findByIdForUpdate(@Param("id") UUID id);
}
