package com.kj.stackchan.device;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<DeviceEntity, UUID> {

    Optional<DeviceEntity> findByHardwareId(String hardwareId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select device from DeviceEntity device where device.hardwareId = :hardwareId")
    Optional<DeviceEntity> findByHardwareIdForUpdate(@Param("hardwareId") String hardwareId);
}
