package com.kj.stackchan.calendar;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ICloudCalendarConnectionRepository
        extends JpaRepository<ICloudCalendarConnectionEntity, UUID> {

    List<ICloudCalendarConnectionEntity> findAllByStatusIn(
            Collection<ICloudCalendarConnectionStatus> statuses
    );
}
