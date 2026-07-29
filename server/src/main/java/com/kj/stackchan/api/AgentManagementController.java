package com.kj.stackchan.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.kj.stackchan.agent.AgentCapabilityType;
import com.kj.stackchan.agent.AgentMcpCatalog;
import com.kj.stackchan.agent.AgentMcpConnectionEntity;
import com.kj.stackchan.agent.AgentMcpConnectionNotFoundException;
import com.kj.stackchan.agent.AgentMcpConnectionService;
import com.kj.stackchan.agent.AgentSettingsService;
import com.kj.stackchan.agent.AgentSkillNotFoundException;
import com.kj.stackchan.agent.AgentSkillPackageService;
import com.kj.stackchan.agent.AgentToolAuditService;
import com.kj.stackchan.agent.CapabilityListTool;
import com.kj.stackchan.agent.CurrentTimeTool;
import com.kj.stackchan.agent.InvalidAgentSkillException;
import com.kj.stackchan.agent.InvalidAgentMcpConnectionException;
import com.kj.stackchan.config.AppProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentManagementController {

    private static final Map<String, String> BUILTIN_DESCRIPTIONS = Map.of(
            CurrentTimeTool.ID, "读取当前用户时区的日期和时间",
            CapabilityListTool.ID, "列出本回合实际授权能力"
    );

    private final AgentSettingsService settingsService;
    private final AgentMcpCatalog mcpCatalog;
    private final AgentMcpConnectionService mcpConnectionService;
    private final AgentToolAuditService auditService;
    private final AgentSkillPackageService skillPackageService;
    private final AppProperties appProperties;

    public AgentManagementController(
            AgentSettingsService settingsService,
            AgentMcpCatalog mcpCatalog,
            AgentMcpConnectionService mcpConnectionService,
            AgentToolAuditService auditService,
            AgentSkillPackageService skillPackageService,
            AppProperties appProperties
    ) {
        this.settingsService = settingsService;
        this.mcpCatalog = mcpCatalog;
        this.mcpConnectionService = mcpConnectionService;
        this.auditService = auditService;
        this.skillPackageService = skillPackageService;
        this.appProperties = appProperties;
    }

    @GetMapping("/capabilities")
    public AgentCapabilities capabilities(@RequestParam(defaultValue = "false") boolean refreshMcp) {
        AgentSettingsService.RuntimeSettings runtime = settingsService.runtimeSettings();
        List<CapabilitySnapshot> builtInTools = BUILTIN_DESCRIPTIONS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new CapabilitySnapshot(
                        entry.getKey(),
                        entry.getValue(),
                        settingsService.isEnabled(AgentCapabilityType.BUILTIN_TOOL, entry.getKey())
                ))
                .toList();
        return new AgentCapabilities(
                "spring-ai-alibaba-react-agent",
                "1.1.2.2",
                runtime,
                new AgentLimits(
                        appProperties.getAgent().getMaxToolCalls(),
                        appProperties.getAgent().getTimeout().toSeconds(),
                        appProperties.getAgent().getMaxToolResultBytes(),
                        appProperties.getAgent().getMaxTotalToolResultBytes()
                ),
                builtInTools,
                skillPackageService.list(),
                mcpCatalog.snapshot(refreshMcp)
        );
    }

    @PutMapping("/settings")
    public AgentSettingsService.RuntimeSettings updateSettings(@Valid @RequestBody RuntimeSettingsRequest request) {
        return settingsService.updateRuntimeEnabled(request.enabled());
    }

    @PutMapping("/capabilities")
    public AgentSettingsService.CapabilitySetting updateCapability(
            @Valid @RequestBody CapabilitySettingsRequest request
    ) {
        return switch (request.type()) {
            case BUILTIN_TOOL -> updateKnownCapability(
                    request,
                    BUILTIN_DESCRIPTIONS.keySet(),
                    null,
                    null
            );
            case SKILL -> throw new InvalidAgentCapabilityException("请使用 Skill 管理接口启停自定义 Skill");
            case MCP_SERVER -> updateMcpServer(request);
            case MCP_TOOL -> updateMcpCapability(request);
        };
    }

    @PostMapping(path = "/skills", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentSkillPackageService.SkillSnapshot importSkill(@RequestParam("archive") MultipartFile archive) {
        return skillPackageService.importArchive(archive);
    }

    @PutMapping("/skills/{id}")
    public AgentSkillPackageService.SkillSnapshot updateSkill(
            @PathVariable UUID id,
            @RequestBody SkillSettingsRequest request
    ) {
        return skillPackageService.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/skills/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSkill(@PathVariable UUID id) {
        skillPackageService.delete(id);
    }

    @PostMapping("/mcp-connections")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentMcpConnectionService.ConnectionSnapshot createMcpConnection(
            @Valid @RequestBody McpConnectionRequest request
    ) {
        AgentMcpConnectionService.ConnectionSnapshot created = mcpConnectionService.create(request.toCommand());
        settingsService.updateCapability(
                AgentCapabilityType.MCP_SERVER,
                created.connectionName(),
                false,
                null,
                null
        );
        mcpCatalog.invalidate();
        return created;
    }

    @PutMapping("/mcp-connections/{id}")
    public AgentMcpConnectionService.ConnectionSnapshot updateMcpConnection(
            @PathVariable UUID id,
            @Valid @RequestBody McpConnectionRequest request
    ) {
        AgentMcpConnectionService.ConnectionSnapshot updated = mcpConnectionService.update(id, request.toCommand());
        settingsService.updateCapability(
                AgentCapabilityType.MCP_SERVER,
                updated.connectionName(),
                false,
                null,
                null
        );
        mcpCatalog.invalidate();
        return updated;
    }

    @DeleteMapping("/mcp-connections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMcpConnection(@PathVariable UUID id) {
        AgentMcpConnectionService.ConnectionSnapshot existing = mcpConnectionService.list().stream()
                .filter(connection -> connection.id().equals(id))
                .findFirst()
                .orElseThrow(AgentMcpConnectionNotFoundException::new);
        settingsService.updateCapability(
                AgentCapabilityType.MCP_SERVER,
                existing.connectionName(),
                false,
                null,
                null
        );
        mcpConnectionService.delete(id);
        mcpCatalog.invalidate();
    }

    @GetMapping("/tool-invocations")
    public List<AgentToolAuditService.InvocationSnapshot> toolInvocations(
            @RequestParam(defaultValue = "50") int limit
    ) {
        return auditService.list(limit);
    }

    private AgentSettingsService.CapabilitySetting updateKnownCapability(
            CapabilitySettingsRequest request,
            Set<String> knownIds,
            String sourceId,
            String schemaSha256
    ) {
        if (!knownIds.contains(request.capabilityId())) {
            throw new InvalidAgentCapabilityException("Unknown Agent capability");
        }
        return settingsService.updateCapability(
                request.type(),
                request.capabilityId(),
                request.enabled(),
                sourceId,
                schemaSha256
        );
    }

    private AgentSettingsService.CapabilitySetting updateMcpCapability(CapabilitySettingsRequest request) {
        if (!request.enabled()) {
            return settingsService.updateCapability(
                    AgentCapabilityType.MCP_TOOL,
                    request.capabilityId(),
                    false,
                    null,
                    null
            );
        }
        AgentMcpCatalog.McpToolSnapshot discovered;
        try {
            discovered = mcpCatalog.requireDiscovered(request.capabilityId());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAgentCapabilityException(exception.getMessage());
        }
        return settingsService.updateCapability(
                AgentCapabilityType.MCP_TOOL,
                discovered.capabilityId(),
                true,
                discovered.sourceIdentity(),
                discovered.schemaSha256()
        );
    }

    private AgentSettingsService.CapabilitySetting updateMcpServer(CapabilitySettingsRequest request) {
        try {
            mcpCatalog.requireDiscoveredConnection(request.capabilityId());
        } catch (IllegalArgumentException exception) {
            throw new InvalidAgentCapabilityException(exception.getMessage());
        }
        return settingsService.updateCapability(
                AgentCapabilityType.MCP_SERVER,
                request.capabilityId(),
                request.enabled(),
                null,
                null
        );
    }

    @ExceptionHandler(InvalidAgentCapabilityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidCapability(InvalidAgentCapabilityException exception) {
        return new ErrorResponse("invalid_agent_capability", exception.getMessage());
    }

    @ExceptionHandler(InvalidAgentSkillException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidSkill(InvalidAgentSkillException exception) {
        return new ErrorResponse("invalid_agent_skill", exception.getMessage());
    }

    @ExceptionHandler(AgentSkillNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse skillNotFound() {
        return new ErrorResponse("agent_skill_not_found", "Skill 不存在");
    }

    @ExceptionHandler(InvalidAgentMcpConnectionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidMcpConnection(InvalidAgentMcpConnectionException exception) {
        return new ErrorResponse("invalid_agent_mcp_connection", exception.getMessage());
    }

    @ExceptionHandler(AgentMcpConnectionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse mcpConnectionNotFound() {
        return new ErrorResponse("agent_mcp_connection_not_found", "MCP 连接不存在");
    }

    public record AgentCapabilities(
            String framework,
            String frameworkVersion,
            AgentSettingsService.RuntimeSettings runtime,
            AgentLimits limits,
            List<CapabilitySnapshot> builtInTools,
            List<AgentSkillPackageService.SkillSnapshot> skills,
            AgentMcpCatalog.DiscoverySnapshot mcp
    ) {
    }

    public record AgentLimits(
            int maxToolCalls,
            long timeoutSeconds,
            int maxToolResultBytes,
            int maxTotalToolResultBytes
    ) {
    }

    public record CapabilitySnapshot(String id, String description, boolean enabled) {
    }

    public record RuntimeSettingsRequest(boolean enabled) {
    }

    public record CapabilitySettingsRequest(
            @NotNull AgentCapabilityType type,
            @NotBlank @Size(max = 240) String capabilityId,
            boolean enabled
    ) {
    }

    public record SkillSettingsRequest(boolean enabled) { }

    public record McpConnectionRequest(
            @NotBlank @Size(max = 64) String connectionName,
            @NotBlank @Size(max = 2048) String url,
            @Size(max = 512) String endpoint,
            @NotNull AgentMcpConnectionEntity.AuthType authType,
            @Size(max = 4096) String bearerToken
    ) {
        private AgentMcpConnectionService.ConnectionCommand toCommand() {
            return new AgentMcpConnectionService.ConnectionCommand(
                    connectionName, url, endpoint, authType, bearerToken
            );
        }
    }

    public record ErrorResponse(String code, String message) {
    }

    private static final class InvalidAgentCapabilityException extends RuntimeException {
        private InvalidAgentCapabilityException(String message) {
            super(message);
        }
    }
}
