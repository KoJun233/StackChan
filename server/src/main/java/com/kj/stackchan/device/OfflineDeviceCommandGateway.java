package com.kj.stackchan.device;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
class OfflineDeviceCommandGateway implements DeviceCommandGateway {

    private final DeviceConnectionRegistry connectionRegistry;

    OfflineDeviceCommandGateway(DeviceConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    @Override
    public boolean stopMotion(UUID deviceId) {
        return connectionRegistry.sendStopMotion(deviceId);
    }

    @Override
    public boolean speakReminder(UUID deviceId, UUID reminderId, String commandId) {
        return connectionRegistry.sendReminder(deviceId, reminderId, commandId);
    }

    @Override
    public boolean isConnected(UUID deviceId) {
        return connectionRegistry.isConnected(deviceId);
    }
}
