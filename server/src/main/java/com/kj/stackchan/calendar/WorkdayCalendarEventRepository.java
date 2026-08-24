package com.kj.stackchan.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkdayCalendarEventRepository extends JpaRepository<WorkdayCalendarEventEntity, UUID> {

    void deleteAllByDeviceId(UUID deviceId);

    void deleteByExpiresAtBefore(Instant cutoff);

    long countByDeviceIdAndExpiresAtAfter(UUID deviceId, Instant now);

    Optional<WorkdayCalendarEventEntity> findFirstByDeviceIdAndExpiresAtAfterOrderByExpiresAtAsc(
            UUID deviceId,
            Instant now
    );

    List<WorkdayCalendarEventEntity> findAllByDeviceIdAndExpiresAtAfterAndEndsAtAfterAndStartsAtBeforeOrderByStartsAtAsc(
            UUID deviceId,
            Instant now,
            Instant from,
            Instant to
    );
}
