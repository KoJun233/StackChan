package com.kj.stackchan.workday;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceWorkdayRuntimeRepository extends JpaRepository<DeviceWorkdayRuntimeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select runtime from DeviceWorkdayRuntimeEntity runtime where runtime.deviceId = :deviceId")
    Optional<DeviceWorkdayRuntimeEntity> findForUpdate(@Param("deviceId") UUID deviceId);
}
