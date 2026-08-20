package com.kj.stackchan.device;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import com.kj.stackchan.speech.VoiceTurnCancellationService;
import com.kj.stackchan.speech.VoiceTurnFailureCode;
import com.kj.stackchan.speech.VoiceTurnStage;
import com.kj.stackchan.expression.DeviceExpressionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(DeviceWebSocketHandler.class);
    private static final String LAST_SEQUENCE_ATTRIBUTE = DeviceWebSocketHandler.class.getName() + ".lastSequence";
    private static final String EXPRESSION_THEME_SYNCED_ATTRIBUTE =
            DeviceWebSocketHandler.class.getName() + ".expressionThemeSynced";
    private static final String EXPRESSION_FRAME_RATE_SYNCED_ATTRIBUTE =
            DeviceWebSocketHandler.class.getName() + ".expressionFrameRateSynced";
    private static final String INVALID_EVENT = "{\"type\":\"error\",\"code\":\"invalid_event\",\"message\":\"event rejected\"}";
    private static final Set<String> HEARTBEAT_FIELDS = Set.of(
            "type", "sequence", "battery_percent", "rssi", "safety_state"
    );
    private static final Set<String> HEARTBEAT_WITH_FIRMWARE_FIELDS = Set.of(
            "type", "sequence", "battery_percent", "rssi", "safety_state", "firmware_version"
    );
    private static final Set<String> HEARTBEAT_WITH_OTA_FIELDS = Set.of(
            "type", "sequence", "battery_percent", "rssi", "safety_state", "firmware_version",
            "application_ota_supported"
    );
    private static final Set<String> HEARTBEAT_WITH_EXPRESSION_FIELDS = Set.of(
            "type", "sequence", "battery_percent", "rssi", "safety_state", "firmware_version",
            "application_ota_supported", "dynamic_expression_supported", "expression"
    );
    private static final Set<String> EXPRESSION_DIAGNOSTIC_FIELDS = Set.of(
            "target_fps", "actual_fps", "draw_time_us", "transfer_time_us",
            "display_lock_wait_us", "dropped_frames", "audio_underruns", "minimum_free_heap",
            "active_layer", "degrade_reason", "dynamic_renderer", "imu_supported",
            "proximity_supported"
    );
    private static final Set<String> EXPRESSION_LAYERS = Set.of(
            "IDLE", "EMOTION", "INTERACTION", "PHYSICAL", "SYSTEM"
    );
    private static final String[] EXPRESSION_DEGRADE_REASONS = {
            "NONE", "DRAW_BUDGET", "DISPLAY_LOCK", "AUDIO_BUSY", "AUDIO_UNDERRUN", "IDLE_SLEEP"
    };
    private static final Pattern FIRMWARE_VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,80}");
    private static final Set<String> COMMAND_ACK_FIELDS = Set.of(
            "type", "sequence", "command_id", "accepted"
    );
    private static final Set<String> COMMAND_ACK_WITH_RESULT_FIELDS = Set.of(
            "type", "sequence", "command_id", "accepted", "result"
    );
    private static final Set<String> WAKE_MODEL_STATUS_FIELDS = Set.of(
            "type", "sequence", "job_id", "status", "model_name", "sha256"
    );
    private static final Set<String> FIRMWARE_UPDATE_STATUS_FIELDS = Set.of(
            "type", "sequence", "job_id", "status", "version", "sha256"
    );
    private static final Pattern WAKE_MODEL_NAME_PATTERN = Pattern.compile("[a-z0-9_]{1,31}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    );
    private static final Set<String> VOICE_TURN_STAGE_FIELDS = Set.of(
            "type", "sequence", "turn_id", "stage", "elapsed_ms"
    );
    private static final Set<String> VOICE_TURN_FAILURE_FIELDS = Set.of(
            "type", "sequence", "turn_id", "stage", "elapsed_ms", "failure_code"
    );
    private static final int MAX_VOICE_TURN_ELAPSED_MS = 300_000;

    private final DeviceConnectionRegistry connectionRegistry;
    private final DeviceEventService deviceEventService;
    private final DeviceCommandAcknowledgementService acknowledgementService;
    private final ObjectMapper objectMapper;
    private final DeviceVoiceSettingsCoordinator voiceSettingsCoordinator;
    private final DeviceWakeModelStatusService wakeModelStatusService;
    private final VoiceTurnDiagnosticsService voiceTurnDiagnosticsService;
    private final VoiceTurnCancellationService voiceTurnCancellationService;
    private DeviceExpressionPackCoordinator expressionPackCoordinator;
    private DeviceFirmwareUpdateStatusService firmwareUpdateStatusService;
    private DeviceExpressionService deviceExpressionService;

    @Autowired(required = false)
    void setExpressionPackCoordinator(DeviceExpressionPackCoordinator expressionPackCoordinator) {
        this.expressionPackCoordinator = expressionPackCoordinator;
    }

    @Autowired(required = false)
    void setFirmwareUpdateStatusService(DeviceFirmwareUpdateStatusService firmwareUpdateStatusService) {
        this.firmwareUpdateStatusService = firmwareUpdateStatusService;
    }

    @Autowired(required = false)
    void setDeviceExpressionService(DeviceExpressionService deviceExpressionService) {
        this.deviceExpressionService = deviceExpressionService;
    }

    @Autowired
    public DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper,
            DeviceVoiceSettingsCoordinator voiceSettingsCoordinator,
            DeviceWakeModelStatusService wakeModelStatusService,
            VoiceTurnDiagnosticsService voiceTurnDiagnosticsService,
            VoiceTurnCancellationService voiceTurnCancellationService
    ) {
        this.connectionRegistry = connectionRegistry;
        this.deviceEventService = deviceEventService;
        this.acknowledgementService = acknowledgementService;
        this.objectMapper = objectMapper;
        this.voiceSettingsCoordinator = voiceSettingsCoordinator;
        this.wakeModelStatusService = wakeModelStatusService;
        this.voiceTurnDiagnosticsService = voiceTurnDiagnosticsService;
        this.voiceTurnCancellationService = voiceTurnCancellationService;
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            ObjectMapper objectMapper
    ) {
        this(
                connectionRegistry,
                deviceEventService,
                (deviceId, commandId, accepted) -> { },
                objectMapper,
                null,
                (deviceId, jobId, status, modelName, sha256) -> { },
                null,
                null
        );
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper
    ) {
        this(
                connectionRegistry,
                deviceEventService,
                acknowledgementService,
                objectMapper,
                null,
                (deviceId, jobId, status, modelName, sha256) -> { },
                null,
                null
        );
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper,
            DeviceVoiceSettingsCoordinator voiceSettingsCoordinator
    ) {
        this(
                connectionRegistry,
                deviceEventService,
                acknowledgementService,
                objectMapper,
                voiceSettingsCoordinator,
                (deviceId, jobId, status, modelName, sha256) -> { },
                null,
                null
        );
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper,
            DeviceVoiceSettingsCoordinator voiceSettingsCoordinator,
            DeviceWakeModelStatusService wakeModelStatusService
    ) {
        this(
                connectionRegistry,
                deviceEventService,
                acknowledgementService,
                objectMapper,
                voiceSettingsCoordinator,
                wakeModelStatusService,
                null,
                null
        );
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper,
            DeviceVoiceSettingsCoordinator voiceSettingsCoordinator,
            DeviceWakeModelStatusService wakeModelStatusService,
            VoiceTurnDiagnosticsService voiceTurnDiagnosticsService
    ) {
        this(
                connectionRegistry,
                deviceEventService,
                acknowledgementService,
                objectMapper,
                voiceSettingsCoordinator,
                wakeModelStatusService,
                voiceTurnDiagnosticsService,
                null
        );
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper,
            DeviceVoiceSettingsCoordinator voiceSettingsCoordinator,
            VoiceTurnDiagnosticsService voiceTurnDiagnosticsService
    ) {
        this(
                connectionRegistry,
                deviceEventService,
                acknowledgementService,
                objectMapper,
                voiceSettingsCoordinator,
                (deviceId, jobId, status, modelName, sha256) -> { },
                voiceTurnDiagnosticsService,
                null
        );
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        UUID deviceId = authenticatedDeviceId(session);
        if (deviceId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        session.getAttributes().put(LAST_SEQUENCE_ATTRIBUTE, new AtomicLong());
        connectionRegistry.register(deviceId, session);
        if (voiceSettingsCoordinator != null) {
            voiceSettingsCoordinator.sendCurrent(deviceId, session);
        }
        if (expressionPackCoordinator != null) {
            expressionPackCoordinator.sendCurrent(deviceId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID deviceId = authenticatedDeviceId(session);
        if (deviceId != null) {
            connectionRegistry.unregister(deviceId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        UUID deviceId = authenticatedDeviceId(session);
        if (deviceId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        try {
            DeviceInboundEvent event = parseEvent(message.getPayload());
            connectionRegistry.processIfActive(deviceId, session, () -> processIfNewSequence(deviceId, session, event));
        } catch (InvalidDeviceEventException exception) {
            sendInvalidEvent(deviceId, session);
        }
    }

    private void processIfNewSequence(UUID deviceId, WebSocketSession session, DeviceInboundEvent event) {
        AtomicLong lastSequence = lastSequence(session);
        synchronized (session) {
            if (event.sequence() <= lastSequence.get()) {
                return;
            }
            processEvent(deviceId, session, event);
            lastSequence.set(event.sequence());
        }
    }

    private void processEvent(UUID deviceId, WebSocketSession session, DeviceInboundEvent event) {
        if (event instanceof HeartbeatEvent heartbeat) {
            if (heartbeat.expression() == null) {
                deviceEventService.recordHeartbeat(
                        deviceId, heartbeat.safetyState(), heartbeat.firmwareVersion(),
                        heartbeat.rssi(), heartbeat.applicationOtaSupported());
            } else {
                deviceEventService.recordHeartbeat(
                        deviceId, heartbeat.safetyState(), heartbeat.firmwareVersion(),
                        heartbeat.rssi(), heartbeat.applicationOtaSupported(), heartbeat.expression());
                if (deviceExpressionService != null) {
                    if (!Boolean.TRUE.equals(session.getAttributes().get(EXPRESSION_THEME_SYNCED_ATTRIBUTE)) &&
                            deviceExpressionService.synchronizeActiveRoleTheme(deviceId)) {
                        session.getAttributes().put(EXPRESSION_THEME_SYNCED_ATTRIBUTE, true);
                    }
                    if (!Boolean.TRUE.equals(session.getAttributes().get(EXPRESSION_FRAME_RATE_SYNCED_ATTRIBUTE)) &&
                            deviceExpressionService.synchronizeFrameRate(deviceId)) {
                        session.getAttributes().put(EXPRESSION_FRAME_RATE_SYNCED_ATTRIBUTE, true);
                    }
                }
            }
            return;
        }
        if (event instanceof WakeModelStatusEvent modelStatus) {
            wakeModelStatusService.record(
                    deviceId,
                    modelStatus.jobId(),
                    modelStatus.status(),
                    modelStatus.modelName(),
                    modelStatus.sha256()
            );
            return;
        }
        if (event instanceof FirmwareUpdateStatusEvent firmwareStatus) {
            if (firmwareUpdateStatusService != null) {
                firmwareUpdateStatusService.record(
                        deviceId,
                        firmwareStatus.jobId(),
                        firmwareStatus.status(),
                        firmwareStatus.version(),
                        firmwareStatus.sha256()
                );
            }
            return;
        }
        if (event instanceof VoiceTurnStageEvent voiceTurnStage) {
            if (voiceTurnDiagnosticsService != null) {
                try {
                    voiceTurnDiagnosticsService.recordDeviceStage(
                            deviceId,
                            voiceTurnStage.turnId(),
                            voiceTurnStage.stage(),
                            voiceTurnStage.elapsedMs(),
                            voiceTurnStage.failureCode()
                    );
                } catch (IllegalArgumentException exception) {
                    throw new InvalidDeviceEventException();
                } catch (RuntimeException exception) {
                    logger.warn(
                            "Voice turn diagnostics unavailable for device={} turn={} stage={}",
                            deviceId,
                            voiceTurnStage.turnId(),
                            voiceTurnStage.stage()
                    );
                }
            }
            if (voiceTurnStage.stage() == VoiceTurnStage.CANCELLED && voiceTurnCancellationService != null) {
                voiceTurnCancellationService.cancel(deviceId, voiceTurnStage.turnId());
            }
            return;
        }
        CommandAcknowledgementEvent acknowledgement = (CommandAcknowledgementEvent) event;
        logger.debug(
                "Device {} acknowledged command {} with accepted={} result={}",
                deviceId,
                acknowledgement.commandId(),
                acknowledgement.accepted(),
                acknowledgement.result()
        );
        if (acknowledgement.result() == null) {
            acknowledgementService.record(deviceId, acknowledgement.commandId(), acknowledgement.accepted());
        } else {
            acknowledgementService.record(
                    deviceId,
                    acknowledgement.commandId(),
                    acknowledgement.accepted(),
                    acknowledgement.result()
            );
        }
    }

    private DeviceInboundEvent parseEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || !root.isObject()) {
                throw new InvalidDeviceEventException();
            }
            String type = requiredText(root, "type");
            return switch (type) {
                case "heartbeat" -> parseHeartbeat(root);
                case "command_ack" -> parseCommandAcknowledgement(root);
                case "wake_model_status" -> parseWakeModelStatus(root);
                case "firmware_update_status" -> parseFirmwareUpdateStatus(root);
                case "voice_turn_stage" -> parseVoiceTurnStage(root);
                default -> throw new InvalidDeviceEventException();
            };
        } catch (IOException exception) {
            throw new InvalidDeviceEventException();
        }
    }

    private HeartbeatEvent parseHeartbeat(JsonNode root) {
        if (!hasHeartbeatFields(root)) {
            throw new InvalidDeviceEventException();
        }
        long sequence = requiredPositiveSequence(root);
        int batteryPercent = requiredInteger(root, "battery_percent");
        if (batteryPercent < 0 || batteryPercent > 100) {
            throw new InvalidDeviceEventException();
        }
        int rssi = requiredInteger(root, "rssi");
        String safetyState = requiredText(root, "safety_state");
        if (!DeviceEventService.MOTION_DISABLED.equals(safetyState)) {
            throw new InvalidDeviceEventException();
        }
        String firmwareVersion = root.has("firmware_version") ? requiredText(root, "firmware_version") : null;
        if (firmwareVersion != null && !FIRMWARE_VERSION_PATTERN.matcher(firmwareVersion).matches()) {
            throw new InvalidDeviceEventException();
        }
        boolean applicationOtaSupported = false;
        if (root.has("application_ota_supported")) {
            JsonNode otaSupported = root.get("application_ota_supported");
            if (!otaSupported.isBoolean() || !otaSupported.booleanValue()) {
                throw new InvalidDeviceEventException();
            }
            applicationOtaSupported = true;
        }
        DeviceExpressionDiagnostics expression = null;
        if (root.has("dynamic_expression_supported")) {
            JsonNode supported = root.get("dynamic_expression_supported");
            if (!supported.isBoolean() || !supported.booleanValue()) throw new InvalidDeviceEventException();
            expression = parseExpressionDiagnostics(root.get("expression"));
        }
        return new HeartbeatEvent(
                sequence, batteryPercent, rssi, safetyState, firmwareVersion, applicationOtaSupported,
                expression
        );
    }

    private DeviceExpressionDiagnostics parseExpressionDiagnostics(JsonNode value) {
        if (value == null || !value.isObject()) throw new InvalidDeviceEventException();
        requireOnlyFields(value, EXPRESSION_DIAGNOSTIC_FIELDS);
        int targetFps = requiredInteger(value, "target_fps");
        int actualFps = requiredInteger(value, "actual_fps");
        int drawTime = requiredInteger(value, "draw_time_us");
        int transferTime = requiredInteger(value, "transfer_time_us");
        int lockWait = requiredInteger(value, "display_lock_wait_us");
        long dropped = requiredNonnegativeLong(value, "dropped_frames");
        long underruns = requiredNonnegativeLong(value, "audio_underruns");
        long minimumHeap = requiredNonnegativeLong(value, "minimum_free_heap");
        String layer = requiredText(value, "active_layer");
        int reasonCode = requiredInteger(value, "degrade_reason");
        JsonNode dynamicRenderer = value.get("dynamic_renderer");
        JsonNode imu = value.get("imu_supported");
        JsonNode proximity = value.get("proximity_supported");
        if (targetFps < 1 || targetFps > 60 || actualFps < 0 ||
                actualFps > 120 || drawTime < 0 || transferTime < 0 || lockWait < 0 ||
                !EXPRESSION_LAYERS.contains(layer) || reasonCode < 0 ||
                reasonCode >= EXPRESSION_DEGRADE_REASONS.length || dynamicRenderer == null ||
                !dynamicRenderer.isBoolean() || imu == null || !imu.isBoolean() ||
                proximity == null || !proximity.isBoolean()) {
            throw new InvalidDeviceEventException();
        }
        return new DeviceExpressionDiagnostics(targetFps, actualFps, drawTime, transferTime, lockWait,
                dropped, underruns, minimumHeap, layer, EXPRESSION_DEGRADE_REASONS[reasonCode],
                dynamicRenderer.booleanValue(), imu.booleanValue(), proximity.booleanValue());
    }

    private CommandAcknowledgementEvent parseCommandAcknowledgement(JsonNode root) {
        if (root.size() == COMMAND_ACK_FIELDS.size()) {
            requireOnlyFields(root, COMMAND_ACK_FIELDS);
        } else {
            requireOnlyFields(root, COMMAND_ACK_WITH_RESULT_FIELDS);
        }
        long sequence = requiredPositiveSequence(root);
        String commandId = requiredText(root, "command_id");
        JsonNode accepted = root.get("accepted");
        if (accepted == null || !accepted.isBoolean()) {
            throw new InvalidDeviceEventException();
        }
        DeviceCommandResult result = null;
        if (root.has("result")) {
            if (accepted.booleanValue()) {
                throw new InvalidDeviceEventException();
            }
            String resultValue = requiredText(root, "result");
            try {
                result = DeviceCommandResult.valueOf(resultValue.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new InvalidDeviceEventException();
            }
            if (!resultValue.equals(result.name().toLowerCase(Locale.ROOT))) {
                throw new InvalidDeviceEventException();
            }
        }
        return new CommandAcknowledgementEvent(sequence, commandId, accepted.booleanValue(), result);
    }

    private WakeModelStatusEvent parseWakeModelStatus(JsonNode root) {
        requireOnlyFields(root, WAKE_MODEL_STATUS_FIELDS);
        long sequence = requiredPositiveSequence(root);
        String jobId = requiredText(root, "job_id");
        String status = requiredText(root, "status");
        String modelName = requiredText(root, "model_name");
        String sha256 = requiredText(root, "sha256");
        if (!UUID_PATTERN.matcher(jobId).matches() ||
                !("INSTALLED".equals(status) || "ROLLED_BACK".equals(status)) ||
                !WAKE_MODEL_NAME_PATTERN.matcher(modelName).matches() ||
                !SHA256_PATTERN.matcher(sha256).matches()) {
            throw new InvalidDeviceEventException();
        }
        return new WakeModelStatusEvent(sequence, UUID.fromString(jobId), status, modelName, sha256);
    }

    private FirmwareUpdateStatusEvent parseFirmwareUpdateStatus(JsonNode root) {
        requireOnlyFields(root, FIRMWARE_UPDATE_STATUS_FIELDS);
        long sequence = requiredPositiveSequence(root);
        String jobId = requiredText(root, "job_id");
        String status = requiredText(root, "status");
        String version = requiredText(root, "version");
        String sha256 = requiredText(root, "sha256");
        if (!UUID_PATTERN.matcher(jobId).matches() ||
                !("INSTALLED".equals(status) || "ROLLED_BACK".equals(status)) ||
                !FIRMWARE_VERSION_PATTERN.matcher(version).matches() ||
                !SHA256_PATTERN.matcher(sha256).matches()) {
            throw new InvalidDeviceEventException();
        }
        return new FirmwareUpdateStatusEvent(
                sequence, UUID.fromString(jobId), status, version, sha256
        );
    }

    private VoiceTurnStageEvent parseVoiceTurnStage(JsonNode root) {
        String stageValue = requiredText(root, "stage");
        VoiceTurnStage stage;
        try {
            stage = VoiceTurnStage.valueOf(stageValue);
        } catch (IllegalArgumentException exception) {
            throw new InvalidDeviceEventException();
        }
        if (!stage.isDeviceStage()) {
            throw new InvalidDeviceEventException();
        }
        requireOnlyFields(
                root,
                stage == VoiceTurnStage.FAILED ? VOICE_TURN_FAILURE_FIELDS : VOICE_TURN_STAGE_FIELDS
        );
        long sequence = requiredPositiveSequence(root);
        UUID turnId = requiredUuid(root, "turn_id");
        int elapsedMs = requiredInteger(root, "elapsed_ms");
        if (elapsedMs < 0 || elapsedMs > MAX_VOICE_TURN_ELAPSED_MS) {
            throw new InvalidDeviceEventException();
        }
        VoiceTurnFailureCode failureCode = null;
        if (stage == VoiceTurnStage.FAILED) {
            try {
                failureCode = VoiceTurnFailureCode.valueOf(requiredText(root, "failure_code"));
            } catch (IllegalArgumentException exception) {
                throw new InvalidDeviceEventException();
            }
        }
        return new VoiceTurnStageEvent(sequence, turnId, stage, elapsedMs, failureCode);
    }

    private long requiredPositiveSequence(JsonNode root) {
        JsonNode sequence = root.get("sequence");
        if (sequence == null || !sequence.isIntegralNumber() || !sequence.canConvertToLong() || sequence.longValue() <= 0) {
            throw new InvalidDeviceEventException();
        }
        return sequence.longValue();
    }

    private int requiredInteger(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        if (field == null || !field.isIntegralNumber() || !field.canConvertToInt()) {
            throw new InvalidDeviceEventException();
        }
        return field.intValue();
    }

    private long requiredNonnegativeLong(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        if (field == null || !field.isIntegralNumber() || !field.canConvertToLong() || field.longValue() < 0) {
            throw new InvalidDeviceEventException();
        }
        return field.longValue();
    }

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        if (field == null || !field.isTextual() || field.textValue().isBlank()) {
            throw new InvalidDeviceEventException();
        }
        return field.textValue();
    }

    private UUID requiredUuid(JsonNode root, String fieldName) {
        String value = requiredText(root, fieldName);
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw new InvalidDeviceEventException();
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new InvalidDeviceEventException();
        }
    }

    private void requireOnlyFields(JsonNode root, Set<String> allowedFields) {
        if (root.size() != allowedFields.size() || !root.properties().stream().allMatch(entry -> allowedFields.contains(entry.getKey()))) {
            throw new InvalidDeviceEventException();
        }
    }

    private boolean hasHeartbeatFields(JsonNode root) {
        return (root.size() == HEARTBEAT_FIELDS.size() && root.properties().stream()
                .allMatch(entry -> HEARTBEAT_FIELDS.contains(entry.getKey())))
                || (root.size() == HEARTBEAT_WITH_FIRMWARE_FIELDS.size() && root.properties().stream()
                .allMatch(entry -> HEARTBEAT_WITH_FIRMWARE_FIELDS.contains(entry.getKey())))
                || (root.size() == HEARTBEAT_WITH_OTA_FIELDS.size() && root.properties().stream()
                .allMatch(entry -> HEARTBEAT_WITH_OTA_FIELDS.contains(entry.getKey())))
                || (root.size() == HEARTBEAT_WITH_EXPRESSION_FIELDS.size() && root.properties().stream()
                .allMatch(entry -> HEARTBEAT_WITH_EXPRESSION_FIELDS.contains(entry.getKey())));
    }

    private AtomicLong lastSequence(WebSocketSession session) {
        Object lastSequence = session.getAttributes().get(LAST_SEQUENCE_ATTRIBUTE);
        if (lastSequence instanceof AtomicLong atomicLong) {
            return atomicLong;
        }
        AtomicLong initialSequence = new AtomicLong();
        session.getAttributes().put(LAST_SEQUENCE_ATTRIBUTE, initialSequence);
        return initialSequence;
    }

    private void sendInvalidEvent(UUID deviceId, WebSocketSession session) throws IOException {
        connectionRegistry.sendIfActive(deviceId, session, new TextMessage(INVALID_EVENT));
    }

    private UUID authenticatedDeviceId(WebSocketSession session) {
        Object deviceId = session.getAttributes().get(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE);
        return deviceId instanceof UUID authenticatedDeviceId ? authenticatedDeviceId : null;
    }

    private sealed interface DeviceInboundEvent permits HeartbeatEvent, CommandAcknowledgementEvent,
            WakeModelStatusEvent, FirmwareUpdateStatusEvent, VoiceTurnStageEvent {

        long sequence();
    }

    private record HeartbeatEvent(
            long sequence,
            int batteryPercent,
            int rssi,
            String safetyState,
            String firmwareVersion,
            boolean applicationOtaSupported,
            DeviceExpressionDiagnostics expression
    )
            implements DeviceInboundEvent {
    }

    private record CommandAcknowledgementEvent(
            long sequence,
            String commandId,
            boolean accepted,
            DeviceCommandResult result
    )
            implements DeviceInboundEvent {
    }

    private record WakeModelStatusEvent(
            long sequence,
            UUID jobId,
            String status,
            String modelName,
            String sha256
    ) implements DeviceInboundEvent {
    }

    private record FirmwareUpdateStatusEvent(
            long sequence,
            UUID jobId,
            String status,
            String version,
            String sha256
    ) implements DeviceInboundEvent {
    }

    private record VoiceTurnStageEvent(
            long sequence,
            UUID turnId,
            VoiceTurnStage stage,
            int elapsedMs,
            VoiceTurnFailureCode failureCode
    ) implements DeviceInboundEvent {
    }

    private static final class InvalidDeviceEventException extends RuntimeException {
    }
}
