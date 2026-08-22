package com.kj.stackchan.expression;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "expression_packs")
public class ExpressionPackEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 240)
    private String description;

    @Column(name = "format_version", nullable = false)
    private int formatVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "pack_type", nullable = false, length = 24)
    private ExpressionPackType packType;

    @Column(name = "artifact_sha256", nullable = false, length = 64)
    private String artifactSha256;

    @Column(name = "artifact_size", nullable = false)
    private int artifactSize;

    @Column(nullable = false)
    private byte[] artifact;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExpressionPackEntity() {
    }

    ExpressionPackEntity(String name, String description, GeneratedExpressionPack generated, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.formatVersion = 1;
        this.packType = ExpressionPackType.STATIC_PNG;
        this.artifactSha256 = generated.sha256();
        this.artifact = generated.artifact().clone();
        this.artifactSize = artifact.length;
        this.createdAt = now;
    }

    ExpressionPackEntity(String name, String description, GeneratedLifecycleExpressionPack generated, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.formatVersion = 2;
        this.packType = ExpressionPackType.LIFECYCLE_EAF;
        this.artifactSha256 = generated.sha256();
        this.artifact = generated.artifact().clone();
        this.artifactSize = artifact.length;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getFormatVersion() { return formatVersion; }
    public ExpressionPackType getPackType() { return packType; }
    public String getArtifactSha256() { return artifactSha256; }
    public int getArtifactSize() { return artifactSize; }
    public byte[] getArtifact() { return artifact.clone(); }
    public Instant getCreatedAt() { return createdAt; }
}
