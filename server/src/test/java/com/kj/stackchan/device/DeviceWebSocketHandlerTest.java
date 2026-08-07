package com.kj.stackchan.device;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import com.kj.stackchan.speech.VoiceTurnCancellationService;
import com.kj.stackchan.speech.VoiceTurnFailureCode;
import com.kj.stackchan.speech.VoiceTurnStage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
    private final DeviceWakeModelStatusService wakeModelStatusService =
            mock(DeviceWakeModelStatusService.class);
    private final DeviceFirmwareUpdateStatusService firmwareUpdateStatusService =
            mock(DeviceFirmwareUpdateStatusService.class);
    private final VoiceTurnDiagnosticsService voiceTurnDiagnosticsService =
            mock(VoiceTurnDiagnosticsService.class);
    private final VoiceTurnCancellationService voiceTurnCancellationService =
            mock(VoiceTurnCancellationService.class);
    private final DeviceWebSocketHandler handler = new DeviceWebSocketHandler(
            connectionRegistry,
            deviceEventService,
            acknowledgementService,
            new ObjectMapper(),
            voiceSettingsCoordinator,
            wakeModelStatusService,
            voiceTurnDiagnosticsService,
            voiceTurnCancellationService
    );

    private DeviceWebSocketHandler handler() {
        handler.setFirmwareUpdateStatusService(firmwareUpdateStatusService);
        return handler;
    }

    @Test
    void sendsCurrentVoiceSettingsWhenTheDeviceConnects() throws Exception {
        WebSocketSession session = authenticatedSession();

        handler().afterConnectionEstablished(session);

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

        verify(deviceEventService).recordHeartbeat(DEVICE_ID, "motion_disabled", null, -54, false);
    }

    @Test
    void recordsTheFirmwareVersionReportedByHeartbeat() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"heartbeat","sequence":7,"battery_percent":80,"rssi":-54,"safety_state":"motion_disabled","firmware_version":"b954a43"}
                """));

        verify(deviceEventService).recordHeartbeat(DEVICE_ID, "motion_disabled", "b954a43", -54, false);
    }

    @Test
    void recordsApplicationOtaCapabilityAndFirmwareStatus() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler().afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"heartbeat","sequence":7,"battery_percent":80,"rssi":-54,"safety_state":"motion_disabled","firmware_version":"ops-002","application_ota_supported":true}
                """));
        handler.handleTextMessage(session, new TextMessage("""
                {"type":"firmware_update_status","sequence":8,"job_id":"550e8400-e29b-41d4-a716-446655440000","status":"INSTALLED","version":"ops-002","sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
                """));

        verify(deviceEventService).recordHeartbeat(
                DEVICE_ID, "motion_disabled", "ops-002", -54, true
        );
        verify(firmwareUpdateStatusService).record(
                DEVICE_ID,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "INSTALLED",
                "ops-002",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
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

    @Test
    void forwardsAnOptionalCancelledCommandResult() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"command_ack","sequence":8,"command_id":"cmd-123",\
                "accepted":false,"result":"cancelled"}
                """));

        verify(acknowledgementService).record(
                DEVICE_ID,
                "cmd-123",
                false,
                DeviceCommandResult.CANCELLED
        );
    }

    @Test
    void rejectsACommandResultWhenTheCommandWasAccepted() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"command_ack","sequence":8,"command_id":"cmd-123",\
                "accepted":true,"result":"failed"}
                """));

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).contains("invalid_event");
        verifyNoInteractions(acknowledgementService);
    }

    @Test
    void forwardsValidatedWakeModelStatusForTheAuthenticatedDevice() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"wake_model_status","sequence":9,"job_id":"550e8400-e29b-41d4-a716-446655440000","status":"INSTALLED","model_name":"wn9l_stackchan_custom","sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
                """));

        verify(wakeModelStatusService).record(
                DEVICE_ID,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "INSTALLED",
                "wn9l_stackchan_custom",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        );
    }

    @Test
    void recordsOnlyPrivacySafeVoiceTurnStageFields() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);
        UUID turnId = UUID.randomUUID();

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"voice_turn_stage","sequence":9,"turn_id":"%s","stage":"FAILED",\
                "elapsed_ms":1250,"failure_code":"NO_SPEECH"}
                """.formatted(turnId)));

        verify(voiceTurnDiagnosticsService).recordDeviceStage(
                DEVICE_ID,
                turnId,
                VoiceTurnStage.FAILED,
                1250,
                VoiceTurnFailureCode.NO_SPEECH
        );
    }

    @Test
    void acceptsOnlyStructuredContinuousConversationStages() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);
        UUID turnId = UUID.randomUUID();

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"voice_turn_stage","sequence":9,"turn_id":"%s",\
                "stage":"FOLLOW_UP_LISTENING","elapsed_ms":0}
                """.formatted(turnId)));
        handler.handleTextMessage(session, new TextMessage("""
                {"type":"voice_turn_stage","sequence":10,"turn_id":"%s",\
                "stage":"FOLLOW_UP_TIMEOUT","elapsed_ms":8000}
                """.formatted(turnId)));
        handler.handleTextMessage(session, new TextMessage("""
                {"type":"voice_turn_stage","sequence":11,"turn_id":"%s",\
                "stage":"CONVERSATION_ENDED","elapsed_ms":8001}
                """.formatted(turnId)));

        verify(voiceTurnDiagnosticsService).recordDeviceStage(
                DEVICE_ID, turnId, VoiceTurnStage.FOLLOW_UP_LISTENING, 0, null
        );
        verify(voiceTurnDiagnosticsService).recordDeviceStage(
                DEVICE_ID, turnId, VoiceTurnStage.FOLLOW_UP_TIMEOUT, 8000, null
        );
        verify(voiceTurnDiagnosticsService).recordDeviceStage(
                DEVICE_ID, turnId, VoiceTurnStage.CONVERSATION_ENDED, 8001, null
        );
    }

    @Test
    void cancelsTheMatchingVoiceTurnAfterDiagnosticsOwnershipValidation() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);
        UUID turnId = UUID.randomUUID();

        TextMessage cancelled = new TextMessage("""
                {"type":"voice_turn_stage","sequence":9,"turn_id":"%s",\
                "stage":"CANCELLED","elapsed_ms":1250}
                """.formatted(turnId));
        handler.handleTextMessage(session, cancelled);
        handler.handleTextMessage(session, cancelled);

        verify(voiceTurnDiagnosticsService).recordDeviceStage(
                DEVICE_ID,
                turnId,
                VoiceTurnStage.CANCELLED,
                1250,
                null
        );
        verify(voiceTurnCancellationService).cancel(DEVICE_ID, turnId);
    }

    @Test
    void rejectsCancellationForATurnOwnedByAnotherDevice() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);
        UUID turnId = UUID.randomUUID();
        doThrow(new IllegalArgumentException()).when(voiceTurnDiagnosticsService).recordDeviceStage(
                DEVICE_ID,
                turnId,
                VoiceTurnStage.CANCELLED,
                50,
                null
        );

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"voice_turn_stage","sequence":9,"turn_id":"%s",\
                "stage":"CANCELLED","elapsed_ms":50}
                """.formatted(turnId)));

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).contains("invalid_event");
        verify(voiceTurnCancellationService, never()).cancel(DEVICE_ID, turnId);
    }

    @Test
    void rejectsWakeModelStatusWithUntrustedFields() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"wake_model_status","sequence":9,"job_id":"550e8400-e29b-41d4-a716-446655440000","status":"INSTALLED","model_name":"wn9l_stackchan_custom","sha256":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef","detail":"untrusted"}
                """));

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload())
                .isEqualTo("{\"type\":\"error\",\"code\":\"invalid_event\",\"message\":\"event rejected\"}");
        verifyNoInteractions(wakeModelStatusService);
    }

    @Test
    void rejectsVoiceTurnStageWithAFreeTextField() throws Exception {
        WebSocketSession session = authenticatedSession();
        handler.afterConnectionEstablished(session);

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"voice_turn_stage","sequence":9,\
                "turn_id":"550e8400-e29b-41d4-a716-446655440000",\
                "stage":"LISTENING","elapsed_ms":20,"transcript":"secret"}
                """));

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).contains("invalid_event");
        verifyNoInteractions(voiceTurnDiagnosticsService);
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
