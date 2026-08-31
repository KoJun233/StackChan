package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.task.PersonalTaskStatus;
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

@WebMvcTest(PersonalTaskController.class)
@Import(SecurityConfiguration.class)
class PersonalTaskControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private PersonalTaskService taskService;
    @MockitoBean private AdminUserRepository adminUserRepository;

    @Test
    void listsFilteredTasksForAdministrator() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(taskService.list("材料", PersonalTaskStatus.OPEN, PersonalTaskPriority.HIGH, roleId, 0, 20))
                .thenReturn(new PersonalTaskService.TaskPage(List.of(snapshot(deviceId, roleId)), 1));

        mockMvc.perform(get("/api/v1/personal-tasks")
                        .with(user("admin").roles("ADMIN"))
                        .param("query", "材料").param("status", "OPEN").param("priority", "HIGH")
                        .param("roleId", roleId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.list[0].title").value("整理会议材料"));
    }

    @Test
    void createsRoleScopedTaskWithOptionalDueTime() throws Exception {
        UUID roleId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        when(taskService.create(any(UUID.class), any())).thenReturn(snapshot(deviceId, roleId));

        mockMvc.perform(post("/api/v1/personal-tasks")
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"%s","roleId":"%s","title":"整理会议材料","notes":"周一使用","priority":"HIGH","dueAt":"2026-08-31T01:00:00Z","zoneId":"Asia/Shanghai"}
                                """.formatted(deviceId, roleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));

        verify(taskService).create(roleId, new PersonalTaskService.TaskCommand(
                deviceId, "整理会议材料", "周一使用", PersonalTaskPriority.HIGH,
                Instant.parse("2026-08-31T01:00:00Z"), "Asia/Shanghai"));
    }

    private PersonalTaskService.TaskSnapshot snapshot(UUID deviceId, UUID roleId) {
        return new PersonalTaskService.TaskSnapshot(UUID.randomUUID(), deviceId, roleId, "整理会议材料", "周一使用",
                PersonalTaskPriority.HIGH, PersonalTaskStatus.OPEN, Instant.parse("2026-08-31T01:00:00Z"),
                "Asia/Shanghai", UUID.randomUUID(), null, Instant.EPOCH, Instant.EPOCH);
    }
}
