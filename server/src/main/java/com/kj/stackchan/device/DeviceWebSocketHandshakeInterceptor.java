package com.kj.stackchan.device;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String DEVICE_ID_ATTRIBUTE = DeviceWebSocketHandshakeInterceptor.class.getName() + ".deviceId";
    public static final String CREDENTIAL_VERSION_ATTRIBUTE =
            DeviceWebSocketHandshakeInterceptor.class.getName() + ".credentialVersion";
    public static final String TOKEN_EXPIRES_AT_ATTRIBUTE =
            DeviceWebSocketHandshakeInterceptor.class.getName() + ".tokenExpiresAt";

    private final DeviceTokenService deviceTokenService;

    public DeviceWebSocketHandshakeInterceptor(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes
    ) {
        List<String> authorizationHeaders = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String authorization = authorizationHeaders.getFirst();
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String accessToken = authorization.substring("Bearer ".length());
        if (accessToken.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            DeviceTokenService.DeviceToken deviceToken = deviceTokenService.verify(accessToken);
            attributes.put(DEVICE_ID_ATTRIBUTE, deviceToken.deviceId());
            attributes.put(CREDENTIAL_VERSION_ATTRIBUTE, deviceToken.credentialVersion());
            attributes.put(TOKEN_EXPIRES_AT_ATTRIBUTE, deviceToken.expiresAt());
            return true;
        } catch (InvalidDeviceTokenException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Exception exception
    ) {
    }
}
