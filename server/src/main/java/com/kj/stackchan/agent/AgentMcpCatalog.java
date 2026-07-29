package com.kj.stackchan.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kj.stackchan.agent.AgentMcpConnectionEntity.AuthType;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentMcpCatalog {

    private static final Duration DISCOVERY_CACHE_TTL = Duration.ofSeconds(30);
    private static final int MAX_TOOL_PAGES = 10;

    private final ObjectProvider<List<McpSyncClient>> clientsProvider;
    private final AgentSettingsService settingsService;
    private final McpStreamableHttpClientProperties mcpProperties;
    private final ManagedMcpClientRegistry managedClients;
    private final ObjectMapper canonicalMapper;
    private final Clock clock;
    private volatile CachedDiscovery cache;

    public AgentMcpCatalog(
            ObjectProvider<List<McpSyncClient>> clientsProvider,
            AgentSettingsService settingsService,
            McpStreamableHttpClientProperties mcpProperties,
            ManagedMcpClientRegistry managedClients,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.clientsProvider = clientsProvider;
        this.settingsService = settingsService;
        this.mcpProperties = mcpProperties;
        this.managedClients = managedClients;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.clock = clock;
    }

    public AuthorizedMcpTools authorizedTools() {
        Discovery discovery = discover(false);
        List<AuthorizedMcpTool> authorized = discovery.tools().stream()
                .filter(tool -> settingsService.isEnabled(
                        AgentCapabilityType.MCP_SERVER,
                        tool.connectionName()
                ))
                .filter(tool -> settingsService.isMcpToolEnabled(
                        tool.capabilityId(),
                        tool.sourceIdentity(),
                        tool.schemaSha256()
                ))
                .map(tool -> new AuthorizedMcpTool(
                        tool.capabilityId(),
                        tool.connectionName(),
                        tool.callbackName(),
                        SyncMcpToolCallback.builder()
                                .mcpClient(tool.client())
                                .tool(tool.tool())
                                .prefixedToolName(tool.callbackName())
                                .build()
                ))
                .toList();
        return new AuthorizedMcpTools(authorized, discovery.connections());
    }

    public DiscoverySnapshot snapshot(boolean refresh) {
        Discovery discovery = discover(refresh);
        List<McpToolSnapshot> tools = discovery.tools().stream()
                .map(tool -> new McpToolSnapshot(
                        tool.capabilityId(),
                        tool.connectionName(),
                        tool.callbackName(),
                        tool.originalName(),
                        tool.description(),
                        tool.schemaSha256(),
                        tool.sourceIdentity(),
                        settingsService.isMcpToolEnabled(
                                tool.capabilityId(),
                                tool.sourceIdentity(),
                                tool.schemaSha256()
                        )
                ))
                .toList();
        return new DiscoverySnapshot(discovery.discoveredAt(), discovery.connections(), tools);
    }

    public McpToolSnapshot requireDiscovered(String capabilityId) {
        return snapshot(true).tools().stream()
                .filter(tool -> tool.capabilityId().equals(capabilityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MCP tool is not currently discoverable"));
    }

    public McpConnectionSnapshot requireDiscoveredConnection(String connectionName) {
        return snapshot(true).connections().stream()
                .filter(connection -> connection.connectionName().equals(connectionName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("MCP server is not currently configured"));
    }

    public void invalidate() {
        synchronized (this) {
            cache = null;
            managedClients.invalidate();
        }
    }

    private Discovery discover(boolean refresh) {
        Instant now = clock.instant();
        CachedDiscovery current = cache;
        if (!refresh && current != null && current.expiresAt().isAfter(now)) {
            return current.discovery();
        }
        synchronized (this) {
            current = cache;
            if (!refresh && current != null && current.expiresAt().isAfter(now)) {
                return current.discovery();
            }
            Discovery loaded = loadDiscovery(now, refresh);
            cache = new CachedDiscovery(now.plus(DISCOVERY_CACHE_TTL), loaded);
            return loaded;
        }
    }

    private Discovery loadDiscovery(Instant now, boolean refresh) {
        List<McpConnectionSnapshot> connections = new ArrayList<>();
        Map<String, DiscoveredMcpTool> tools = new LinkedHashMap<>();
        List<ClientConnection> clients = new ArrayList<>();
        for (McpSyncClient client : clientsProvider.getIfAvailable(List::of)) {
            String connectionName = connectionName(client);
            McpStreamableHttpClientProperties.ConnectionParameters parameters =
                    mcpProperties.getConnections().get(connectionName);
            clients.add(new ClientConnection(
                    null,
                    connectionName,
                    parameters == null ? null : parameters.url(),
                    parameters == null ? null : parameters.endpoint(),
                    AuthType.NONE,
                    false,
                    false,
                    client,
                    null
            ));
        }
        for (ManagedMcpClientRegistry.ManagedConnection managed : managedClients.connections(refresh)) {
            clients.add(new ClientConnection(
                    managed.id(), managed.connectionName(), managed.url(), managed.endpoint(),
                    managed.authType(), managed.authenticationConfigured(), true,
                    managed.client(), managed.failureCode()
            ));
        }
        for (ClientConnection connection : clients) {
            McpSyncClient client = connection.client();
            String connectionName = connection.connectionName();
            if (client == null) {
                connections.add(connectionSnapshot(connection, false, 0, connection.failureCode()));
                continue;
            }
            String sourceIdentity = sourceIdentity(connection, client);
            try {
                List<McpSchema.Tool> connectionTools = listTools(client);
                connections.add(connectionSnapshot(connection, true, connectionTools.size(), null));
                for (McpSchema.Tool tool : connectionTools) {
                    String capabilityId = connectionName + "/" + tool.name();
                    String schemaSha256 = sha256(tool.inputSchema());
                    DiscoveredMcpTool discovered = new DiscoveredMcpTool(
                            capabilityId,
                            connectionName,
                            callbackName(capabilityId),
                            tool.name(),
                            tool.description(),
                            schemaSha256,
                            sourceIdentity,
                            client,
                            tool
                    );
                    tools.putIfAbsent(capabilityId, discovered);
                }
            } catch (RuntimeException exception) {
                connections.add(connectionSnapshot(connection, false, 0, "discovery_failed"));
            }
        }
        List<DiscoveredMcpTool> sortedTools = tools.values().stream()
                .sorted(Comparator.comparing(DiscoveredMcpTool::capabilityId))
                .toList();
        return new Discovery(now, List.copyOf(connections), sortedTools);
    }

    private McpConnectionSnapshot connectionSnapshot(
            ClientConnection connection,
            boolean healthy,
            int toolCount,
            String failureCode
    ) {
        McpSyncClient client = connection.client();
        return new McpConnectionSnapshot(
                connection.id(),
                connection.connectionName(),
                client == null ? "unknown" : serverName(client),
                client == null ? "unknown" : serverVersion(client),
                connection.url(),
                connection.endpoint(),
                connection.authType(),
                connection.authenticationConfigured(),
                connection.managed(),
                healthy,
                toolCount,
                failureCode,
                settingsService.isEnabled(AgentCapabilityType.MCP_SERVER, connection.connectionName())
        );
    }

    private List<McpSchema.Tool> listTools(McpSyncClient client) {
        List<McpSchema.Tool> tools = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < MAX_TOOL_PAGES; page++) {
            McpSchema.ListToolsResult result = cursor == null ? client.listTools() : client.listTools(cursor);
            if (result == null || result.tools() == null) {
                break;
            }
            tools.addAll(result.tools());
            cursor = result.nextCursor();
            if (!StringUtils.hasText(cursor)) {
                break;
            }
        }
        return tools;
    }

    private String connectionName(McpSyncClient client) {
        McpSchema.Implementation clientInfo = client.getClientInfo();
        if (clientInfo != null && StringUtils.hasText(clientInfo.title())) {
            return clientInfo.title();
        }
        if (clientInfo != null && StringUtils.hasText(clientInfo.name())) {
            return clientInfo.name();
        }
        return "mcp";
    }

    private String serverName(McpSyncClient client) {
        McpSchema.Implementation info = client.getServerInfo();
        return info == null || !StringUtils.hasText(info.name()) ? "unknown" : info.name();
    }

    private String serverVersion(McpSyncClient client) {
        McpSchema.Implementation info = client.getServerInfo();
        return info == null || !StringUtils.hasText(info.version()) ? "unknown" : info.version();
    }

    private String sourceIdentity(ClientConnection connection, McpSyncClient client) {
        McpSchema.Implementation info = client.getServerInfo();
        return sha256(String.join(
                "|",
                connection.connectionName(),
                Objects.toString(connection.url(), ""),
                Objects.toString(connection.endpoint(), ""),
                info == null ? "" : Objects.toString(info.name(), ""),
                info == null ? "" : Objects.toString(info.title(), ""),
                info == null ? "" : Objects.toString(info.version(), "")
        ));
    }

    private String callbackName(String capabilityId) {
        String normalized = capabilityId.toLowerCase()
                .replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        String hash = sha256(capabilityId).substring(0, 8);
        String base = "mcp_" + normalized;
        if (base.length() > 51) {
            base = base.substring(0, 51);
        }
        return base + "_" + hash;
    }

    private String sha256(Object value) {
        try {
            byte[] serialized = value instanceof String text
                    ? text.getBytes(StandardCharsets.UTF_8)
                    : canonicalMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not hash MCP tool schema", exception);
        }
    }

    private record CachedDiscovery(Instant expiresAt, Discovery discovery) {
    }

    private record Discovery(
            Instant discoveredAt,
            List<McpConnectionSnapshot> connections,
            List<DiscoveredMcpTool> tools
    ) {
    }

    private record DiscoveredMcpTool(
            String capabilityId,
            String connectionName,
            String callbackName,
            String originalName,
            String description,
            String schemaSha256,
            String sourceIdentity,
            McpSyncClient client,
            McpSchema.Tool tool
    ) {
    }

    private record ClientConnection(
            UUID id,
            String connectionName,
            String url,
            String endpoint,
            AuthType authType,
            boolean authenticationConfigured,
            boolean managed,
            McpSyncClient client,
            String failureCode
    ) { }

    public record AuthorizedMcpTools(
            List<AuthorizedMcpTool> tools,
            List<McpConnectionSnapshot> connections
    ) {
    }

    public record AuthorizedMcpTool(
            String capabilityId,
            String connectionName,
            String callbackName,
            ToolCallback callback
    ) {
    }

    public record DiscoverySnapshot(
            Instant discoveredAt,
            List<McpConnectionSnapshot> connections,
            List<McpToolSnapshot> tools
    ) {
    }

    public record McpConnectionSnapshot(
            UUID id,
            String connectionName,
            String serverName,
            String serverVersion,
            String url,
            String endpoint,
            AuthType authType,
            boolean authenticationConfigured,
            boolean managed,
            boolean healthy,
            int discoveredToolCount,
            String failureCode,
            boolean enabled
    ) {
    }

    public record McpToolSnapshot(
            String capabilityId,
            String connectionName,
            String callbackName,
            String originalName,
            String description,
            String schemaSha256,
            String sourceIdentity,
            boolean enabled
    ) {
    }
}
