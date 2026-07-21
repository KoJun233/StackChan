package com.kj.stackchan.device;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String INVALID_EVENT = "{\"type\":\"error\",\"code\":\"invalid_event\",\"message\":\"event rejected\"}";
    private static final Set<String> HEARTBEAT_FIELDS = Set.of(
            "type", "sequence", "battery_percent", "rssi", "safety_state"
    );
    private static final Set<String> HEARTBEAT_WITH_FIRMWARE_FIELDS = Set.of(
            "type", "sequence", "battery_percent", "rssi", "safety_state", "firmware_version"
    );
    private static final Pattern FIRMWARE_VERSION_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,80}");
    private static final Set<String> COMMAND_ACK_FIELDS = Set.of(
            "type", "sequence", "command_id", "accepted"
    );

    private final DeviceConnectionRegistry connectionRegistry;
    private final DeviceEventService deviceEventService;
    private final DeviceCommandAcknowledgementService acknowledgementService;
    private final ObjectMapper objectMapper;
    private final DeviceVoiceSettingsCoordinator voiceSettingsCoordinator;

    @Autowired
    public DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper,
            DeviceVoiceSettingsCoordinator voiceSettingsCoordinator
    ) {
        this.connectionRegistry = connectionRegistry;
        this.deviceEventService = deviceEventService;
        this.acknowledgementService = acknowledgementService;
        this.objectMapper = objectMapper;
        this.voiceSettingsCoordinator = voiceSettingsCoordinator;
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            ObjectMapper objectMapper
    ) {
        this(connectionRegistry, deviceEventService, (deviceId, commandId, accepted) -> { }, objectMapper, null);
    }

    DeviceWebSocketHandler(
            DeviceConnectionRegistry connectionRegistry,
            DeviceEventService deviceEventService,
            DeviceCommandAcknowledgementService acknowledgementService,
            ObjectMapper objectMapper
    ) {
        this(connectionRegistry, deviceEventService, acknowledgementService, objectMapper, null);
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
            processEvent(deviceId, event);
            lastSequence.set(event.sequence());
        }
    }

    private void processEvent(UUID deviceId, DeviceInboundEvent event) {
        if (event instanceof HeartbeatEvent heartbeat) {
            deviceEventService.recordHeartbeat(deviceId, heartbeat.safetyState(), heartbeat.firmwareVersion());
            return;
        }
        CommandAcknowledgementEvent acknowledgement = (CommandAcknowledgementEvent) event;
        logger.debug(
                "Device {} acknowledged command {} with accepted={}",
                deviceId,
                acknowledgement.commandId(),
                acknowledgement.accepted()
        );
        acknowledgementService.record(deviceId, acknowledgement.commandId(), acknowledgement.accepted());
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
        return new HeartbeatEvent(sequence, batteryPercent, rssi, safetyState, firmwareVersion);
    }

    private CommandAcknowledgementEvent parseCommandAcknowledgement(JsonNode root) {
        requireOnlyFields(root, COMMAND_ACK_FIELDS);
        long sequence = requiredPositiveSequence(root);
        String commandId = requiredText(root, "command_id");
        JsonNode accepted = root.get("accepted");
        if (accepted == null || !accepted.isBoolean()) {
            throw new InvalidDeviceEventException();
        }
        return new CommandAcknowledgementEvent(sequence, commandId, accepted.booleanValue());
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

    private String requiredText(JsonNode root, String fieldName) {
        JsonNode field = root.get(fieldName);
        if (field == null || !field.isTextual() || field.textValue().isBlank()) {
            throw new InvalidDeviceEventException();
        }
        return field.textValue();
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
                .allMatch(entry -> HEARTBEAT_WITH_FIRMWARE_FIELDS.contains(entry.getKey())));
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

    private sealed interface DeviceInboundEvent permits HeartbeatEvent, CommandAcknowledgementEvent {

        long sequence();
    }

    private record HeartbeatEvent(
            long sequence,
            int batteryPercent,
            int rssi,
            String safetyState,
            String firmwareVersion
    )
            implements DeviceInboundEvent {
    }

    private record CommandAcknowledgementEvent(long sequence, String commandId, boolean accepted)
            implements DeviceInboundEvent {
    }

    private static final class InvalidDeviceEventException extends RuntimeException {
    }
}
