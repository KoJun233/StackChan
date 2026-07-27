package com.kj.stackchan.interaction;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceInteractionSettingsRepository
        extends JpaRepository<DeviceInteractionSettingsEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select settings from DeviceInteractionSettingsEntity settings where settings.deviceId = :deviceId")
    Optional<DeviceInteractionSettingsEntity> findLockedByDeviceId(@Param("deviceId") UUID deviceId);
}
