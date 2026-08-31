package com.kj.stackchan.workday;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkdayPilotObservationRepository
        extends JpaRepository<WorkdayPilotObservationEntity, UUID> {

    List<WorkdayPilotObservationEntity>
            findAllByCompletionNotificationQueuedAtIsNullOrderByEndsOnAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select observation from WorkdayPilotObservationEntity observation "
            + "where observation.deviceId = :deviceId")
    Optional<WorkdayPilotObservationEntity> findForUpdate(@Param("deviceId") UUID deviceId);
}
