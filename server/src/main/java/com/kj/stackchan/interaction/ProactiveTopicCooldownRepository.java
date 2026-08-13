package com.kj.stackchan.interaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.kj.stackchan.role.CompanionRoleEntity;

public interface ProactiveTopicCooldownRepository
        extends JpaRepository<ProactiveTopicCooldownEntity, ProactiveTopicCooldownId> {

    List<ProactiveTopicCooldownEntity> findAllByDeviceIdAndRoleIdOrderByLastMentionedAtDescTopicKeyAsc(
            UUID deviceId, UUID roleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProactiveTopicCooldownEntity> findFirstByDeviceIdAndRoleIdOrderByLastMentionedAtDescTopicKeyAsc(
            UUID deviceId, UUID roleId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cooldown from ProactiveTopicCooldownEntity cooldown where cooldown.deviceId = :deviceId "
            + "and cooldown.roleId = :roleId and cooldown.topicKey = :topicKey")
    Optional<ProactiveTopicCooldownEntity> findLocked(
            @Param("deviceId") UUID deviceId,
            @Param("roleId") UUID roleId,
            @Param("topicKey") String topicKey
    );

    default Optional<ProactiveTopicCooldownEntity> findLocked(UUID deviceId, String topicKey) {
        return findLocked(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, topicKey);
    }
}
