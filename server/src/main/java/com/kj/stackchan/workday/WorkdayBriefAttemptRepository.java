package com.kj.stackchan.workday;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkdayBriefAttemptRepository extends JpaRepository<WorkdayBriefAttemptEntity, UUID> {

    Optional<WorkdayBriefAttemptEntity> findByDeviceIdAndWorkDate(UUID deviceId, LocalDate workDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from WorkdayBriefAttemptEntity attempt where attempt.deviceId = :deviceId and attempt.workDate = :workDate")
    Optional<WorkdayBriefAttemptEntity> findForUpdate(
            @Param("deviceId") UUID deviceId,
            @Param("workDate") LocalDate workDate
    );

    @Modifying
    @Query(value = """
            insert into workday_brief_attempts
              (id, device_id, work_date, status, claimed_at, updated_at)
            values (:id, :deviceId, :workDate, 'PENDING', :now, :now)
            on conflict (device_id, work_date) do nothing
            """, nativeQuery = true)
    int claim(
            @Param("id") UUID id,
            @Param("deviceId") UUID deviceId,
            @Param("workDate") LocalDate workDate,
            @Param("now") Instant now
    );

    long deleteByWorkDateBefore(LocalDate cutoff);
}
