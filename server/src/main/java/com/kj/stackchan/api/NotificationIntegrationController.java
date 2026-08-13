package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import com.kj.stackchan.notification.ExternalNotificationService;
import com.kj.stackchan.notification.NotificationIntegrationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import com.kj.stackchan.reminder.ReminderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/notification-integrations", produces = MediaType.APPLICATION_JSON_VALUE)
public class NotificationIntegrationController {

    private final NotificationIntegrationService integrationService;
    private final ExternalNotificationService notificationService;

    public NotificationIntegrationController(
            NotificationIntegrationService integrationService,
            ExternalNotificationService notificationService
    ) {
        this.integrationService = integrationService;
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationIntegrationService.IntegrationSnapshot> list() {
        return integrationService.list();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationIntegrationService.IntegrationSnapshot create(@Valid @RequestBody IntegrationRequest request) {
        return integrationService.create(request.toCommand());
    }

    @GetMapping("/{id}")
    public NotificationIntegrationService.IntegrationSnapshot get(@PathVariable UUID id) {
        return integrationService.get(id);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public NotificationIntegrationService.IntegrationSnapshot update(
            @PathVariable UUID id,
            @Valid @RequestBody IntegrationRequest request
    ) {
        return integrationService.update(id, request.toCommand());
    }

    @PostMapping(path = "/{id}/tokens", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationIntegrationService.IssuedToken issueToken(
            @PathVariable UUID id,
            @RequestBody(required = false) TokenRequest request
    ) {
        return integrationService.issueToken(id, request == null ? null : request.expiresAt());
    }

    @DeleteMapping("/{id}/tokens/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeToken(@PathVariable UUID id, @PathVariable UUID tokenId) {
        integrationService.revokeToken(id, tokenId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        integrationService.delete(id);
    }

    @GetMapping("/notifications")
    public ExternalNotificationService.AdminNotificationPage notifications(
            @RequestParam(required = false) UUID integrationId,
            @RequestParam(required = false) ReminderStatus status,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return notificationService.adminList(integrationId, status, from, limit);
    }

    @DeleteMapping("/notifications/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNotification(@PathVariable UUID notificationId) {
        notificationService.deleteAdmin(notificationId);
    }
    @PostMapping(path = "/{id}:test", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ExternalNotificationService.PublicNotificationSnapshot test(
            @PathVariable UUID id,
            @Valid @RequestBody TestNotificationRequest request
    ) {
        return notificationService.createAdminTest(id, request.content(), request.responseActions()).notification();
    }

    public record IntegrationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID deviceId,
            UUID roleId,
            boolean enabled
    ) {
        NotificationIntegrationService.IntegrationCommand toCommand() {
            return new NotificationIntegrationService.IntegrationCommand(name, deviceId, roleId, enabled);
        }
    }

    public record TokenRequest(Instant expiresAt) { }

    public record TestNotificationRequest(
            @NotBlank @Size(max = 500) String content,
            @Size(max = 3) Set<NotificationResponseAction> responseActions
    ) { }

}
