package com.kj.stackchan.device;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceConnectionRegistryConcurrencyTest {

    private static final UUID DEVICE_A = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");
    private static final UUID DEVICE_B = UUID.fromString("b88e4a94-8536-4fa1-91ed-8681b597429d");

    @Test
    void blockedHeartbeatForOneDeviceDoesNotDelayStopMotionForAnother() throws Exception {
        DeviceConnectionRegistry connectionRegistry = new DeviceConnectionRegistry(new ObjectMapper());
        DeviceEventService deviceEventService = mock(DeviceEventService.class);
        DeviceWebSocketHandler handler = new DeviceWebSocketHandler(
                connectionRegistry,
                deviceEventService,
                new ObjectMapper()
        );
        OfflineDeviceCommandGateway commandGateway = new OfflineDeviceCommandGateway(connectionRegistry);
        WebSocketSession deviceASession = authenticatedSession(DEVICE_A);
        WebSocketSession deviceBSession = authenticatedSession(DEVICE_B);
        CountDownLatch heartbeatEntered = new CountDownLatch(1);
        CountDownLatch releaseHeartbeat = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        doAnswer(invocation -> {
            heartbeatEntered.countDown();
            assertThat(releaseHeartbeat.await(5, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(deviceEventService).recordHeartbeat(DEVICE_A, "motion_disabled", null);
        handler.afterConnectionEstablished(deviceASession);
        handler.afterConnectionEstablished(deviceBSession);

        try {
            Future<?> heartbeat = executor.submit(() -> {
                try {
                    handler.handleTextMessage(deviceASession, new TextMessage("""
                            {"type":"heartbeat","sequence":1,"battery_percent":80,"rssi":-54,"safety_state":"motion_disabled"}
                            """));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertThat(heartbeatEntered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> stopMotion = executor.submit(() -> commandGateway.stopMotion(DEVICE_B));
            assertThat(stopMotion.get(1, TimeUnit.SECONDS)).isTrue();
            verify(deviceBSession).sendMessage(org.mockito.ArgumentMatchers.any(TextMessage.class));

            releaseHeartbeat.countDown();
            heartbeat.get(5, TimeUnit.SECONDS);
        } finally {
            releaseHeartbeat.countDown();
            executor.shutdownNow();
        }
    }

    private WebSocketSession authenticatedSession(UUID deviceId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, deviceId);
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
