package com.kj.stackchan.workday;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceWorkdaySettingsRepository extends JpaRepository<DeviceWorkdaySettingsEntity, UUID> {

    List<DeviceWorkdaySettingsEntity> findAllByLatitudeIsNotNullAndLongitudeIsNotNull();
}
