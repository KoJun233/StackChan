package com.kj.stackchan.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.agent.AgentMcpConnectionEntity.AuthType;
import com.kj.stackchan.llm.SecretCipher;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ManagedMcpClientRegistry {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final AgentMcpConnectionRepository repository;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;
    private final Map<UUID, Holder> holders = new HashMap<>();

    public ManagedMcpClientRegistry(
            AgentMcpConnectionRepository repository,
            SecretCipher secretCipher,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.secretCipher = secretCipher;
        this.objectMapper = objectMapper;
    }

    public synchronized List<ManagedConnection> connections(boolean refresh) {
        List<AgentMcpConnectionEntity> entities = repository.findAllByOrderByConnectionNameAsc();
        Set<UUID> currentIds = new HashSet<>();
        List<ManagedConnection> result = new ArrayList<>();
        for (AgentMcpConnectionEntity entity : entities) {
            currentIds.add(entity.getId());
            String fingerprint = entity.getUpdatedAt().toString();
            Holder holder = holders.get(entity.getId());
            if (holder == null || !holder.fingerprint().equals(fingerprint) || (refresh && holder.client() == null)) {
                close(holder);
                holder = create(entity, fingerprint);
                holders.put(entity.getId(), holder);
            }
            result.add(new ManagedConnection(
                    entity.getId(), entity.getConnectionName(), entity.getUrl(), entity.getEndpoint(),
                    entity.getAuthType(), entity.getAuthType() == AuthType.BEARER,
                    holder.client(), holder.failureCode()
            ));
        }
        holders.keySet().removeIf(id -> {
            if (currentIds.contains(id)) {
                return false;
            }
            close(holders.get(id));
            return true;
        });
        return List.copyOf(result);
    }

    public synchronized void invalidate() {
        holders.values().forEach(this::close);
        holders.clear();
    }

    private Holder create(AgentMcpConnectionEntity entity, String fingerprint) {
        McpSyncClient client = null;
        try {
            WebClient.Builder webClient = WebClient.builder().baseUrl(entity.getUrl());
            if (entity.getAuthType() == AuthType.BEARER) {
                String token = secretCipher.decrypt(new SecretCipher.EncryptedSecret(
                        entity.getBearerTokenCiphertext(), entity.getBearerTokenIv()
                ));
                webClient.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            WebClientStreamableHttpTransport transport = WebClientStreamableHttpTransport.builder(webClient)
                    .endpoint(entity.getEndpoint())
                    .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                    .build();
            client = McpClient.sync(transport)
                    .requestTimeout(REQUEST_TIMEOUT)
                    .initializationTimeout(REQUEST_TIMEOUT)
                    .clientInfo(new McpSchema.Implementation(
                            "stackchan-" + entity.getConnectionName(),
                            entity.getConnectionName(),
                            "1.0"
                    ))
                    .build();
            client.initialize();
            return new Holder(fingerprint, client, null);
        } catch (RuntimeException exception) {
            if (client != null) {
                close(new Holder(fingerprint, client, null));
            }
            return new Holder(fingerprint, null, "connection_failed");
        }
    }

    private void close(Holder holder) {
        if (holder != null && holder.client() != null) {
            try {
                holder.client().closeGracefully();
            } catch (RuntimeException ignored) {
                // Connection cleanup must not prevent configuration changes or shutdown.
            }
        }
    }

    @PreDestroy
    public synchronized void closeAll() {
        invalidate();
    }

    private record Holder(String fingerprint, McpSyncClient client, String failureCode) { }

    public record ManagedConnection(
            UUID id,
            String connectionName,
            String url,
            String endpoint,
            AuthType authType,
            boolean authenticationConfigured,
            McpSyncClient client,
            String failureCode
    ) { }
}
