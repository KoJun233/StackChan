package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.notification.InteractiveNotificationService;
import com.kj.stackchan.notification.NotificationResponseAction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        path = "/api/v1/notification-integrations/notifications",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class InteractiveNotificationController {
    private final InteractiveNotificationService notificationService;

    public InteractiveNotificationController(InteractiveNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping(path = "/{notificationId}:respond", consumes = MediaType.APPLICATION_JSON_VALUE)
    public InteractiveNotificationService.ResponseSnapshot respond(
            @PathVariable UUID notificationId,
            @Valid @RequestBody NotificationResponseRequest request
    ) {
        return notificationService.respondAdmin(notificationId, request.action(), request.snoozeMinutes());
    }

    public record NotificationResponseRequest(
            @NotNull NotificationResponseAction action,
            Integer snoozeMinutes
    ) { }
}
