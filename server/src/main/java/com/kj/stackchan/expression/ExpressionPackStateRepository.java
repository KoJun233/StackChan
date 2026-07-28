package com.kj.stackchan.expression;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpressionPackStateRepository
        extends JpaRepository<ExpressionPackStateEntity, ExpressionPackStateId> {
    List<ExpressionPackStateEntity> findAllByPackIdOrderByStateName(UUID packId);
    Optional<ExpressionPackStateEntity> findByPackIdAndStateName(UUID packId, String stateName);
}
