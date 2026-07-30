package com.kj.stackchan.device;

import java.io.IOException;
import java.util.UUID;

import com.kj.stackchan.interaction.InteractionSettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class DeviceInteractionSettingsCoordinator {

    private final InteractionSettingsService settingsService;
    private final ObjectProvider<DeviceConnectionRegistry> registryProvider;

    public DeviceInteractionSettingsCoordinator(
            InteractionSettingsService settingsService,
            ObjectProvider<DeviceConnectionRegistry> registryProvider
    ) {
        this.settingsService = settingsService;
        this.registryProvider = registryProvider;
    }

    public void send(InteractionSettingsService.InteractionSettingsSnapshot settings) {
        DeviceConnectionRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            registry.sendInteractionConfiguration(
                    settings.deviceId(), settings.volumePercent(), settings.nightMode(),
                    settings.continuousConversationEnabled(), settings.followUpWindowSeconds()
            );
        }
    }

    public void sendCurrent(UUID deviceId, WebSocketSession session) throws IOException {
        DeviceConnectionRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        var settings = settingsService.resolve(deviceId);
        registry.sendInteractionConfigurationIfActive(
                deviceId, session, settings.volumePercent(), settings.nightMode(),
                settings.continuousConversationEnabled(), settings.followUpWindowSeconds()
        );
    }
}
