package com.kj.stackchan.device;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.workday.WorkdayCompanionService;
import com.kj.stackchan.workday.WorkdayPilotService;
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
    private WorkdayPilotService workdayPilotService;

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

    @Autowired(required = false)
    public void setWorkdayPilotService(WorkdayPilotService workdayPilotService) {
        this.workdayPilotService = workdayPilotService;
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
        recordHeartbeat(deviceId, null, safetyState, firmwareVersion, rssi,
                applicationOtaSupported, expression, body);
    }

    @Transactional
    public void recordHeartbeat(UUID deviceId, Long sequence, String safetyState, String firmwareVersion,
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

        Optional<DeviceEntity> existing = deviceRepository.findById(deviceId);
        boolean restarted = false;
        long motionFailureDelta = 0;
        String motionFailureCode = null;
        if (existing.isPresent()) {
            DeviceEntity device = existing.get();
            Long previousSequence = device.getLastDeviceSequence();
            long previousFailureCount = device.getBodyDiagnostics().failureCount();
            restarted = sequence != null && previousSequence != null && sequence < previousSequence;
            if (!restarted && body != null && body.failureCount() > previousFailureCount) {
                motionFailureDelta = body.failureCount() - previousFailureCount;
                motionFailureCode = body.lastFailureCode();
            }
            device.recordHeartbeat(
                    clock.instant(), safetyState, firmwareVersion, rssi, applicationOtaSupported,
                    expression, body
            );
            if (sequence != null) device.recordDeviceSequence(sequence);
        }
        if (workdayPilotService != null) {
            try {
                if (restarted) workdayPilotService.recordDeviceRestart(deviceId);
                if (motionFailureDelta > 0) {
                    workdayPilotService.recordMotionObservation(
                            deviceId, motionFailureCode, motionFailureDelta
                    );
                }
            } catch (RuntimeException ignored) {
                // Pilot metrics are isolated and must never reject a valid heartbeat.
            }
        }
        if (workdayCompanionService != null && body != null && body.proximitySupported()) {
            try {
                workdayCompanionService.presenceChanged(deviceId, body.present());
            } catch (RuntimeException ignored) {
                // Presence automation must never reject an otherwise valid heartbeat.
            }
        }
    }
}
