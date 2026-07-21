package com.kj.stackchan.device;

import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class DeviceHttpAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final DeviceTokenService deviceTokenService;

    public DeviceHttpAuthenticator(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    public DeviceTokenService.DeviceToken authenticate(HttpServletRequest request) {
        List<String> values = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (values.size() != 1) {
            throw new InvalidDeviceTokenException("Exactly one device Authorization header is required");
        }
        String authorization = values.getFirst();
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new InvalidDeviceTokenException("Device Bearer token is required");
        }
        String token = authorization.substring(BEARER_PREFIX.length());
        if (token.isBlank()) {
            throw new InvalidDeviceTokenException("Device Bearer token is required");
        }
        return deviceTokenService.verify(token);
    }
}
