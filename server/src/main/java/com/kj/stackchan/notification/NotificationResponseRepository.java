package com.kj.stackchan.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationResponseRepository extends JpaRepository<NotificationResponseEntity, UUID> {
    Optional<NotificationResponseEntity> findFirstByNotificationIdOrderByCreatedAtDescIdDesc(UUID notificationId);
    List<NotificationResponseEntity> findAllByNotificationIdOrderByCreatedAtAscIdAsc(UUID notificationId);
}
