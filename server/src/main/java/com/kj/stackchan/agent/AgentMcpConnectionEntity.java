package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_mcp_connections")
public class AgentMcpConnectionEntity {

    @Id
    private UUID id;

    @Column(name = "connection_name", nullable = false, unique = true, length = 64)
    private String connectionName;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 512)
    private String endpoint;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 16)
    private AuthType authType;

    @Column(name = "bearer_token_ciphertext")
    private String bearerTokenCiphertext;

    @Column(name = "bearer_token_iv", length = 64)
    private String bearerTokenIv;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentMcpConnectionEntity() {
    }

    public AgentMcpConnectionEntity(
            String connectionName,
            String url,
            String endpoint,
            AuthType authType,
            String bearerTokenCiphertext,
            String bearerTokenIv,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        update(connectionName, url, endpoint, authType, bearerTokenCiphertext, bearerTokenIv, now);
        this.createdAt = now;
    }

    public void update(
            String connectionName,
            String url,
            String endpoint,
            AuthType authType,
            String bearerTokenCiphertext,
            String bearerTokenIv,
            Instant now
    ) {
        this.connectionName = connectionName;
        this.url = url;
        this.endpoint = endpoint;
        this.authType = authType;
        this.bearerTokenCiphertext = bearerTokenCiphertext;
        this.bearerTokenIv = bearerTokenIv;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getConnectionName() { return connectionName; }
    public String getUrl() { return url; }
    public String getEndpoint() { return endpoint; }
    public AuthType getAuthType() { return authType; }
    public String getBearerTokenCiphertext() { return bearerTokenCiphertext; }
    public String getBearerTokenIv() { return bearerTokenIv; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public enum AuthType {
        NONE,
        BEARER
    }
}
