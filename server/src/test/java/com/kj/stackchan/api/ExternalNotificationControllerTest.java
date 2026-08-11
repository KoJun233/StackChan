package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.notification.ExternalNotificationService;
import com.kj.stackchan.notification.NotificationIntegrationPrincipal;
import com.kj.stackchan.notification.NotificationIntegrationService;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ExternalNotificationController.class, NotificationIntegrationController.class})
@Import(SecurityConfiguration.class)
class ExternalNotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationIntegrationService integrationService;
    @MockitoBean private ExternalNotificationService notificationService;
    @MockitoBean private AdminUserRepository adminUserRepository;

    @Test
    void externalEndpointRequiresDedicatedBearerTokenAndIgnoresAdminSession() throws Exception {
        mockMvc.perform(post("/api/v1/external/notifications")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "task-1")
                        .content("{\"content\":\"完成\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("notification_authentication_failed"));
    }

    @Test
    void bearerTokenCreatesNotificationWithoutAdministratorCsrf() throws Exception {
        NotificationIntegrationPrincipal principal = new NotificationIntegrationPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), "Codex"
        );
        var snapshot = new ExternalNotificationService.PublicNotificationSnapshot(
                UUID.randomUUID(), ReminderStatus.PENDING, 0, null,
                Instant.EPOCH, Instant.EPOCH, Instant.EPOCH.plusSeconds(3600), null
        );
        when(integrationService.authenticate("valid-token")).thenReturn(principal);
        when(notificationService.create(principal, "task-1", "完成", null))
                .thenReturn(new ExternalNotificationService.CreateResult(snapshot, false));

        mockMvc.perform(post("/api/v1/external/notifications")
                        .header("Authorization", "Bearer valid-token")
                        .header("Idempotency-Key", "task-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"完成\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void integrationTokenCannotUseAdministratorManagementEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/notification-integrations")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Codex\",\"deviceId\":\"" + UUID.randomUUID() + "\",\"enabled\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorManagementWriteStillRequiresCsrf() throws Exception {
        UUID deviceId = UUID.randomUUID();
        var snapshot = new NotificationIntegrationService.IntegrationSnapshot(
                UUID.randomUUID(), "Codex", deviceId, true, List.of(), Instant.EPOCH, Instant.EPOCH
        );
        when(integrationService.create(any())).thenReturn(snapshot);
        String body = "{\"name\":\"Codex\",\"deviceId\":\"" + deviceId + "\",\"enabled\":true}";

        mockMvc.perform(post("/api/v1/notification-integrations")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/notification-integrations")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Codex"));
    }

    @Test
    void administratorDeletesIntegrationAndQueueItemWithCsrf() throws Exception {
        UUID integrationId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/notification-integrations/{id}", integrationId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/notification-integrations/{id}", integrationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/notification-integrations/notifications/{id}", notificationId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(integrationService).delete(integrationId);
        verify(notificationService).deleteAdmin(notificationId);
    }
}
