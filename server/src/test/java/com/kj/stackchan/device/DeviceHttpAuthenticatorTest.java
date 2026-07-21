package com.kj.stackchan.device;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceHttpAuthenticatorTest {

    @Mock
    private DeviceTokenService deviceTokenService;

    @Test
    void verifiesTheOnlyBearerHeader() {
        UUID deviceId = UUID.randomUUID();
        DeviceTokenService.DeviceToken token = new DeviceTokenService.DeviceToken(
                deviceId, 2, Instant.parse("2026-07-20T00:00:00Z")
        );
        when(deviceTokenService.verify("token-value")).thenReturn(token);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-value");

        assertThat(new DeviceHttpAuthenticator(deviceTokenService).authenticate(request)).isEqualTo(token);
        verify(deviceTokenService).verify("token-value");
    }

    @Test
    void rejectsDuplicateAuthorizationHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer first");
        request.addHeader("Authorization", "Bearer second");

        assertThatThrownBy(() -> new DeviceHttpAuthenticator(deviceTokenService).authenticate(request))
                .isInstanceOf(InvalidDeviceTokenException.class);
    }
}
