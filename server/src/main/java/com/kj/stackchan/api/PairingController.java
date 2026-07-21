package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.device.PairingCodeEntity;
import com.kj.stackchan.device.PairingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping(path = "/api/v1/pairing", produces = MediaType.APPLICATION_JSON_VALUE)
public class PairingController {

    private final PairingService pairingService;
    private final DeviceTokenService deviceTokenService;

    public PairingController(PairingService pairingService, DeviceTokenService deviceTokenService) {
        this.pairingService = pairingService;
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping(path = "/codes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PairingCodeResponse createCode(@Valid @RequestBody CreatePairingCodeRequest request) {
        PairingCodeEntity pairingCode = pairingService.createCode(request.createdBy());
        return new PairingCodeResponse(pairingCode.getValue(), pairingCode.getExpiresAt());
    }

    @PostMapping(path = "/claim", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PairingClaimResponse claim(@Valid @RequestBody PairingClaimRequest request) {
        PairingService.PairingClaim claim = pairingService.claim(
                        request.pairingCode(),
                        request.hardwareId(),
                        request.firmwareVersion()
                )
                .orElseThrow(PairingCodeUnavailableException::new);
        DeviceEntity device = claim.device();
        UUID deviceId = device.getId();
        DeviceTokenService.IssuedDeviceToken accessToken = deviceTokenService.issue(device);
        return new PairingClaimResponse(
                deviceId,
                accessToken.value(),
                accessToken.expiresAt(),
                claim.refreshToken(),
                "/api/v1/devices/token:refresh",
                "/api/v1/ws/device"
        );
    }

    public record CreatePairingCodeRequest(@NotBlank @Size(max = 80) String createdBy) {
    }

    public record PairingClaimRequest(
            @NotBlank @Size(max = 12) String pairingCode,
            @NotBlank @Size(max = 64) String hardwareId,
            @NotBlank @Size(max = 32) String firmwareVersion
    ) {
    }

    public record PairingCodeResponse(String value, Instant expiresAt) {
    }

    public record PairingClaimResponse(
            UUID deviceId,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            String refreshUrl,
            String wsUrl
    ) {
    }
}
