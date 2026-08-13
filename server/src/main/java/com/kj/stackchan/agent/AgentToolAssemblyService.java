package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.FileSystemSkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ReminderService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;

@Service
public class AgentToolAssemblyService {

    public static final Set<String> BUILTIN_TOOL_IDS = Set.of(
            CurrentTimeTool.ID,
            CapabilityListTool.ID,
            NextReminderTool.ID,
            PendingMemoryCountTool.ID
    );

    private final AgentSettingsService settingsService;
    private final AgentMcpCatalog mcpCatalog;
    private final FileSystemSkillRegistry skillRegistry;
    private final AgentSkillPackageService skillPackageService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Clock clock;
    private final ReminderService reminderService;
    private final LongTermMemoryService memoryService;

    public AgentToolAssemblyService(
            AgentSettingsService settingsService,
            AgentMcpCatalog mcpCatalog,
            FileSystemSkillRegistry skillRegistry,
            AgentSkillPackageService skillPackageService,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            Clock clock,
            ReminderService reminderService,
            LongTermMemoryService memoryService
    ) {
        this.settingsService = settingsService;
        this.mcpCatalog = mcpCatalog;
        this.skillRegistry = skillRegistry;
        this.skillPackageService = skillPackageService;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.clock = clock;
        this.reminderService = reminderService;
        this.memoryService = memoryService;
    }

    public AgentToolAssembly assemble(AgentInvocationContext context) {
        Set<String> enabledSkills = enabledSkills();
        AgentMcpCatalog.AuthorizedMcpTools mcpTools = mcpCatalog.authorizedTools();
        Map<String, List<ToolCallback>> groupedTools = new LinkedHashMap<>();
        Map<String, AgentToolPolicyInterceptor.ToolAuditMetadata> auditMetadata = new LinkedHashMap<>();
        List<ToolCallback> directTools = new ArrayList<>();

        if (settingsService.isEnabled(AgentCapabilityType.BUILTIN_TOOL, CurrentTimeTool.ID)) {
            ToolCallback callback = callback(new CurrentTimeTool(clock, userZoneId(), objectMapper));
            directTools.add(callback);
            auditMetadata.put(callback.getToolDefinition().name(), new AgentToolPolicyInterceptor.ToolAuditMetadata(
                    AgentToolSource.BUILTIN,
                    null,
                    null
            ));
        }

        if (context.deviceId() != null && settingsService.isEnabled(AgentCapabilityType.BUILTIN_TOOL, NextReminderTool.ID)) {
            ToolCallback callback = callback(new NextReminderTool(context.deviceId(), context.roleId(), reminderService, objectMapper));
            directTools.add(callback);
            auditMetadata.put(callback.getToolDefinition().name(), new AgentToolPolicyInterceptor.ToolAuditMetadata(
                    AgentToolSource.BUILTIN, null, null));
        }
        if (context.deviceId() != null && settingsService.isEnabled(
                AgentCapabilityType.BUILTIN_TOOL, PendingMemoryCountTool.ID)) {
            ToolCallback callback = callback(new PendingMemoryCountTool(context.deviceId(), context.roleId(), memoryService, objectMapper));
            directTools.add(callback);
            auditMetadata.put(callback.getToolDefinition().name(), new AgentToolPolicyInterceptor.ToolAuditMetadata(
                    AgentToolSource.BUILTIN, null, null));
        }

        for (AgentMcpCatalog.AuthorizedMcpTool mcpTool : mcpTools.tools()) {
            directTools.add(mcpTool.callback());
            auditMetadata.put(
                    mcpTool.callbackName(),
                    new AgentToolPolicyInterceptor.ToolAuditMetadata(
                            AgentToolSource.MCP,
                            mcpTool.connectionName(),
                            null
                    )
            );
        }

        List<String> visibleToolNames = new ArrayList<>();
        directTools.forEach(tool -> visibleToolNames.add(tool.getToolDefinition().name()));
        groupedTools.values().forEach(group -> group.forEach(tool -> visibleToolNames.add(
                tool.getToolDefinition().name()
        )));
        if (!enabledSkills.isEmpty()) {
            visibleToolNames.add("read_skill");
        }
        if (settingsService.isEnabled(AgentCapabilityType.BUILTIN_TOOL, CapabilityListTool.ID)) {
            visibleToolNames.add(CapabilityListTool.ID);
            ToolCallback callback = callback(new CapabilityListTool(visibleToolNames, List.copyOf(enabledSkills), objectMapper));
            directTools.add(callback);
            auditMetadata.put(callback.getToolDefinition().name(), new AgentToolPolicyInterceptor.ToolAuditMetadata(
                    AgentToolSource.BUILTIN,
                    null,
                    null
            ));
        }

        SkillRegistry filteredRegistry = new FilteringSkillRegistry(skillRegistry, enabledSkills);
        List<SkillSnapshot> skills = filteredRegistry.listAll().stream()
                .map(skill -> new SkillSnapshot(skill.getName(), skill.getDescription(), true))
                .toList();
        return new AgentToolAssembly(
                List.copyOf(directTools),
                Map.copyOf(groupedTools),
                filteredRegistry,
                skills,
                Map.copyOf(auditMetadata),
                mcpTools.connections()
        );
    }

    private Set<String> enabledSkills() {
        return new LinkedHashSet<>(skillPackageService.enabledNames());
    }

    private ToolCallback callback(Object toolObject) {
        return callbacks(toolObject)[0];
    }

    private ToolCallback[] callbacks(Object toolObject) {
        return ToolCallbacks.from(toolObject);
    }

    private ZoneId userZoneId() {
        try {
            return ZoneId.of(appProperties.getAgent().getUserZoneId());
        } catch (DateTimeException exception) {
            throw new IllegalStateException("COMPANION_AGENT_USER_ZONE_ID is invalid", exception);
        }
    }

    public record AgentToolAssembly(
            List<ToolCallback> directTools,
            Map<String, List<ToolCallback>> groupedTools,
            SkillRegistry skillRegistry,
            List<SkillSnapshot> skills,
            Map<String, AgentToolPolicyInterceptor.ToolAuditMetadata> auditMetadata,
            List<AgentMcpCatalog.McpConnectionSnapshot> mcpConnections
    ) {
    }

    public record SkillSnapshot(String id, String description, boolean enabled) { }

}
