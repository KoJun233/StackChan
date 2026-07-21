package com.kj.stackchan.device;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeviceWebSocketSessionReplacementTest {

    private static final UUID DEVICE_ID = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");

    private final DeviceConnectionRegistry connectionRegistry = new DeviceConnectionRegistry(new ObjectMapper());
    private final OfflineDeviceCommandGateway commandGateway = new OfflineDeviceCommandGateway(connectionRegistry);
    private final DeviceEventService deviceEventService = mock(DeviceEventService.class);
    private final DeviceWebSocketHandler handler = new DeviceWebSocketHandler(
            connectionRegistry,
            deviceEventService,
            new ObjectMapper()
    );

    @Test
    void replacesAndClosesThePreviousSessionThenSuppressesItsHeartbeat() throws Exception {
        WebSocketSession originalSession = authenticatedSession();
        WebSocketSession replacementSession = authenticatedSession();
        handler.afterConnectionEstablished(originalSession);

        handler.afterConnectionEstablished(replacementSession);
        handler.handleTextMessage(originalSession, new TextMessage("""
                {"type":"heartbeat","sequence":1,"battery_percent":80,"rssi":-54,"safety_state":"motion_disabled"}
                """));

        verify(originalSession).close(CloseStatus.NORMAL);
        verifyNoInteractions(deviceEventService);
    }

    @Test
    void oldDisconnectPreservesTheReplacementAndTargetsItForStopMotion() throws Exception {
        WebSocketSession originalSession = authenticatedSession();
        WebSocketSession replacementSession = authenticatedSession();
        handler.afterConnectionEstablished(originalSession);
        handler.afterConnectionEstablished(replacementSession);

        handler.afterConnectionClosed(originalSession, CloseStatus.NORMAL);

        assertThat(commandGateway.stopMotion(DEVICE_ID)).isTrue();
        assertThat(connectionRegistry.sessionStateCount()).isEqualTo(1);
        verify(replacementSession).sendMessage(any(TextMessage.class));
        verify(originalSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendsStopMotionOnlyToTheReplacementAfterReplacementWins() throws Exception {
        WebSocketSession originalSession = authenticatedSession();
        WebSocketSession replacementSession = authenticatedSession();
        CountDownLatch replacementWon = new CountDownLatch(1);
        CountDownLatch releaseOldSessionClose = new CountDownLatch(1);
        doAnswer(invocation -> {
            replacementWon.countDown();
            assertThat(releaseOldSessionClose.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(originalSession).close(CloseStatus.NORMAL);
        connectionRegistry.register(DEVICE_ID, originalSession);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Future<?> replacement = executor.submit(
                    () -> connectionRegistry.register(DEVICE_ID, replacementSession)
            );
            assertThat(replacementWon.await(5, TimeUnit.SECONDS)).isTrue();

            java.util.concurrent.Future<Boolean> stopMotion = executor.submit(
                    () -> commandGateway.stopMotion(DEVICE_ID)
            );
            assertThat(stopMotion.get(1, TimeUnit.SECONDS)).isTrue();

            releaseOldSessionClose.countDown();
            replacement.get(5, TimeUnit.SECONDS);
        } finally {
            releaseOldSessionClose.countDown();
            executor.shutdownNow();
        }

        verify(replacementSession).sendMessage(any(TextMessage.class));
        verify(originalSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void concurrentOldUnregisterDoesNotRemoveTheReplacement() throws Exception {
        WebSocketSession originalSession = authenticatedSession();
        WebSocketSession replacementSession = authenticatedSession();
        connectionRegistry.register(DEVICE_ID, originalSession);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            java.util.concurrent.Future<?> oldUnregister = executor.submit(() -> {
                await(start);
                connectionRegistry.unregister(DEVICE_ID, originalSession);
            });
            java.util.concurrent.Future<?> replacementRegister = executor.submit(() -> {
                await(start);
                connectionRegistry.register(DEVICE_ID, replacementSession);
            });
            start.countDown();
            oldUnregister.get(5, TimeUnit.SECONDS);
            replacementRegister.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(connectionRegistry.sessionStateCount()).isEqualTo(1);
        assertThat(commandGateway.stopMotion(DEVICE_ID)).isTrue();
        verify(replacementSession).sendMessage(any(TextMessage.class));
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent replacement start was not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent replacement was interrupted", exception);
        }
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
