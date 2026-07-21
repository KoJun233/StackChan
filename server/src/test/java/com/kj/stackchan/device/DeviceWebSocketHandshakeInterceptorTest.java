package com.kj.stackchan.device;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceWebSocketHandshakeInterceptorTest {

    private static final UUID DEVICE_ID = UUID.fromString("a88e4a94-8536-4fa1-91ed-8681b597429d");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-02T00:00:00Z");
    private static final String CREDENTIAL_VERSION_ATTRIBUTE =
            DeviceWebSocketHandshakeInterceptor.class.getName() + ".credentialVersion";
    private static final String TOKEN_EXPIRES_AT_ATTRIBUTE =
            DeviceWebSocketHandshakeInterceptor.class.getName() + ".tokenExpiresAt";

    private final DeviceTokenService deviceTokenService = mock(DeviceTokenService.class);
    private final DeviceWebSocketHandshakeInterceptor interceptor = new DeviceWebSocketHandshakeInterceptor(deviceTokenService);

    @Test
    void acceptsOnlyAnAuthorizationBearerToken() throws Exception {
        when(deviceTokenService.verify("valid-token"))
                .thenReturn(new DeviceTokenService.DeviceToken(DEVICE_ID, 3L, EXPIRES_AT));
        Map<String, Object> attributes = new HashMap<>();
        MockHttpServletRequest servletRequest = servletRequest("/api/v1/ws/device?access_token=query-token");
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                response().serverResponse(),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(accepted).isTrue();
        assertThat(attributes).containsEntry(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, DEVICE_ID);
        assertThat(attributes).containsEntry(CREDENTIAL_VERSION_ATTRIBUTE, 3L);
        assertThat(attributes).containsEntry(TOKEN_EXPIRES_AT_ATTRIBUTE, EXPIRES_AT);
    }

    @Test
    void rejectsQueryOnlyAndMalformedAuthorizationCredentials() throws Exception {
        MockHttpServletRequest queryOnly = servletRequest("/api/v1/ws/device?access_token=valid-token");
        MockHttpServletRequest basic = servletRequest("/api/v1/ws/device");
        basic.addHeader(HttpHeaders.AUTHORIZATION, "Basic placeholder");
        MockHttpServletRequest blankBearer = servletRequest("/api/v1/ws/device");
        blankBearer.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ");
        MockHttpServletRequest duplicate = servletRequest("/api/v1/ws/device");
        duplicate.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");
        duplicate.addHeader(HttpHeaders.AUTHORIZATION, "Bearer second-token");

        assertUnauthorized(queryOnly);
        assertUnauthorized(basic);
        assertUnauthorized(blankBearer);
        assertUnauthorized(duplicate);
    }

    @Test
    void rejectsAnInvalidBearerToken() throws Exception {
        when(deviceTokenService.verify("invalid-token")).thenThrow(new InvalidDeviceTokenException("invalid"));
        MockHttpServletRequest servletRequest = servletRequest("/api/v1/ws/device");
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");

        assertUnauthorized(servletRequest);
    }

    private void assertUnauthorized(MockHttpServletRequest servletRequest) throws Exception {
        HandshakeResponse handshakeResponse = response();
        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                handshakeResponse.serverResponse(),
                mock(WebSocketHandler.class),
                new HashMap<>()
        );

        assertThat(accepted).isFalse();
        assertThat(handshakeResponse.servletResponse().getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    private MockHttpServletRequest servletRequest(String path) {
        int queryStart = path.indexOf('?');
        String requestPath = queryStart < 0 ? path : path.substring(0, queryStart);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", requestPath);
        if (queryStart >= 0) {
            request.setQueryString(path.substring(queryStart + 1));
        }
        return request;
    }

    private HandshakeResponse response() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        return new HandshakeResponse(new ServletServerHttpResponse(servletResponse), servletResponse);
    }

    private record HandshakeResponse(ServerHttpResponse serverResponse, MockHttpServletResponse servletResponse) {
    }
}
