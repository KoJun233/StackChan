package com.kj.stackchan.device;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.speech.VoiceWakeSensitivity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceConnectionRegistryTest {

    private static final UUID DEVICE_ID = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");
    private static final String CREDENTIAL_VERSION_ATTRIBUTE =
            DeviceWebSocketHandshakeInterceptor.class.getName() + ".credentialVersion";
    private static final String TOKEN_EXPIRES_AT_ATTRIBUTE =
            DeviceWebSocketHandshakeInterceptor.class.getName() + ".tokenExpiresAt";
    private static final Instant FUTURE_EXPIRY = Instant.parse("2099-01-01T00:00:00Z");

    private final DeviceConnectionRegistry connectionRegistry = new DeviceConnectionRegistry(new ObjectMapper());
    private final OfflineDeviceCommandGateway commandGateway = new OfflineDeviceCommandGateway(connectionRegistry);

    @Test
    void sendsStopMotionToTheAuthenticatedLiveDeviceOnly() throws Exception {
        WebSocketSession session = authenticatedSession(1L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(commandGateway.stopMotion(DEVICE_ID)).isTrue();

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).matches(
                "\\{\"type\":\"stop_motion\",\"command_id\":\"[0-9a-f-]{36}\"}"
        );
    }

    @Test
    void sendsStrictSpeakReminderCommandToTheAuthenticatedLiveDevice() throws Exception {
        UUID reminderId = UUID.fromString("f20b6177-3f7a-466a-9eae-70120bbf1912");
        WebSocketSession session = authenticatedSession(1L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(commandGateway.speakReminder(DEVICE_ID, reminderId, "cmd-123")).isTrue();

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).isEqualTo(
                "{\"type\":\"speak_reminder\",\"command_id\":\"cmd-123\","
                        + "\"reminder_id\":\"f20b6177-3f7a-466a-9eae-70120bbf1912\"}"
        );
    }

    @Test
    void sendsStrictVoiceDetectionConfigurationToAnActiveDevice() throws Exception {
        WebSocketSession session = authenticatedSession(1L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(connectionRegistry.sendVoiceConfigurationIfActive(
                DEVICE_ID,
                session,
                VoiceWakeSensitivity.SENSITIVE,
                350,
                200
        )).isTrue();

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).matches(
                "\\{\"type\":\"configure_voice_detection\",\"command_id\":\"[0-9a-f-]{36}\","
                        + "\"wake_sensitivity\":\"SENSITIVE\",\"speech_start_threshold\":350,"
                        + "\"speech_silence_threshold\":200}"
        );
    }

    @Test
    void sendsStrictInteractionConfigurationAndStopAudioCommands() throws Exception {
        WebSocketSession session = authenticatedSession(1L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(connectionRegistry.sendInteractionConfiguration(DEVICE_ID, 65, true)).isTrue();
        assertThat(commandGateway.stopAudio(DEVICE_ID)).isTrue();

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(message.capture());
        assertThat(message.getAllValues().get(0).getPayload()).matches(
                "\\{\"type\":\"configure_interaction\",\"command_id\":\"[0-9a-f-]{36}\","
                        + "\"volume_percent\":65,\"night_mode\":true}"
        );
        assertThat(message.getAllValues().get(1).getPayload()).matches(
                "\\{\"type\":\"stop_audio\",\"command_id\":\"[0-9a-f-]{36}\"}"
        );
    }

    @Test
    void makesTheDeviceOfflineAfterItsSessionDisconnects() {
        WebSocketSession session = authenticatedSession(1L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);
        connectionRegistry.unregister(DEVICE_ID, session);

        assertThat(commandGateway.stopMotion(DEVICE_ID)).isFalse();
        assertThat(connectionRegistry.sessionStateCount()).isZero();
    }

    @Test
    void retiresAndClosesTheSessionWhenStopMotionDeliveryFails() throws Exception {
        WebSocketSession session = authenticatedSession(1L, FUTURE_EXPIRY);
        doThrow(new IOException("connection lost")).when(session).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(commandGateway.stopMotion(DEVICE_ID)).isFalse();
        assertThat(commandGateway.stopMotion(DEVICE_ID)).isFalse();

        verify(session, times(1)).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));
        verify(session).close(org.springframework.web.socket.CloseStatus.NORMAL);
        assertThat(connectionRegistry.sessionStateCount()).isZero();
    }

    @Test
    void closesExpiredSessionsBeforeTheyCanReportOrReceiveCommands() throws Exception {
        WebSocketSession session = authenticatedSession(1L, Instant.EPOCH);
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(commandGateway.stopMotion(DEVICE_ID)).isFalse();
        assertThat(connectionRegistry.processIfActive(DEVICE_ID, session, () -> {
            throw new AssertionError("Expired session must not process device events");
        })).isFalse();

        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        assertThat(connectionRegistry.sessionStateCount()).isZero();
    }

    @Test
    void revokesAnActiveSessionWhenCredentialVersionChanges() throws Exception {
        WebSocketSession session = authenticatedSession(4L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);

        revokeCredentials(DEVICE_ID, 5L);

        assertThat(commandGateway.stopMotion(DEVICE_ID)).isFalse();
        verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        assertThat(connectionRegistry.sessionStateCount()).isZero();
    }

    @Test
    void rejectsDelayedRegistrationAuthenticatedBeforeCredentialRotation() throws Exception {
        revokeCredentials(DEVICE_ID, 2L);
        WebSocketSession staleSession = authenticatedSession(1L, FUTURE_EXPIRY);

        connectionRegistry.register(DEVICE_ID, staleSession);

        verify(staleSession).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        assertThat(commandGateway.stopMotion(DEVICE_ID)).isFalse();
        assertThat(isConnected(DEVICE_ID)).isFalse();

        WebSocketSession laterStaleSession = authenticatedSession(1L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, laterStaleSession);

        verify(laterStaleSession).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        assertThat(connectionRegistry.sessionStateCount()).isZero();
    }

    @Test
    void ignoresAnOlderCredentialRevocationCallback() throws Exception {
        revokeCredentials(DEVICE_ID, 3L);
        WebSocketSession currentSession = authenticatedSession(3L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, currentSession);

        revokeCredentials(DEVICE_ID, 2L);

        assertThat(isConnected(DEVICE_ID)).isTrue();
        assertThat(commandGateway.stopMotion(DEVICE_ID)).isTrue();
        WebSocketSession staleSession = authenticatedSession(2L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, staleSession);
        verify(staleSession).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        assertThat(isConnected(DEVICE_ID)).isTrue();
    }

    @Test
    void reportsCommandAvailabilityOnlyForAnAuthorizedOpenSession() {
        WebSocketSession session = authenticatedSession(2L, FUTURE_EXPIRY);
        connectionRegistry.register(DEVICE_ID, session);

        assertThat(isConnected(DEVICE_ID)).isTrue();
        connectionRegistry.unregister(DEVICE_ID, session);
        assertThat(isConnected(DEVICE_ID)).isFalse();
    }

    @Test
    void passiveCallsForUnknownDevicesDoNotAllocateSessionState() {
        for (long value = 1; value <= 64; value++) {
            UUID unknownDeviceId = new UUID(0, value);
            WebSocketSession unknownSession = mock(WebSocketSession.class);

            assertThat(commandGateway.stopMotion(unknownDeviceId)).isFalse();
            assertThat(connectionRegistry.processIfActive(unknownDeviceId, unknownSession, () -> {
                throw new AssertionError("Unknown session must not be processed");
            })).isFalse();
            connectionRegistry.unregister(unknownDeviceId, unknownSession);
        }

        assertThat(stateRecordCount()).isZero();
    }

    private WebSocketSession authenticatedSession(long credentialVersion, Instant expiresAt) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, DEVICE_ID);
        attributes.put(CREDENTIAL_VERSION_ATTRIBUTE, credentialVersion);
        attributes.put(TOKEN_EXPIRES_AT_ATTRIBUTE, expiresAt);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    private void revokeCredentials(UUID deviceId, long currentVersion) {
        try {
            connectionRegistry.getClass()
                    .getMethod("revokeCredentials", UUID.class, long.class)
                    .invoke(connectionRegistry, deviceId, currentVersion);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private boolean isConnected(UUID deviceId) {
        try {
            return (boolean) connectionRegistry.getClass()
                    .getMethod("isConnected", UUID.class)
                    .invoke(connectionRegistry, deviceId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private int stateRecordCount() {
        try {
            return (int) connectionRegistry.getClass()
                    .getDeclaredMethod("stateRecordCount")
                    .invoke(connectionRegistry);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
