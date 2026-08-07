package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/device/firmware-updates")
public class DeviceFirmwareUpdateController {

    private static final MediaType FIRMWARE = new MediaType("application", "vnd.stackchan.firmware");

    private final DeviceHttpAuthenticator authenticator;
    private final FirmwareUpdateService service;

    public DeviceFirmwareUpdateController(DeviceHttpAuthenticator authenticator, FirmwareUpdateService service) {
        this.authenticator = authenticator;
        this.service = service;
    }

    @GetMapping(path = "/{jobId}/artifact", produces = "application/vnd.stackchan.firmware")
    public ResponseEntity<byte[]> artifact(HttpServletRequest request, @PathVariable UUID jobId) {
        DeviceTokenService.DeviceToken token = authenticator.authenticate(request);
        byte[] artifact = service.artifact(jobId, token.deviceId());
        return ResponseEntity.ok()
                .contentType(FIRMWARE)
                .contentLength(artifact.length)
                .cacheControl(CacheControl.noStore())
                .body(artifact);
    }
}
