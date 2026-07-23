package com.kj.stackchan.device;

import java.util.UUID;

public interface DeviceWakeModelStatusService {

    void record(UUID deviceId, UUID jobId, String status, String modelName, String sha256);
}
