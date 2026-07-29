package com.kj.stackchan.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentCapabilitySettingsRepository extends JpaRepository<AgentCapabilitySettingsEntity, UUID> {

    Optional<AgentCapabilitySettingsEntity> findByCapabilityTypeAndCapabilityId(
            AgentCapabilityType capabilityType,
            String capabilityId
    );

    List<AgentCapabilitySettingsEntity> findAllByCapabilityType(AgentCapabilityType capabilityType);
}
