package com.kj.stackchan.expression;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpressionPackRepository extends JpaRepository<ExpressionPackEntity, UUID> {
    List<ExpressionPackEntity> findAllByOrderByCreatedAtDesc();
}
