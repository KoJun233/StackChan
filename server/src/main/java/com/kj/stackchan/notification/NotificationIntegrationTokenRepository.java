package com.kj.stackchan.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationIntegrationTokenRepository
        extends JpaRepository<NotificationIntegrationTokenEntity, UUID> {

    Optional<NotificationIntegrationTokenEntity> findByTokenHash(String tokenHash);

    Optional<NotificationIntegrationTokenEntity> findByIdAndIntegrationId(UUID id, UUID integrationId);

    List<NotificationIntegrationTokenEntity> findAllByIntegrationIdOrderByCreatedAtDesc(UUID integrationId);
}
