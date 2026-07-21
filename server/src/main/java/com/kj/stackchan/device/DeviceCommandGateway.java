package com.kj.stackchan.device;

import java.util.UUID;

public interface DeviceCommandGateway {

    boolean stopMotion(UUID deviceId);

    boolean speakReminder(UUID deviceId, UUID reminderId, String commandId);

    boolean isConnected(UUID deviceId);
}
