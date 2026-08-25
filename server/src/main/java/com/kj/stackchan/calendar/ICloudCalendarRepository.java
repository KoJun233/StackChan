package com.kj.stackchan.calendar;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ICloudCalendarRepository extends JpaRepository<ICloudCalendarEntity, UUID> {

    List<ICloudCalendarEntity> findAllByDeviceIdOrderByDisplayNameAsc(UUID deviceId);

    List<ICloudCalendarEntity> findAllByDeviceIdAndAllowedTrueOrderByDisplayNameAsc(UUID deviceId);

    boolean existsByDeviceIdAndAllowedTrue(UUID deviceId);

    Optional<ICloudCalendarEntity> findByDeviceIdAndCalendarKey(UUID deviceId, String calendarKey);
}
