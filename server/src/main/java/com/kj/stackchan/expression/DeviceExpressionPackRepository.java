package com.kj.stackchan.expression;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface DeviceExpressionPackRepository extends JpaRepository<DeviceExpressionPackEntity, UUID> {

    List<DeviceExpressionPackEntity> findTop10ByStatusOrderByUpdatedAtAsc(DeviceExpressionPackStatus status);

    Optional<DeviceExpressionPackEntity> findByCommandId(String commandId);

    boolean existsByPackIdAndEnabledTrue(UUID packId);

    List<DeviceExpressionPackEntity> findAllByStatusAndUpdatedAtBefore(
            DeviceExpressionPackStatus status,
            Instant updatedAt
    );

    long countByStatusIn(java.util.Collection<DeviceExpressionPackStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select mapping from DeviceExpressionPackEntity mapping where mapping.deviceId = :deviceId")
    Optional<DeviceExpressionPackEntity> findByDeviceIdForUpdate(@Param("deviceId") UUID deviceId);
}
