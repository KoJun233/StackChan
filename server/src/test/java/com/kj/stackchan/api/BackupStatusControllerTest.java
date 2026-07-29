package com.kj.stackchan.api;

import java.time.Instant;

import com.kj.stackchan.backup.BackupStatusService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BackupStatusController.class)
@Import(SecurityConfiguration.class)
class BackupStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BackupStatusService backupStatusService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void requiresAuthenticationAndReturnsOnlyTheSafeStatusProjection() throws Exception {
        when(backupStatusService.status()).thenReturn(new BackupStatusService.BackupStatus(
                true,
                Instant.parse("2026-07-29T12:00:00Z"),
                Instant.parse("2026-07-29T12:00:00Z"),
                null,
                null,
                Instant.parse("2026-07-29T12:01:00Z"),
                true,
                null,
                1,
                1,
                7,
                4,
                4096
        ));

        mockMvc.perform(get("/api/v1/personal-data/backups/status"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/personal-data/backups/status")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRestoreVerificationSuccessful").value(true))
                .andExpect(jsonPath("$.dailyRetention").value(7))
                .andExpect(jsonPath("$.weeklyRetention").value(4))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("path"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sha256"))));
    }
}
