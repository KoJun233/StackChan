package com.kj.stackchan.device;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PairingCodeRepository extends JpaRepository<PairingCodeEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pairingCode from PairingCodeEntity pairingCode where pairingCode.value = :value")
    Optional<PairingCodeEntity> findByValueForUpdate(@Param("value") String value);
}
