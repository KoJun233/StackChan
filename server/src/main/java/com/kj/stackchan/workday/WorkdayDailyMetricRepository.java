package com.kj.stackchan.workday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkdayDailyMetricRepository extends JpaRepository<WorkdayDailyMetricEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select metric from WorkdayDailyMetricEntity metric where metric.deviceId = :deviceId and metric.workDate = :workDate")
    Optional<WorkdayDailyMetricEntity> findForUpdate(
            @Param("deviceId") UUID deviceId,
            @Param("workDate") LocalDate workDate
    );

    List<WorkdayDailyMetricEntity> findAllByDeviceIdAndWorkDateBetweenOrderByWorkDateAsc(
            UUID deviceId,
            LocalDate from,
            LocalDate to
    );

    long deleteByWorkDateBefore(LocalDate cutoff);
}
