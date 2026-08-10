package com.kj.stackchan.firmwareupdate;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "firmware_releases")
public class FirmwareReleaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "project_name", nullable = false, length = 32)
    private String projectName;

    @Column(name = "artifact_sha256", nullable = false, length = 64)
    private String artifactSha256;

    @Column(name = "artifact_size", nullable = false)
    private int artifactSize;

    @Column(nullable = false)
    private byte[] artifact;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FirmwareReleaseEntity() {
    }

    FirmwareReleaseEntity(ValidatedFirmwareArtifact artifact, Instant now) {
        this.id = UUID.randomUUID();
        this.version = artifact.version();
        this.projectName = artifact.projectName();
        this.artifactSha256 = artifact.sha256();
        this.artifact = artifact.bytes().clone();
        this.artifactSize = this.artifact.length;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getVersion() { return version; }
    public String getProjectName() { return projectName; }
    public String getArtifactSha256() { return artifactSha256; }
    public int getArtifactSize() { return artifactSize; }
    public byte[] getArtifact() { return artifact.clone(); }
    public Instant getCreatedAt() { return createdAt; }
}
