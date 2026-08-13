package com.kj.stackchan.memory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface LongTermMemoryRepository
        extends JpaRepository<LongTermMemoryEntity, UUID>, JpaSpecificationExecutor<LongTermMemoryEntity> {

    long countByConfirmationStatus(MemoryConfirmationStatus confirmationStatus);

    @Query("""
            select count(memory) from LongTermMemoryEntity memory
            where memory.confirmationStatus = :status
              and (memory.scopeType = com.kj.stackchan.memory.MemoryScopeType.GLOBAL
                   or memory.deviceId = :deviceId)
            """)
    long countVisibleByDeviceAndStatus(
            @Param("deviceId") UUID deviceId,
            @Param("status") MemoryConfirmationStatus status
    );

    @Query("select count(memory) from LongTermMemoryEntity memory where memory.roleId = :roleId "
            + "and memory.confirmationStatus = :status and (memory.scopeType = com.kj.stackchan.memory.MemoryScopeType.GLOBAL "
            + "or memory.deviceId = :deviceId)")
    long countVisibleByRoleAndDeviceAndStatus(@Param("roleId") UUID roleId,
            @Param("deviceId") UUID deviceId, @Param("status") MemoryConfirmationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select memory from LongTermMemoryEntity memory where memory.id = :id")
    Optional<LongTermMemoryEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select memory from LongTermMemoryEntity memory
            where memory.topicKey = :topicKey
              and memory.roleId = :roleId
              and memory.scopeType = :scopeType
              and ((:deviceId is null and memory.deviceId is null) or memory.deviceId = :deviceId)
              and memory.confirmationStatus = com.kj.stackchan.memory.MemoryConfirmationStatus.CONFIRMED
              and memory.enabled = true
              and memory.supersededByMemoryId is null
            order by memory.updatedAt desc, memory.id desc
            """)
    List<LongTermMemoryEntity> findActiveTopicMatches(
            @Param("roleId") UUID roleId,
            @Param("topicKey") String topicKey,
            @Param("scopeType") MemoryScopeType scopeType,
            @Param("deviceId") UUID deviceId
    );

    @Query("""
            select memory from LongTermMemoryEntity memory
            where memory.topicKey = :topicKey
              and memory.roleId = :roleId
              and memory.scopeType = :scopeType
              and ((:deviceId is null and memory.deviceId is null) or memory.deviceId = :deviceId)
              and memory.id <> :excludedId
              and memory.confirmationStatus <> com.kj.stackchan.memory.MemoryConfirmationStatus.REJECTED
            order by memory.updatedAt desc, memory.id desc
            """)
    List<LongTermMemoryEntity> findPossibleDuplicates(
            @Param("roleId") UUID roleId,
            @Param("topicKey") String topicKey,
            @Param("scopeType") MemoryScopeType scopeType,
            @Param("deviceId") UUID deviceId,
            @Param("excludedId") UUID excludedId
    );

    @Query(value = """
            select memory.*
            from long_term_memories memory
            where memory.confirmation_status = 'CONFIRMED'
              and memory.role_id = :roleId
              and memory.enabled = true
              and memory.superseded_by_memory_id is null
              and (
                (:deviceId is null and memory.scope_type = 'GLOBAL')
                or (:deviceId is not null and (
                  memory.scope_type = 'GLOBAL'
                  or (memory.scope_type = 'DEVICE' and memory.device_id = :deviceId)
                ))
              )
            order by
              case when trim(:queryText) = '' then 0.0 else greatest(
                similarity(memory.topic_key, :queryText),
                similarity(memory.title, :queryText),
                similarity(memory.content, :queryText)
              ) end desc,
              memory.importance desc,
              coalesce(memory.last_used_at, memory.confirmed_at, memory.updated_at) desc,
              memory.updated_at desc,
              memory.id desc
            limit :limit
            """, nativeQuery = true)
    List<LongTermMemoryEntity> searchContext(
            @Param("roleId") UUID roleId,
            @Param("deviceId") UUID deviceId,
            @Param("queryText") String queryText,
            @Param("limit") int limit
    );

    @Query("""
            select memory from LongTermMemoryEntity memory
            where memory.confirmationStatus = com.kj.stackchan.memory.MemoryConfirmationStatus.CONFIRMED
              and memory.roleId = :roleId
              and memory.enabled = true
              and memory.allowProactiveMention = true
              and memory.supersededByMemoryId is null
              and (memory.scopeType = com.kj.stackchan.memory.MemoryScopeType.GLOBAL
                   or (memory.scopeType = com.kj.stackchan.memory.MemoryScopeType.DEVICE
                       and memory.deviceId = :deviceId))
            order by memory.importance desc,
                     coalesce(memory.lastUsedAt, memory.confirmedAt, memory.updatedAt) desc,
                     memory.updatedAt desc,
                     memory.id desc
            """)
    List<LongTermMemoryEntity> findProactiveCandidates(
            @Param("roleId") UUID roleId,
            @Param("deviceId") UUID deviceId,
            Pageable pageable
    );
}
