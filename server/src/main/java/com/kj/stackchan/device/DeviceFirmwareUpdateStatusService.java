package com.kj.stackchan.device;

import java.util.UUID;

public interface DeviceFirmwareUpdateStatusService {

    void record(UUID deviceId, UUID jobId, String status, String version, String sha256);
}
