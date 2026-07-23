package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.wakeword.WakeWordModelJobService;
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
@RequestMapping("/api/v1/device/wake-models")
public class DeviceWakeWordModelController {

    private static final MediaType WAKE_MODEL = new MediaType("application", "vnd.stackchan.wake-model");

    private final DeviceHttpAuthenticator authenticator;
    private final WakeWordModelJobService jobService;

    public DeviceWakeWordModelController(
            DeviceHttpAuthenticator authenticator,
            WakeWordModelJobService jobService
    ) {
        this.authenticator = authenticator;
        this.jobService = jobService;
    }

    @GetMapping(path = "/{jobId}/artifact", produces = "application/vnd.stackchan.wake-model")
    public ResponseEntity<byte[]> artifact(HttpServletRequest request, @PathVariable UUID jobId) {
        DeviceTokenService.DeviceToken token = authenticator.authenticate(request);
        byte[] artifact = jobService.artifact(jobId, token.deviceId());
        return ResponseEntity.ok()
                .contentType(WAKE_MODEL)
                .contentLength(artifact.length)
                .cacheControl(CacheControl.noStore())
                .body(artifact);
    }
}
