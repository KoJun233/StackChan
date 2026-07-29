package com.kj.stackchan.memory;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
