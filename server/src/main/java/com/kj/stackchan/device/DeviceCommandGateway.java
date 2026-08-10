package com.kj.stackchan.device;

import java.util.UUID;

public interface DeviceCommandGateway {

    boolean stopMotion(UUID deviceId);

    boolean stopAudio(UUID deviceId);

    boolean speakReminder(UUID deviceId, UUID reminderId, String commandId);

    boolean installWakeModel(
            UUID deviceId,
            UUID jobId,
            String modelName,
            String sha256,
            int artifactSize,
            String commandId
    );

    boolean installFirmware(
            UUID deviceId,
            UUID jobId,
            String version,
            String sha256,
            int artifactSize,
            String commandId
    );

    boolean installExpressionPack(
            UUID deviceId,
            UUID packId,
            String sha256,
            int artifactSize,
            String commandId
    );

    boolean clearExpressionPack(UUID deviceId, String commandId);

    boolean isConnected(UUID deviceId);
}
