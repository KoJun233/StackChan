package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.device.DeviceHttpAuthenticator;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.speech.VoiceTurnEnvelope;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService;
import com.kj.stackchan.speech.VoiceTurnService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceVoiceController.class)
@Import({SecurityConfiguration.class, VoiceTurnEnvelope.class, ObjectMapper.class})
class DeviceVoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceHttpAuthenticator authenticator;

    @MockitoBean
    private VoiceTurnService voiceTurnService;

    @MockitoBean
    private VoiceTurnDiagnosticsService diagnosticsService;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @Test
    void acceptsDeviceBearerWithoutAdminSessionAndReturnsVoiceEnvelope() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        byte[] input = new byte[64];
        when(authenticator.authenticate(org.mockito.ArgumentMatchers.any())).thenReturn(
                new DeviceTokenService.DeviceToken(deviceId, 1, Instant.parse("2026-07-20T00:00:00Z"))
        );
        when(voiceTurnService.handle(deviceId, turnId, input)).thenReturn(
                new VoiceTurnService.VoiceTurnResult("你好", "你好呀", new byte[44])
        );

        mockMvc.perform(post("/api/v1/device/voice/turn")
                        .header("Authorization", "Bearer token")
                        .header(DeviceVoiceController.VOICE_TURN_ID_HEADER, turnId)
                        .contentType("audio/wav")
                        .content(input))
                .andExpect(status().isOk())
                .andExpect(content().contentType(DeviceVoiceController.VOICE_TURN_MEDIA_TYPE))
                .andExpect(result -> assertThat(result.getResponse().getHeader(
                        DeviceVoiceController.VOICE_TURN_ID_HEADER
                )).isEqualTo(turnId.toString()))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .startsWith(VoiceTurnEnvelope.MAGIC));
        verify(voiceTurnService).handle(deviceId, turnId, input);
    }
}
