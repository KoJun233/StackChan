package com.kj.stackchan.device;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BodyMotionAutoSettingsRepository extends JpaRepository<BodyMotionAutoSettingsEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select setting from BodyMotionAutoSettingsEntity setting where setting.deviceId = :deviceId")
    Optional<BodyMotionAutoSettingsEntity> lockByDeviceId(@Param("deviceId") UUID deviceId);
}
