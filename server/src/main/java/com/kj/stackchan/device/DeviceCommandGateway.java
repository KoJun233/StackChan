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

    default boolean configureExpression(UUID deviceId, String themeColor, String emotion,
                                        String intensity, int durationSeconds) {
        return false;
    }

    default boolean configureExpressionFrameRate(UUID deviceId, String mode, int minFps, int maxFps) {
        return false;
    }

    default boolean previewExpression(UUID deviceId, String category, String value, int durationSeconds) {
        return false;
    }

    boolean isConnected(UUID deviceId);
}
