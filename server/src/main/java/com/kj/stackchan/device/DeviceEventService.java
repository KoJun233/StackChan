package com.kj.stackchan.device;

import java.time.Clock;
import java.util.UUID;

import com.kj.stackchan.workday.WorkdayCompanionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceEventService {

    public static final String MOTION_DISABLED = "motion_disabled";
    public static final String MOTION_ARMED = "motion_armed";

    private final DeviceRepository deviceRepository;
    private final Clock clock;
    private WorkdayCompanionService workdayCompanionService;

    public DeviceEventService(DeviceRepository deviceRepository, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, String safetyState) {
        recordHeartbeat(deviceId, safetyState, null, null, false);
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, String safetyState, String firmwareVersion) {
        recordHeartbeat(deviceId, safetyState, firmwareVersion, null, false);
    }

    @Transactional
    public void recordHeartbeat(
            UUID deviceId,
            String safetyState,
            String firmwareVersion,
            Integer rssi,
            boolean applicationOtaSupported
    ) {
        recordHeartbeat(deviceId, safetyState, firmwareVersion, rssi, applicationOtaSupported, null);
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, String safetyState, String firmwareVersion,
                                Integer rssi, boolean applicationOtaSupported,
                                DeviceExpressionDiagnostics expression) {
        recordHeartbeat(deviceId, safetyState, firmwareVersion, rssi,
                applicationOtaSupported, expression, null);
    }

    @Autowired(required = false)
    public void setWorkdayCompanionService(WorkdayCompanionService workdayCompanionService) {
        this.workdayCompanionService = workdayCompanionService;
    }

    public void toggleWorkday(UUID deviceId) {
        if (workdayCompanionService != null) {
            workdayCompanionService.toggleFromDevice(deviceId);
        }
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, String safetyState, String firmwareVersion,
                                Integer rssi, boolean applicationOtaSupported,
                                DeviceExpressionDiagnostics expression,
                                DeviceBodyDiagnostics body) {
        if (!MOTION_DISABLED.equals(safetyState) && !MOTION_ARMED.equals(safetyState)) {
            throw new IllegalArgumentException("Unsupported motion safety state");
        }
        if (MOTION_ARMED.equals(safetyState) &&
                (body == null || !body.bodyMotionSupported() ||
                        !body.servoFeedbackSupported() || !body.calibrated() ||
                        !("ARMED".equals(body.motionState()) ||
                                "RUNNING".equals(body.motionState())))) {
            throw new IllegalArgumentException("Armed motion requires calibrated feedback diagnostics");
        }
        if (MOTION_DISABLED.equals(safetyState) && body != null &&
                !"DISABLED".equals(body.motionState())) {
            throw new IllegalArgumentException("Disabled motion requires disabled diagnostics");
        }

        deviceRepository.findById(deviceId).ifPresent(device ->
                device.recordHeartbeat(
                        clock.instant(), safetyState, firmwareVersion, rssi, applicationOtaSupported,
                        expression, body
                )
        );
        if (workdayCompanionService != null && body != null && body.proximitySupported()) {
            try {
                workdayCompanionService.presenceChanged(deviceId, body.present());
            } catch (RuntimeException ignored) {
                // Presence automation must never reject an otherwise valid heartbeat.
            }
        }
    }
}
