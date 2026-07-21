package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCredentialService;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceCredentialController {

    private static final String DEVICE_WEBSOCKET_URL = "/api/v1/ws/device";

    private final DeviceCredentialService credentialService;
    private final DeviceTokenService tokenService;

    public DeviceCredentialController(DeviceCredentialService credentialService, DeviceTokenService tokenService) {
        this.credentialService = credentialService;
        this.tokenService = tokenService;
    }

    @PostMapping(
            path = "/api/v1/devices/token:refresh",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public DeviceAccessResponse refresh(@Valid @RequestBody RefreshDeviceTokenRequest request) {
        DeviceEntity device = credentialService.authenticateRefresh(request.deviceId(), request.refreshToken());
        DeviceTokenService.IssuedDeviceToken token = tokenService.issue(device);
        return new DeviceAccessResponse(token.value(), token.expiresAt(), DEVICE_WEBSOCKET_URL);
    }

    public record RefreshDeviceTokenRequest(@NotNull UUID deviceId, @NotBlank String refreshToken) {
    }

    public record DeviceAccessResponse(String accessToken, Instant accessTokenExpiresAt, String wsUrl) {
    }

}
