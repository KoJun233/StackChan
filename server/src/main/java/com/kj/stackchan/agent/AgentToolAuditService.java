package com.kj.stackchan.agent;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentToolAuditService {

    private final AgentToolInvocationRepository repository;
    private final Clock clock;

    public AgentToolAuditService(AgentToolInvocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            AgentInvocationContext context,
            String skillId,
            String toolName,
            AgentToolSource source,
            String sourceId,
            AgentToolOutcome outcome,
            long durationMs,
            int resultBytes,
            boolean truncated
    ) {
        repository.save(new AgentToolInvocationEntity(
                context,
                skillId,
                toolName,
                source,
                sourceId,
                outcome,
                durationMs,
                resultBytes,
                truncated,
                clock.instant()
        ));
    }

    @Transactional(readOnly = true)
    public List<InvocationSnapshot> list(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream()
                .map(entity -> new InvocationSnapshot(
                        entity.getId(),
                        entity.getTurnId(),
                        entity.getConversationId(),
                        entity.getDeviceId(),
                        entity.getChannel(),
                        entity.getSkillId(),
                        entity.getToolName(),
                        entity.getSourceType(),
                        entity.getSourceId(),
                        entity.getOutcome(),
                        entity.getDurationMs(),
                        entity.getResultBytes(),
                        entity.isTruncated(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    public record InvocationSnapshot(
            java.util.UUID id,
            java.util.UUID turnId,
            java.util.UUID conversationId,
            java.util.UUID deviceId,
            AgentChannel channel,
            String skillId,
            String toolName,
            AgentToolSource source,
            String sourceId,
            AgentToolOutcome outcome,
            long durationMs,
            int resultBytes,
            boolean truncated,
            java.time.Instant createdAt
    ) {
    }
}
