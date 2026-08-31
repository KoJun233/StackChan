package com.kj.stackchan.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
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

    List<PersonalTaskEntity> findTop10ByDeviceIdAndRoleIdAndStatusAndTitleContainingIgnoreCaseOrderByCreatedAtDesc(
            UUID deviceId,
            UUID roleId,
            PersonalTaskStatus status,
            String title
    );
}
