package com.kj.stackchan.device;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceWebSocketHandlerTest {

    private static final UUID DEVICE_ID = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");

    private final DeviceConnectionRegistry connectionRegistry = new DeviceConnectionRegistry(new ObjectMapper());
    private final DeviceEventService deviceEventService = mock(DeviceEventService.class);
    private final DeviceCommandAcknowledgementService acknowledgementService =
            mock(DeviceCommandAcknowledgementService.class);
    private final DeviceVoiceSettingsCoordinator voiceSettingsCoordinator =
            mock(DeviceVoiceSettingsCoordinator.class);
    private final DeviceWebSocketHandler handler = new DeviceWebSocketHandler(
            connectionRegistry,
            deviceEventService,
            acknowledgementService,
            new ObjectMapper(),
            voiceSettingsCoordinator
    );

    @Test
    void sendsCurrentVoiceSettingsWhenTheDeviceConnects() throws Exception {
        WebSocketSession session = authenticatedSession();

        handler.afterConnectionEstablished(session);

        verify(voiceSettingsCoordinator).sendCurrent(DEVICE_ID, session);
    }

    @Test
    void persistsOnlyTheFirstHeartbeatSequenceForAnActiveConnection() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"heartbeat","sequence":7,"battery_percent":80,"rssi":-54,"safety_state":"motion_disabled"}
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {"type":"heartbeat","sequence":7,"battery_percent":81,"rssi":-55,"safety_state":"motion_disabled"}
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {"type":"heartbeat","sequence":6,"battery_percent":82,"rssi":-56,"safety_state":"motion_disabled"}
                """));

        verify(deviceEventService).recordHeartbeat(DEVICE_ID, "motion_disabled", null);
    }

    @Test
    void recordsTheFirmwareVersionReportedByHeartbeat() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"heartbeat","sequence":7,"battery_percent":80,"rssi":-54,"safety_state":"motion_disabled","firmware_version":"b954a43"}
                """));

        verify(deviceEventService).recordHeartbeat(DEVICE_ID, "motion_disabled", "b954a43");
    }

    @Test
    void returnsAnErrorAndDoesNotMutateStateForMalformedEvents() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"heartbeat\",\"sequence\":0}"));

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload())
                .isEqualTo("{\"type\":\"error\",\"code\":\"invalid_event\",\"message\":\"event rejected\"}");
        verifyNoInteractions(deviceEventService);
    }

    @Test
    void forwardsCommandAcknowledgementsForTheAuthenticatedDevice() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"command_ack","sequence":8,"command_id":"cmd-123","accepted":true}
                """));

        verify(acknowledgementService).record(DEVICE_ID, "cmd-123", true);
        verifyNoInteractions(deviceEventService);
    }

    private WebSocketSession authenticatedSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, DEVICE_ID);
        attributes.put(DeviceWebSocketHandshakeInterceptor.CREDENTIAL_VERSION_ATTRIBUTE, 1L);
        attributes.put(
                DeviceWebSocketHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE,
                Instant.parse("2099-01-01T00:00:00Z")
        );
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
