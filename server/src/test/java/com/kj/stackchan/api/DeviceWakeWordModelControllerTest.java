package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.wakeword.WakeWordModelJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceWakeWordModelController.class)
@Import({SecurityConfiguration.class, ObjectMapper.class})
class DeviceWakeWordModelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceHttpAuthenticator authenticator;

    @MockitoBean
    private WakeWordModelJobService jobService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void returnsOnlyTheArtifactOwnedByTheAuthenticatedDevice() throws Exception {
        UUID jobId = UUID.fromString("111e8400-e29b-41d4-a716-446655440000");
        UUID deviceId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        byte[] artifact = {1, 2, 3, 4};
        when(authenticator.authenticate(any())).thenReturn(new DeviceTokenService.DeviceToken(
                deviceId,
                3,
                Instant.parse("2026-07-24T00:00:00Z")
        ));
        when(jobService.artifact(jobId, deviceId)).thenReturn(artifact);

        mockMvc.perform(get("/api/v1/device/wake-models/{jobId}/artifact", jobId)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.stackchan.wake-model"))
                .andExpect(header().longValue("Content-Length", artifact.length))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes(artifact));

        verify(jobService).artifact(jobId, deviceId);
    }
}
