package com.kj.stackchan.notification;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationMcpToolsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exposesOnlyTwoToolsAndDelegatesToSharedService() {
        ExternalNotificationService service = mock(ExternalNotificationService.class);
        NotificationMcpTools tools = new NotificationMcpTools(service);
        UUID integrationId = UUID.randomUUID();
        NotificationIntegrationPrincipal principal = new NotificationIntegrationPrincipal(
                integrationId, UUID.randomUUID(), "Codex"
        );
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of())
        );
        var snapshot = new ExternalNotificationService.PublicNotificationSnapshot(
                UUID.randomUUID(), ReminderStatus.PENDING, 0, null,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), null
        );
        when(service.create(principal, "task-1", "完成", null))
                .thenReturn(new ExternalNotificationService.CreateResult(snapshot, false));
        when(service.get(principal, snapshot.id())).thenReturn(snapshot);

        assertThat(ToolCallbacks.from(tools)).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder("push_notification", "get_notification_status");
        assertThat(tools.pushNotification("完成", "task-1", null)).isEqualTo(snapshot);
        assertThat(tools.getNotificationStatus(snapshot.id().toString())).isEqualTo(snapshot);
        verify(service).create(principal, "task-1", "完成", null);
        verify(service).get(principal, snapshot.id());
    }
}
