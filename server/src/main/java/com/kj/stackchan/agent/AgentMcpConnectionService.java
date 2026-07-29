package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import com.kj.stackchan.agent.AgentMcpConnectionEntity.AuthType;
import com.kj.stackchan.llm.SecretCipher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AgentMcpConnectionService {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private final AgentMcpConnectionRepository repository;
    private final SecretCipher secretCipher;
    private final McpTransportPolicyValidator transportPolicy;
    private final Clock clock;

    public AgentMcpConnectionService(
            AgentMcpConnectionRepository repository,
            SecretCipher secretCipher,
            McpTransportPolicyValidator transportPolicy,
            Clock clock
    ) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.transportPolicy = transportPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConnectionSnapshot> list() {
        return repository.findAllByOrderByConnectionNameAsc().stream().map(this::snapshot).toList();
    }

    @Transactional
    public ConnectionSnapshot create(ConnectionCommand command) {
        NormalizedCommand normalized = normalize(command, null);
        Instant now = clock.instant();
        AgentMcpConnectionEntity entity = new AgentMcpConnectionEntity(
                normalized.name(), normalized.url(), normalized.endpoint(), normalized.authType(),
                normalized.ciphertext(), normalized.iv(), now
        );
        try {
            return snapshot(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidAgentMcpConnectionException("MCP 连接名称已存在");
        }
    }

    @Transactional
    public ConnectionSnapshot update(UUID id, ConnectionCommand command) {
        AgentMcpConnectionEntity entity = repository.findById(id)
                .orElseThrow(AgentMcpConnectionNotFoundException::new);
        NormalizedCommand normalized = normalize(command, entity);
        entity.update(
                normalized.name(), normalized.url(), normalized.endpoint(), normalized.authType(),
                normalized.ciphertext(), normalized.iv(), clock.instant()
        );
        try {
            return snapshot(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException exception) {
            throw new InvalidAgentMcpConnectionException("MCP 连接名称已存在");
        }
    }

    @Transactional
    public void delete(UUID id) {
        AgentMcpConnectionEntity entity = repository.findById(id)
                .orElseThrow(AgentMcpConnectionNotFoundException::new);
        repository.delete(entity);
    }

    private NormalizedCommand normalize(ConnectionCommand command, AgentMcpConnectionEntity existing) {
        String name = required(command.connectionName(), "连接名称").toLowerCase(Locale.ROOT);
        if (name.length() > 64 || !NAME_PATTERN.matcher(name).matches()) {
            throw new InvalidAgentMcpConnectionException("连接名称只能包含小写字母、数字和单个连字符");
        }
        String url = required(command.url(), "URL");
        String endpoint = StringUtils.hasText(command.endpoint()) ? command.endpoint().trim() : "/mcp";
        if (url.length() > 2048 || endpoint.length() > 512) {
            throw new InvalidAgentMcpConnectionException("MCP URL 或 endpoint 过长");
        }
        try {
            transportPolicy.validateConnection(name, url, endpoint);
        } catch (IllegalStateException exception) {
            throw new InvalidAgentMcpConnectionException(exception.getMessage());
        }
        AuthType authType = command.authType() == null ? AuthType.NONE : command.authType();
        if (authType == AuthType.NONE) {
            return new NormalizedCommand(name, url, endpoint, authType, null, null);
        }
        String token = normalizeBearerToken(command.bearerToken());
        if (!StringUtils.hasText(token)) {
            if (existing == null || existing.getAuthType() != AuthType.BEARER) {
                throw new InvalidAgentMcpConnectionException("Bearer 认证必须提供 Token");
            }
            return new NormalizedCommand(
                    name, url, endpoint, authType,
                    existing.getBearerTokenCiphertext(), existing.getBearerTokenIv()
            );
        }
        if (token.length() > 4096) {
            throw new InvalidAgentMcpConnectionException("Bearer Token 过长");
        }
        SecretCipher.EncryptedSecret encrypted = secretCipher.encrypt(token);
        return new NormalizedCommand(name, url, endpoint, authType, encrypted.ciphertext(), encrypted.initializationVector());
    }

    private String normalizeBearerToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String normalized = token.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) {
            normalized = normalized.substring(7).trim();
        }
        return normalized;
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidAgentMcpConnectionException(label + "不能为空");
        }
        return value.trim();
    }

    private ConnectionSnapshot snapshot(AgentMcpConnectionEntity entity) {
        return new ConnectionSnapshot(
                entity.getId(), entity.getConnectionName(), entity.getUrl(), entity.getEndpoint(),
                entity.getAuthType(), entity.getAuthType() == AuthType.BEARER,
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    public record ConnectionCommand(
            String connectionName,
            String url,
            String endpoint,
            AuthType authType,
            String bearerToken
    ) { }

    public record ConnectionSnapshot(
            UUID id,
            String connectionName,
            String url,
            String endpoint,
            AuthType authType,
            boolean authenticationConfigured,
            Instant createdAt,
            Instant updatedAt
    ) { }

    private record NormalizedCommand(
            String name,
            String url,
            String endpoint,
            AuthType authType,
            String ciphertext,
            String iv
    ) { }
}
