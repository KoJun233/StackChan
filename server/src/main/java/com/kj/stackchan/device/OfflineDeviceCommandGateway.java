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
    public boolean stopAudio(UUID deviceId) {
        return connectionRegistry.sendStopAudio(deviceId);
    }

    @Override
    public boolean speakReminder(UUID deviceId, UUID reminderId, String commandId) {
        return connectionRegistry.sendReminder(deviceId, reminderId, commandId);
    }

    @Override
    public boolean installWakeModel(
            UUID deviceId,
            UUID jobId,
            String modelName,
            String sha256,
            int artifactSize,
            String commandId
    ) {
        return connectionRegistry.sendWakeModelInstall(
                deviceId,
                jobId,
                modelName,
                sha256,
                artifactSize,
                commandId
        );
    }

    @Override
    public boolean installFirmware(
            UUID deviceId,
            UUID jobId,
            String version,
            String sha256,
            int artifactSize,
            String commandId
    ) {
        return connectionRegistry.sendFirmwareInstall(
                deviceId, jobId, version, sha256, artifactSize, commandId
        );
    }

    @Override
    public boolean installExpressionPack(
            UUID deviceId,
            UUID packId,
            String sha256,
            int artifactSize,
            String commandId
    ) {
        return connectionRegistry.sendExpressionPackInstall(
                deviceId, packId, sha256, artifactSize, commandId
        );
    }

    @Override
    public boolean clearExpressionPack(UUID deviceId, String commandId) {
        return connectionRegistry.sendExpressionPackClear(deviceId, commandId);
    }

    @Override
    public boolean isConnected(UUID deviceId) {
        return connectionRegistry.isConnected(deviceId);
    }
}
