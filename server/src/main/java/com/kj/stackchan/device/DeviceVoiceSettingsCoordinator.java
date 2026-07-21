package com.kj.stackchan.device;

import java.io.IOException;
import java.util.UUID;

import com.kj.stackchan.speech.SpeechSettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class DeviceVoiceSettingsCoordinator {

    private final SpeechSettingsService settingsService;
    private final ObjectProvider<DeviceConnectionRegistry> connectionRegistryProvider;

    public DeviceVoiceSettingsCoordinator(
            SpeechSettingsService settingsService,
            ObjectProvider<DeviceConnectionRegistry> connectionRegistryProvider
    ) {
        this.settingsService = settingsService;
        this.connectionRegistryProvider = connectionRegistryProvider;
    }

    public void broadcast(SpeechSettingsService.SpeechSettingsSnapshot settings) {
        DeviceConnectionRegistry connectionRegistry = connectionRegistryProvider.getIfAvailable();
        if (connectionRegistry != null) {
            connectionRegistry.broadcastVoiceConfiguration(
                    settings.wakeSensitivity(),
                    settings.speechStartThreshold(),
                    settings.speechSilenceThreshold()
            );
        }
    }

    public void sendCurrent(UUID deviceId, WebSocketSession session) throws IOException {
        DeviceConnectionRegistry connectionRegistry = connectionRegistryProvider.getIfAvailable();
        if (connectionRegistry == null) {
            return;
        }
        SpeechSettingsService.SpeechSettingsSnapshot settings = settingsService.getSettings();
        connectionRegistry.sendVoiceConfigurationIfActive(
                deviceId,
                session,
                settings.wakeSensitivity(),
                settings.speechStartThreshold(),
                settings.speechSilenceThreshold()
        );
    }
}
