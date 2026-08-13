package com.kj.stackchan.role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanionRoleRepository extends JpaRepository<CompanionRoleEntity, UUID> {
    List<CompanionRoleEntity> findAllByOrderByDefaultRoleDescArchivedAtAscUpdatedAtDescIdAsc();
    Optional<CompanionRoleEntity> findByDefaultRoleTrue();
    Optional<CompanionRoleEntity> findFirstByNameIgnoreCaseAndArchivedAtIsNull(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from CompanionRoleEntity role where role.id = :id")
    Optional<CompanionRoleEntity> findByIdForUpdate(@Param("id") UUID id);
}
