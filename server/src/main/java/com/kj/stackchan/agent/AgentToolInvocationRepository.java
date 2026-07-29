package com.kj.stackchan.agent;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentToolInvocationRepository extends JpaRepository<AgentToolInvocationEntity, UUID> {

    List<AgentToolInvocationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
