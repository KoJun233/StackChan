package com.kj.stackchan.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import com.kj.stackchan.speech.VoiceTurnStage;
import com.kj.stackchan.speech.VoiceTurnStageSource;
import com.kj.stackchan.speech.VoiceTurnStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceVoiceDiagnosticsController.class)
@Import(SecurityConfiguration.class)
class DeviceVoiceDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VoiceTurnDiagnosticsService diagnosticsService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void returnsMetadataWithoutAudioTranscriptOrReply() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-07-26T00:00:00Z");
        when(diagnosticsService.recent(deviceId, 10)).thenReturn(List.of(
                new VoiceTurnDiagnosticsService.VoiceTurnSnapshot(
                        turnId,
                        VoiceTurnStatus.COMPLETED,
                        null,
                        startedAt,
                        startedAt.plusSeconds(2),
                        List.of(new VoiceTurnDiagnosticsService.VoiceTurnEventSnapshot(
                                VoiceTurnStage.LISTENING_RESUMED,
                                VoiceTurnStageSource.DEVICE,
                                startedAt.plusSeconds(2),
                                2000,
                                null
                        ))
                )
        ));

        mockMvc.perform(get("/api/v1/devices/{deviceId}/voice-turns", deviceId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.turns[0].turnId").value(turnId.toString()))
                .andExpect(jsonPath("$.turns[0].events[0].elapsedMs").value(2000))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("transcript")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("reply")
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("audio")
                )));
    }
}
