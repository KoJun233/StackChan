package com.kj.stackchan.agent;

import java.time.Clock;
import java.util.List;

import com.kj.stackchan.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentSettingsService {

    private final AgentRuntimeSettingsRepository runtimeSettingsRepository;
    private final AgentCapabilitySettingsRepository capabilitySettingsRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    public AgentSettingsService(
            AgentRuntimeSettingsRepository runtimeSettingsRepository,
            AgentCapabilitySettingsRepository capabilitySettingsRepository,
            AppProperties appProperties,
            Clock clock
    ) {
        this.runtimeSettingsRepository = runtimeSettingsRepository;
        this.capabilitySettingsRepository = capabilitySettingsRepository;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RuntimeSettings runtimeSettings() {
        AgentRuntimeSettingsEntity entity = runtimeSettingsRepository
                .findById(AgentRuntimeSettingsEntity.SINGLETON_ID)
                .orElseGet(() -> new AgentRuntimeSettingsEntity(true, clock.instant()));
        return new RuntimeSettings(
                appProperties.getAgent().isEnabled() && entity.isEnabled(),
                appProperties.getAgent().isEnabled(),
                entity.isEnabled(),
                entity.getUpdatedAt()
        );
    }

    @Transactional
    public RuntimeSettings updateRuntimeEnabled(boolean enabled) {
        AgentRuntimeSettingsEntity entity = runtimeSettingsRepository
                .findById(AgentRuntimeSettingsEntity.SINGLETON_ID)
                .orElseGet(() -> new AgentRuntimeSettingsEntity(enabled, clock.instant()));
        entity.update(enabled, clock.instant());
        runtimeSettingsRepository.save(entity);
        return runtimeSettings();
    }

    @Transactional(readOnly = true)
    public boolean isEnabled(AgentCapabilityType type, String capabilityId) {
        return capabilitySettingsRepository.findByCapabilityTypeAndCapabilityId(type, capabilityId)
                .map(AgentCapabilitySettingsEntity::isEnabled)
                .orElse(type != AgentCapabilityType.MCP_TOOL);
    }

    @Transactional(readOnly = true)
    public boolean isMcpToolEnabled(String capabilityId, String sourceId, String schemaSha256) {
        return capabilitySettingsRepository
                .findByCapabilityTypeAndCapabilityId(AgentCapabilityType.MCP_TOOL, capabilityId)
                .filter(AgentCapabilitySettingsEntity::isEnabled)
                .filter(setting -> sourceId.equals(setting.getSourceId()))
                .filter(setting -> schemaSha256.equals(setting.getSchemaSha256()))
                .isPresent();
    }

    @Transactional
    public CapabilitySetting updateCapability(
            AgentCapabilityType type,
            String capabilityId,
            boolean enabled,
            String sourceId,
            String schemaSha256
    ) {
        AgentCapabilitySettingsEntity entity = capabilitySettingsRepository
                .findByCapabilityTypeAndCapabilityId(type, capabilityId)
                .orElseGet(() -> new AgentCapabilitySettingsEntity(
                        type,
                        capabilityId,
                        enabled,
                        sourceId,
                        schemaSha256,
                        clock.instant()
                ));
        entity.update(enabled, sourceId, schemaSha256, clock.instant());
        AgentCapabilitySettingsEntity saved = capabilitySettingsRepository.save(entity);
        return toSetting(saved);
    }

    @Transactional(readOnly = true)
    public List<CapabilitySetting> listSettings() {
        return capabilitySettingsRepository.findAll().stream().map(this::toSetting).toList();
    }

    private CapabilitySetting toSetting(AgentCapabilitySettingsEntity entity) {
        return new CapabilitySetting(
                entity.getCapabilityType(),
                entity.getCapabilityId(),
                entity.isEnabled(),
                entity.getSourceId(),
                entity.getSchemaSha256(),
                entity.getUpdatedAt()
        );
    }

    public record RuntimeSettings(
            boolean enabled,
            boolean deploymentEnabled,
            boolean adminEnabled,
            java.time.Instant updatedAt
    ) {
    }

    public record CapabilitySetting(
            AgentCapabilityType type,
            String capabilityId,
            boolean enabled,
            String sourceId,
            String schemaSha256,
            java.time.Instant updatedAt
    ) {
    }
}
