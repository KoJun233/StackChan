package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMcpCatalogTest {

    @Test
    void exposesOnlyToolsWhoseConnectionIdentityAndSchemaAreStillAuthorized() {
        @SuppressWarnings("unchecked")
        ObjectProvider<List<McpSyncClient>> clientsProvider = mock(ObjectProvider.class);
        AgentSettingsService settingsService = mock(AgentSettingsService.class);
        ManagedMcpClientRegistry managedClients = mock(ManagedMcpClientRegistry.class);
        when(managedClients.connections(true)).thenReturn(List.of());
        McpSyncClient client = mock(McpSyncClient.class);
        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("lookup")
                .description("read-only lookup")
                .inputSchema(new McpSchema.JsonSchema(
                        "object",
                        Map.of("query", Map.of("type", "string")),
                        List.of("query"),
                        false,
                        Map.of(),
                        Map.of()
                ))
                .build();
        when(clientsProvider.getIfAvailable(org.mockito.ArgumentMatchers.<java.util.function.Supplier<List<McpSyncClient>>>any()))
                .thenReturn(List.of(client));
        when(client.getClientInfo()).thenReturn(new McpSchema.Implementation("stackchan-tools", "home", "1"));
        when(client.getServerInfo()).thenReturn(new McpSchema.Implementation("home-server", "Home", "2"));
        when(client.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(tool), null));
        when(settingsService.isEnabled(AgentCapabilityType.MCP_SERVER, "home")).thenReturn(true);
        when(settingsService.isMcpToolEnabled(eq("home/lookup"), anyString(), anyString())).thenReturn(true);
        McpStreamableHttpClientProperties mcpProperties = new McpStreamableHttpClientProperties();
        mcpProperties.getConnections().put(
                "home",
                new McpStreamableHttpClientProperties.ConnectionParameters("https://mcp.example", "/mcp")
        );

        AgentMcpCatalog catalog = new AgentMcpCatalog(
                clientsProvider,
                settingsService,
                mcpProperties,
                managedClients,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC)
        );

        AgentMcpCatalog.DiscoverySnapshot snapshot = catalog.snapshot(true);

        assertThat(snapshot.connections()).singleElement().satisfies(connection -> {
            assertThat(connection.connectionName()).isEqualTo("home");
            assertThat(connection.healthy()).isTrue();
        });
        assertThat(snapshot.tools()).singleElement().satisfies(discovered -> {
            assertThat(discovered.capabilityId()).isEqualTo("home/lookup");
            assertThat(discovered.schemaSha256()).hasSize(64);
            assertThat(discovered.callbackName()).startsWith("mcp_home_lookup_");
        });
        AgentMcpCatalog.McpToolSnapshot discovered = snapshot.tools().getFirst();
        org.mockito.Mockito.reset(settingsService);
        when(settingsService.isEnabled(AgentCapabilityType.MCP_SERVER, "home")).thenReturn(true);
        when(settingsService.isMcpToolEnabled(
                discovered.capabilityId(),
                discovered.sourceIdentity(),
                discovered.schemaSha256()
        )).thenReturn(true);
        AgentMcpCatalog.AuthorizedMcpTools authorized = catalog.authorizedTools();
        assertThat(authorized.tools()).singleElement().satisfies(allowed ->
                assertThat(allowed.callback().getToolDefinition().name()).isEqualTo(allowed.callbackName())
        );

        mcpProperties.getConnections().put(
                "home",
                new McpStreamableHttpClientProperties.ConnectionParameters("https://replacement.example", "/mcp")
        );
        AgentMcpCatalog.DiscoverySnapshot changedConnection = catalog.snapshot(true);
        assertThat(changedConnection.tools()).singleElement().satisfies(changed -> {
            assertThat(changed.sourceIdentity()).isNotEqualTo(discovered.sourceIdentity());
            assertThat(changed.enabled()).isFalse();
        });
        assertThat(catalog.authorizedTools().tools()).isEmpty();
    }
}
