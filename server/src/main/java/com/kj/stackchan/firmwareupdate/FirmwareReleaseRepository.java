package com.kj.stackchan.firmwareupdate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FirmwareReleaseRepository extends JpaRepository<FirmwareReleaseEntity, UUID> {

    List<FirmwareReleaseEntity> findAllByOrderByCreatedAtDesc();

    Optional<FirmwareReleaseEntity> findByArtifactSha256(String artifactSha256);
}
