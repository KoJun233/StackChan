package com.kj.stackchan.device;

import java.time.Clock;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceEventService {

    public static final String MOTION_DISABLED = "motion_disabled";

    private final DeviceRepository deviceRepository;
    private final Clock clock;

    public DeviceEventService(DeviceRepository deviceRepository, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, String safetyState) {
        recordHeartbeat(deviceId, safetyState, null);
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, String safetyState, String firmwareVersion) {
        if (!MOTION_DISABLED.equals(safetyState)) {
            throw new IllegalArgumentException("Heartbeats cannot enable motion");
        }

        deviceRepository.findById(deviceId).ifPresent(device ->
                device.recordHeartbeat(clock.instant(), MOTION_DISABLED, firmwareVersion)
        );
    }
}
