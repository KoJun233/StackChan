package com.kj.stackchan.task;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonalTaskRepository extends JpaRepository<PersonalTaskEntity, UUID>,
        JpaSpecificationExecutor<PersonalTaskEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from PersonalTaskEntity task where task.id = :id")
    Optional<PersonalTaskEntity> findByIdForUpdate(@Param("id") UUID id);

    List<PersonalTaskEntity> findTop20ByDeviceIdAndRoleIdAndStatusOrderByDueAtAscCreatedAtDesc(
            UUID deviceId,
            UUID roleId,
            PersonalTaskStatus status
    );

    @Query("""
            select task from PersonalTaskEntity task
            where task.deviceId = :deviceId
              and task.roleId = :roleId
              and task.status = :status
              and (task.dueAt < :endExclusive or task.priority = :highPriority)
            order by
              case when task.dueAt < :endExclusive then 0 else 1 end,
              case when task.dueAt is null then 1 else 0 end,
              task.dueAt asc,
              task.createdAt desc,
              task.id desc
            """)
    List<PersonalTaskEntity> findDailyBriefCandidates(
            @Param("deviceId") UUID deviceId,
            @Param("roleId") UUID roleId,
            @Param("status") PersonalTaskStatus status,
            @Param("highPriority") PersonalTaskPriority highPriority,
            @Param("endExclusive") Instant endExclusive,
            Pageable pageable
    );

    List<PersonalTaskEntity> findTop10ByDeviceIdAndRoleIdAndStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            UUID deviceId,
            UUID roleId,
            PersonalTaskStatus status,
            String title
    );
}
