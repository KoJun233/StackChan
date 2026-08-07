package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.firmwareupdate.FirmwareReleaseEntity;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FirmwareUpdateController.class)
@Import(SecurityConfiguration.class)
class FirmwareUpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FirmwareUpdateService service;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void requiresAdministratorAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/firmware/releases"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAJobWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/v1/firmware/jobs")
                        .with(user("admin").roles("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"deviceId":"550e8400-e29b-41d4-a716-446655440000","releaseId":"650e8400-e29b-41d4-a716-446655440000","confirmedCurrentVersion":"old"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsTheSafeFirmwareErrorForMalformedJobs() throws Exception {
        mockMvc.perform(post("/api/v1/firmware/jobs")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(content().json("""
                        {"code":"invalid_firmware_update","message":"固件必须是版本匹配的 StackChan 应用镜像，目标设备需在线、已完成 OTA 引导并通过当前版本确认。"}
                        """, STRICT));
    }

    @Test
    void importsAnApplicationArtifactWithoutReturningItsBytes() throws Exception {
        UUID releaseId = UUID.fromString("650e8400-e29b-41d4-a716-446655440000");
        FirmwareReleaseEntity release = mock(FirmwareReleaseEntity.class);
        when(release.getId()).thenReturn(releaseId);
        when(release.getVersion()).thenReturn("abc1234");
        when(release.getProjectName()).thenReturn("stackchan_firmware");
        when(release.getArtifactSha256()).thenReturn("0".repeat(64));
        when(release.getArtifactSize()).thenReturn(256);
        when(release.getCreatedAt()).thenReturn(Instant.parse("2026-08-07T12:00:00Z"));
        when(service.importRelease(any(byte[].class), eq("abc1234"))).thenReturn(release);

        MockMultipartFile artifact = new MockMultipartFile(
                "artifact", "stackchan_firmware.bin", "application/octet-stream", new byte[256]
        );
        mockMvc.perform(multipart("/api/v1/firmware/releases")
                        .file(artifact)
                        .param("version", "abc1234")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(content().json("""
                        {"id":"650e8400-e29b-41d4-a716-446655440000","version":"abc1234","projectName":"stackchan_firmware","artifactSha256":"0000000000000000000000000000000000000000000000000000000000000000","artifactSize":256,"createdAt":"2026-08-07T12:00:00Z"}
                        """, STRICT));
    }
}
