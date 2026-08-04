package com.kj.stackchan.interaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProactiveTopicCooldownRepository
        extends JpaRepository<ProactiveTopicCooldownEntity, ProactiveTopicCooldownId> {

    List<ProactiveTopicCooldownEntity> findAllByDeviceIdOrderByLastMentionedAtDescTopicKeyAsc(UUID deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProactiveTopicCooldownEntity> findFirstByDeviceIdOrderByLastMentionedAtDescTopicKeyAsc(UUID deviceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cooldown from ProactiveTopicCooldownEntity cooldown where cooldown.deviceId = :deviceId and cooldown.topicKey = :topicKey")
    Optional<ProactiveTopicCooldownEntity> findLocked(
            @Param("deviceId") UUID deviceId,
            @Param("topicKey") String topicKey
    );
}
