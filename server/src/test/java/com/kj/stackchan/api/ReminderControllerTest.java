package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReminderController.class)
@Import(SecurityConfiguration.class)
class ReminderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReminderService reminderService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void listsReminderPageForAdministrator() throws Exception {
        UUID reminderId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(reminderService.list("外卖", ReminderStatus.PENDING, 0, 20)).thenReturn(
                new ReminderService.ReminderPage(List.of(new ReminderService.ReminderSnapshot(
                        reminderId, deviceId, "去拿外卖", Instant.parse("2026-07-19T10:30:00Z"),
                        "Asia/Shanghai", ReminderStatus.PENDING, 0, null,
                        Instant.parse("2026-07-19T10:00:00Z"), Instant.parse("2026-07-19T10:00:00Z")
                )), 1)
        );

        mockMvc.perform(get("/api/v1/reminders")
                        .with(user("admin").roles("ADMIN"))
                        .param("content", "外卖")
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.list[0].content").value("去拿外卖"));
    }

    @Test
    void createsReminderWithExplicitTimeZone() throws Exception {
        UUID deviceId = UUID.randomUUID();
        when(reminderService.create(any())).thenAnswer(invocation -> {
            ReminderService.ReminderCommand command = invocation.getArgument(0);
            return new ReminderService.ReminderSnapshot(
                    UUID.randomUUID(), command.deviceId(), command.content(), command.scheduledAt(), command.zoneId(),
                    ReminderStatus.PENDING, 0, null, Instant.EPOCH, Instant.EPOCH
            );
        });

        mockMvc.perform(post("/api/v1/reminders")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"%s","content":"去拿外卖","scheduledAt":"2026-07-19T10:30:00Z","zoneId":"Asia/Shanghai"}
                                """.formatted(deviceId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(reminderService).create(new ReminderService.ReminderCommand(
                deviceId, "去拿外卖", Instant.parse("2026-07-19T10:30:00Z"), "Asia/Shanghai"
        ));
    }
}
