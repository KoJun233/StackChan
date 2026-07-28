package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.expression.ExpressionPackService;
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
@RequestMapping("/api/v1/device/expression-packs")
public class DeviceExpressionPackController {

    private static final MediaType PACK = new MediaType("application", "vnd.stackchan.expression-pack");
    private final DeviceHttpAuthenticator authenticator;
    private final ExpressionPackService service;

    public DeviceExpressionPackController(DeviceHttpAuthenticator authenticator, ExpressionPackService service) {
        this.authenticator = authenticator;
        this.service = service;
    }

    @GetMapping(path = "/{packId}/artifact", produces = "application/vnd.stackchan.expression-pack")
    public ResponseEntity<byte[]> artifact(HttpServletRequest request, @PathVariable UUID packId) {
        DeviceTokenService.DeviceToken token = authenticator.authenticate(request);
        byte[] artifact = service.artifact(packId, token.deviceId());
        return ResponseEntity.ok()
                .contentType(PACK)
                .contentLength(artifact.length)
                .cacheControl(CacheControl.noStore())
                .body(artifact);
    }
}
