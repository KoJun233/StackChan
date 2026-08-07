package com.kj.stackchan.firmwareupdate;

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

public interface FirmwareUpdateJobRepository extends JpaRepository<FirmwareUpdateJobEntity, UUID> {

    List<FirmwareUpdateJobEntity> findTop20ByDeviceIdOrderByCreatedAtDesc(UUID deviceId);

    List<FirmwareUpdateJobEntity> findTop10ByStatusOrderByCreatedAtAsc(FirmwareUpdateStatus status);

    List<FirmwareUpdateJobEntity> findAllByStatusAndUpdatedAtBefore(
            FirmwareUpdateStatus status, Instant updatedAt
    );

    Optional<FirmwareUpdateJobEntity> findByCommandId(String commandId);

    Optional<FirmwareUpdateJobEntity> findByIdAndDeviceId(UUID id, UUID deviceId);

    boolean existsByDeviceIdAndStatusIn(UUID deviceId, Collection<FirmwareUpdateStatus> statuses);

    long countByStatusIn(Collection<FirmwareUpdateStatus> statuses);

    List<FirmwareUpdateJobEntity> findTop10ByStatusInOrderByUpdatedAtDesc(
            Collection<FirmwareUpdateStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from FirmwareUpdateJobEntity job where job.id = :id")
    Optional<FirmwareUpdateJobEntity> findByIdForUpdate(@Param("id") UUID id);
}
