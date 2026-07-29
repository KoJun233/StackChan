package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.agent.AgentCapabilityType;
import com.kj.stackchan.agent.AgentMcpCatalog;
import com.kj.stackchan.agent.AgentMcpConnectionEntity.AuthType;
import com.kj.stackchan.agent.AgentMcpConnectionService;
import com.kj.stackchan.agent.AgentSettingsService;
import com.kj.stackchan.agent.AgentSkillPackageService;
import com.kj.stackchan.agent.AgentToolAuditService;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentManagementController.class)
@Import(SecurityConfiguration.class)
class AgentManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentSettingsService settingsService;
    @MockitoBean
    private AgentMcpCatalog mcpCatalog;
    @MockitoBean
    private AgentMcpConnectionService mcpConnectionService;
    @MockitoBean
    private AgentToolAuditService auditService;
    @MockitoBean
    private AgentSkillPackageService skillPackageService;
    @MockitoBean
    private AppProperties appProperties;
    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @BeforeEach
    void setUp() {
        AppProperties.Agent agent = new AppProperties.Agent();
        when(appProperties.getAgent()).thenReturn(agent);
        when(settingsService.runtimeSettings()).thenReturn(new AgentSettingsService.RuntimeSettings(
                true,
                true,
                true,
                Instant.parse("2026-07-28T00:00:00Z")
        ));
        when(skillPackageService.list()).thenReturn(List.of(
                new AgentSkillPackageService.SkillSnapshot(
                        UUID.fromString("10000000-0000-0000-0000-000000000001"),
                        "daily-routine", "每日流程", "1.0", false, "a".repeat(64),
                        2, 128, List.of("SKILL.md", "references/checklist.md"),
                        Instant.parse("2026-07-28T00:00:00Z"), Instant.parse("2026-07-28T00:00:00Z")
                )
        ));
        when(mcpCatalog.snapshot(anyBoolean())).thenReturn(new AgentMcpCatalog.DiscoverySnapshot(
                Instant.parse("2026-07-28T00:00:00Z"),
                List.of(),
                List.of()
        ));
    }

    @Test
    void requiresAuthenticationForCapabilities() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsFrameworkLimitsAndSafeCapabilityMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/agent/capabilities")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.framework").value("spring-ai-alibaba-react-agent"))
                .andExpect(jsonPath("$.frameworkVersion").value("1.1.2.2"))
                .andExpect(jsonPath("$.limits.maxToolCalls").value(4))
                .andExpect(jsonPath("$.builtInTools", hasSize(2)))
                .andExpect(jsonPath("$.skills[0].name").value("daily-routine"))
                .andExpect(jsonPath("$.skills[0].files", hasSize(2)))
                .andExpect(jsonPath("$.mcp.tools", hasSize(0)));
    }

    @Test
    void updatesTheEmergencySwitchOnlyWithCsrf() throws Exception {
        when(settingsService.updateRuntimeEnabled(false)).thenReturn(new AgentSettingsService.RuntimeSettings(
                false,
                true,
                false,
                Instant.parse("2026-07-28T01:00:00Z")
        ));

        mockMvc.perform(put("/api/v1/agent/settings")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/agent/settings")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void snapshotsTheDiscoveredSchemaWhenAnMcpToolIsEnabled() throws Exception {
        AgentMcpCatalog.McpToolSnapshot discovered = new AgentMcpCatalog.McpToolSnapshot(
                "home/lookup",
                "home",
                "mcp_home_lookup_deadbeef",
                "lookup",
                "read-only lookup",
                "a".repeat(64),
                "b".repeat(64),
                false
        );
        when(mcpCatalog.requireDiscovered("home/lookup")).thenReturn(discovered);
        when(settingsService.updateCapability(
                AgentCapabilityType.MCP_TOOL,
                "home/lookup",
                true,
                "b".repeat(64),
                "a".repeat(64)
        )).thenReturn(new AgentSettingsService.CapabilitySetting(
                AgentCapabilityType.MCP_TOOL,
                "home/lookup",
                true,
                "b".repeat(64),
                "a".repeat(64),
                Instant.parse("2026-07-28T01:00:00Z")
        ));

        mockMvc.perform(put("/api/v1/agent/capabilities")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"type":"MCP_TOOL","capabilityId":"home/lookup","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.schemaSha256").value("a".repeat(64)));

        verify(settingsService).updateCapability(
                AgentCapabilityType.MCP_TOOL,
                "home/lookup",
                true,
                "b".repeat(64),
                "a".repeat(64)
        );
    }

    @Test
    void disablesAConfiguredMcpServerWithoutChangingToolSchemas() throws Exception {
        AgentMcpCatalog.McpConnectionSnapshot connection = new AgentMcpCatalog.McpConnectionSnapshot(
                null,
                "home",
                "home-server",
                "2",
                "https://mcp.example",
                "/mcp",
                AuthType.NONE,
                false,
                false,
                true,
                1,
                null,
                true
        );
        when(mcpCatalog.requireDiscoveredConnection("home")).thenReturn(connection);
        when(settingsService.updateCapability(
                AgentCapabilityType.MCP_SERVER,
                "home",
                false,
                null,
                null
        )).thenReturn(new AgentSettingsService.CapabilitySetting(
                AgentCapabilityType.MCP_SERVER,
                "home",
                false,
                null,
                null,
                Instant.parse("2026-07-28T01:00:00Z")
        ));

        mockMvc.perform(put("/api/v1/agent/capabilities")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"type":"MCP_SERVER","capabilityId":"home","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(settingsService).updateCapability(
                AgentCapabilityType.MCP_SERVER,
                "home",
                false,
                null,
                null
        );
    }
}
