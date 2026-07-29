package com.kj.stackchan.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSkillRepository extends JpaRepository<AgentSkillEntity, UUID> {
    Optional<AgentSkillEntity> findByName(String name);
    List<AgentSkillEntity> findAllByOrderByCreatedAtDesc();
    List<AgentSkillEntity> findAllByEnabledTrueOrderByNameAsc();
}
