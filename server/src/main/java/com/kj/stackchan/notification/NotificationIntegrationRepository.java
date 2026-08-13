package com.kj.stackchan.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface NotificationIntegrationRepository extends JpaRepository<NotificationIntegrationEntity, UUID> {

    List<NotificationIntegrationEntity> findAllByOrderByCreatedAtDesc();

    long countByEnabledTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select integration from NotificationIntegrationEntity integration where integration.id = :id")
    Optional<NotificationIntegrationEntity> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("update NotificationIntegrationEntity integration set integration.enabled = false, "
            + "integration.updatedAt = :now where integration.roleId = :roleId and integration.enabled = true")
    int disableAllByRoleId(@Param("roleId") UUID roleId, @Param("now") java.time.Instant now);
}
