package com.kj.stackchan.device;

import java.util.UUID;

public interface DeviceCommandAcknowledgementService {

    void record(UUID deviceId, String commandId, boolean accepted);

    default void record(UUID deviceId, String commandId, boolean accepted, DeviceCommandResult result) {
        record(deviceId, commandId, accepted);
    }
}
