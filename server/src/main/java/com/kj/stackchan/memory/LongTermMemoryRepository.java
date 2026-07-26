package com.kj.stackchan.memory;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LongTermMemoryRepository
        extends JpaRepository<LongTermMemoryEntity, UUID>, JpaSpecificationExecutor<LongTermMemoryEntity> {
}
