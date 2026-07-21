package com.kj.stackchan.config;

import com.kj.stackchan.device.DeviceWebSocketHandler;
import com.kj.stackchan.device.DeviceWebSocketHandshakeInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceWebSocketConfiguration implements WebSocketConfigurer {

    private final DeviceWebSocketHandler deviceWebSocketHandler;
    private final DeviceWebSocketHandshakeInterceptor deviceWebSocketHandshakeInterceptor;

    public DeviceWebSocketConfiguration(
            DeviceWebSocketHandler deviceWebSocketHandler,
            DeviceWebSocketHandshakeInterceptor deviceWebSocketHandshakeInterceptor
    ) {
        this.deviceWebSocketHandler = deviceWebSocketHandler;
        this.deviceWebSocketHandshakeInterceptor = deviceWebSocketHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(deviceWebSocketHandler, "/api/v1/ws/device")
                .addInterceptors(deviceWebSocketHandshakeInterceptor);
    }
}
