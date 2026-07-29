package com.kj.stackchan.agent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMcpConnectionRepository extends JpaRepository<AgentMcpConnectionEntity, UUID> {
    Optional<AgentMcpConnectionEntity> findByConnectionName(String connectionName);
    List<AgentMcpConnectionEntity> findAllByOrderByConnectionNameAsc();
}
