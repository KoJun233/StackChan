package com.kj.stackchan.device;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "devices")
public class DeviceEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "hardware_id", nullable = false, unique = true)
    private String hardwareId;

    @Column(name = "firmware_version", nullable = false)
    private String firmwareVersion;

    @Column(name = "display_name", nullable = false)
    private String displayName = "StackChan";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "safety_state", nullable = false)
    private String safetyState = "motion_disabled";

    @Column
    private Integer rssi;

    @Column(name = "application_ota_supported", nullable = false)
    private boolean applicationOtaSupported;

    @Column(name = "dynamic_expression_supported", nullable = false)
    private boolean dynamicExpressionSupported;
    @Column(name = "lifecycle_clip_supported", nullable = false)
    private boolean lifecycleClipSupported;
    @Column(name = "expression_fps_mode", nullable = false, length = 16)
    private String expressionFpsMode = "ADAPTIVE";
    @Column(name = "expression_min_fps", nullable = false)
    private int expressionMinFps = 30;
    @Column(name = "expression_max_fps", nullable = false)
    private int expressionMaxFps = 60;
    @Column(name = "expression_target_fps") private Integer expressionTargetFps;
    @Column(name = "expression_actual_fps") private Integer expressionActualFps;
    @Column(name = "expression_draw_time_us") private Integer expressionDrawTimeUs;
    @Column(name = "expression_transfer_time_us") private Integer expressionTransferTimeUs;
    @Column(name = "expression_display_lock_wait_us") private Integer expressionDisplayLockWaitUs;
    @Column(name = "expression_dropped_frames") private Long expressionDroppedFrames;
    @Column(name = "expression_audio_underruns") private Long expressionAudioUnderruns;
    @Column(name = "expression_minimum_free_heap") private Long expressionMinimumFreeHeap;
    @Column(name = "expression_active_layer", length = 16) private String expressionActiveLayer;
    @Column(name = "expression_degrade_reason", length = 32) private String expressionDegradeReason;
    @Column(name = "expression_dynamic_renderer", nullable = false) private boolean expressionDynamicRenderer;
    @Column(name = "expression_imu_supported", nullable = false) private boolean expressionImuSupported;
    @Column(name = "expression_proximity_supported", nullable = false) private boolean expressionProximitySupported;

    @Column(name = "body_motion_supported", nullable = false) private boolean bodyMotionSupported;
    @Column(name = "body_touch_supported", nullable = false) private boolean bodyTouchSupported;
    @Column(name = "proximity_supported", nullable = false) private boolean proximitySupported;
    @Column(name = "ambient_light_supported", nullable = false) private boolean ambientLightSupported;
    @Column(name = "servo_feedback_supported", nullable = false) private boolean servoFeedbackSupported;
    @Column(name = "body_calibrated", nullable = false) private boolean bodyCalibrated;
    @Column(name = "body_present", nullable = false) private boolean bodyPresent;
    @Column(name = "body_ambient_light", nullable = false, length = 16)
    private String bodyAmbientLight = "UNAVAILABLE";
    @Column(name = "body_motion_state", nullable = false, length = 16)
    private String bodyMotionState = "DISABLED";
    @Column(name = "body_last_failure_code", nullable = false, length = 32)
    private String bodyLastFailureCode = "NONE";
    @Column(name = "body_failure_count", nullable = false) private long bodyFailureCount;

    @Column(name = "refresh_token_hash")
    private String refreshTokenHash;

    @Column(name = "refresh_token_issued_at")
    private Instant refreshTokenIssuedAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    protected DeviceEntity() {
    }

    public DeviceEntity(String hardwareId, String firmwareVersion) {
        this.hardwareId = hardwareId;
        this.firmwareVersion = firmwareVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public String getSafetyState() {
        return safetyState;
    }

    public Integer getRssi() {
        return rssi;
    }

    public boolean isApplicationOtaSupported() {
        return applicationOtaSupported;
    }
    public boolean isDynamicExpressionSupported() { return dynamicExpressionSupported; }
    public boolean isLifecycleClipSupported() { return lifecycleClipSupported; }
    public String getExpressionFpsMode() { return expressionFpsMode; }
    public int getExpressionMinFps() { return expressionMinFps; }
    public int getExpressionMaxFps() { return expressionMaxFps; }
    public void configureExpressionFrameRate(String mode, int minFps, int maxFps) {
        this.expressionFpsMode = mode;
        this.expressionMinFps = minFps;
        this.expressionMaxFps = maxFps;
    }
    public DeviceExpressionDiagnostics getExpressionDiagnostics() {
        if (!dynamicExpressionSupported || expressionTargetFps == null) return null;
        return new DeviceExpressionDiagnostics(
                expressionTargetFps, expressionActualFps, expressionDrawTimeUs, expressionTransferTimeUs,
                expressionDisplayLockWaitUs, expressionDroppedFrames, expressionAudioUnderruns,
                expressionMinimumFreeHeap, expressionActiveLayer, expressionDegradeReason,
                expressionDynamicRenderer, expressionImuSupported, expressionProximitySupported,
                lifecycleClipSupported);
    }
    public DeviceBodyDiagnostics getBodyDiagnostics() {
        return new DeviceBodyDiagnostics(
                bodyMotionSupported, bodyTouchSupported, proximitySupported,
                ambientLightSupported, servoFeedbackSupported, bodyCalibrated,
                bodyPresent, bodyAmbientLight, bodyMotionState,
                bodyLastFailureCode, bodyFailureCount);
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public Instant getRefreshTokenIssuedAt() {
        return refreshTokenIssuedAt;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    void prepareForRepairing(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
        this.safetyState = "motion_disabled";
        this.applicationOtaSupported = false;
        this.dynamicExpressionSupported = false;
        this.lifecycleClipSupported = false;
        this.bodyMotionSupported = false;
        this.bodyTouchSupported = false;
        this.proximitySupported = false;
        this.ambientLightSupported = false;
        this.servoFeedbackSupported = false;
        this.bodyCalibrated = false;
        this.bodyPresent = false;
        this.bodyAmbientLight = "UNAVAILABLE";
        this.bodyMotionState = "DISABLED";
        this.bodyLastFailureCode = "NONE";
        this.bodyFailureCount = 0;
    }

    void rotateCredentials(String refreshTokenHash, Instant issuedAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenIssuedAt = issuedAt;
        this.credentialVersion++;
    }

    void recordHeartbeat(
            Instant lastSeenAt,
            String safetyState,
            String firmwareVersion,
            Integer rssi,
            boolean applicationOtaSupported
    ) {
        recordHeartbeat(lastSeenAt, safetyState, firmwareVersion, rssi, applicationOtaSupported, null);
    }

    void recordHeartbeat(Instant lastSeenAt, String safetyState, String firmwareVersion,
                         Integer rssi, boolean applicationOtaSupported,
                         DeviceExpressionDiagnostics expression) {
        recordHeartbeat(lastSeenAt, safetyState, firmwareVersion, rssi,
                applicationOtaSupported, expression, null);
    }

    void recordHeartbeat(Instant lastSeenAt, String safetyState, String firmwareVersion,
                         Integer rssi, boolean applicationOtaSupported,
                         DeviceExpressionDiagnostics expression,
                         DeviceBodyDiagnostics body) {
        this.lastSeenAt = lastSeenAt;
        this.safetyState = safetyState;
        this.rssi = rssi;
        this.applicationOtaSupported = applicationOtaSupported;
        this.dynamicExpressionSupported = expression != null;
        this.lifecycleClipSupported = expression != null && expression.lifecycleClipsSupported();
        if (expression != null) {
            this.expressionTargetFps = expression.targetFps();
            this.expressionActualFps = expression.actualFps();
            this.expressionDrawTimeUs = expression.drawTimeUs();
            this.expressionTransferTimeUs = expression.transferTimeUs();
            this.expressionDisplayLockWaitUs = expression.displayLockWaitUs();
            this.expressionDroppedFrames = expression.droppedFrames();
            this.expressionAudioUnderruns = expression.audioUnderruns();
            this.expressionMinimumFreeHeap = expression.minimumFreeHeap();
            this.expressionActiveLayer = expression.activeLayer();
            this.expressionDegradeReason = expression.degradeReason();
            this.expressionDynamicRenderer = expression.dynamicRenderer();
            this.expressionImuSupported = expression.imuSupported();
            this.expressionProximitySupported = expression.proximitySupported();
        }
        if (body != null) {
            this.bodyMotionSupported = body.bodyMotionSupported();
            this.bodyTouchSupported = body.bodyTouchSupported();
            this.proximitySupported = body.proximitySupported();
            this.ambientLightSupported = body.ambientLightSupported();
            this.servoFeedbackSupported = body.servoFeedbackSupported();
            this.bodyCalibrated = body.calibrated();
            this.bodyPresent = body.present();
            this.bodyAmbientLight = body.ambientLight();
            this.bodyMotionState = body.motionState();
            this.bodyLastFailureCode = body.lastFailureCode();
            this.bodyFailureCount = body.failureCount();
        } else {
            this.bodyMotionSupported = false;
            this.bodyTouchSupported = false;
            this.proximitySupported = false;
            this.ambientLightSupported = false;
            this.servoFeedbackSupported = false;
            this.bodyCalibrated = false;
            this.bodyPresent = false;
            this.bodyAmbientLight = "UNAVAILABLE";
            this.bodyMotionState = "DISABLED";
            this.bodyLastFailureCode = "NONE";
            this.bodyFailureCount = 0;
        }
        if (firmwareVersion != null) {
            this.firmwareVersion = firmwareVersion;
        }
    }

    void recordHeartbeat(Instant lastSeenAt, String safetyState, String firmwareVersion) {
        recordHeartbeat(lastSeenAt, safetyState, firmwareVersion, null, false);
    }
}
