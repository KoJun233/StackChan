package com.kj.stackchan.api;

import java.util.UUID;
import java.util.Set;

import com.kj.stackchan.notification.ExternalNotificationService;
import com.kj.stackchan.notification.NotificationIntegrationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/external/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
public class ExternalNotificationController {

    private final ExternalNotificationService notificationService;

    public ExternalNotificationController(ExternalNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ExternalNotificationService.PublicNotificationSnapshot> create(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExternalNotificationRequest request
    ) {
        var result = request.responseActions() == null || request.responseActions().isEmpty()
                ? notificationService.create(
                        principal(authentication), idempotencyKey, request.content(), request.expiresInSeconds())
                : notificationService.create(
                        principal(authentication), idempotencyKey, request.content(), request.expiresInSeconds(),
                        request.responseActions());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.notification());
    }

    @GetMapping("/{id}")
    public ExternalNotificationService.PublicNotificationSnapshot get(
            Authentication authentication,
            @PathVariable UUID id
    ) {
        return notificationService.get(principal(authentication), id);
    }

    private NotificationIntegrationPrincipal principal(Authentication authentication) {
        return (NotificationIntegrationPrincipal) authentication.getPrincipal();
    }

    public record ExternalNotificationRequest(
            @NotBlank @Size(max = 500) String content,
            @Min(60) @Max(86400) Integer expiresInSeconds,
            @Size(max = 3) Set<com.kj.stackchan.notification.NotificationResponseAction> responseActions
    ) { }
}
