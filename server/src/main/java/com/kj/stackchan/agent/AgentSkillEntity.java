package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_skills")
public class AgentSkillEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(nullable = false, length = 1024)
    private String description;

    @Column(length = 64)
    private String version;

    @Column(name = "directory_name", nullable = false, unique = true, length = 64)
    private String directoryName;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "file_count", nullable = false)
    private int fileCount;

    @Column(name = "uncompressed_bytes", nullable = false)
    private long uncompressedBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentSkillEntity() {
    }

    public AgentSkillEntity(
            String name,
            String description,
            String version,
            String directoryName,
            String contentSha256,
            int fileCount,
            long uncompressedBytes,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.description = description;
        this.version = version;
        this.directoryName = directoryName;
        this.contentSha256 = contentSha256;
        this.enabled = false;
        this.fileCount = fileCount;
        this.uncompressedBytes = uncompressedBytes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public String getDirectoryName() { return directoryName; }
    public String getContentSha256() { return contentSha256; }
    public boolean isEnabled() { return enabled; }
    public int getFileCount() { return fileCount; }
    public long getUncompressedBytes() { return uncompressedBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
