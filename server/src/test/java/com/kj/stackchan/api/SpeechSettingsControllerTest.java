package com.kj.stackchan.api;

import java.time.Instant;

import com.kj.stackchan.device.DeviceVoiceSettingsCoordinator;
import com.kj.stackchan.health.ProviderHealthRegistry;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.speech.SpeechAccessMode;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import com.kj.stackchan.speech.SpeechProviderType;
import com.kj.stackchan.speech.SpeechSettingsService;
import com.kj.stackchan.speech.VoiceWakeSensitivity;
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
import static org.springframework.test.json.JsonCompareMode.STRICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SpeechSettingsController.class)
@Import(SecurityConfiguration.class)
class SpeechSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpeechSettingsService settingsService;

    @MockitoBean
    private SpeechRuntimeClient speechRuntimeClient;

    @MockitoBean
    private DeviceVoiceSettingsCoordinator deviceVoiceSettingsCoordinator;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private ProviderHealthRegistry providerHealthRegistry;

    @Test
    void returnsSettingsWithoutTheApiKey() throws Exception {
        when(settingsService.getSettings()).thenReturn(new SpeechSettingsService.SpeechSettingsSnapshot(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1",
                "",
                "whisper-1",
                SpeechAccessMode.NON_REALTIME,
                "tts-1",
                SpeechAccessMode.NON_REALTIME,
                "alloy",
                true,
                Instant.parse("2026-07-19T10:30:00Z")
        ));

        mockMvc.perform(get("/api/v1/settings/speech").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"providerType":"OPENAI_COMPATIBLE","baseUrl":"https://speech.example.com/v1","workspaceId":"","asrModel":"whisper-1","asrMode":"NON_REALTIME","ttsModel":"tts-1","ttsMode":"NON_REALTIME","ttsVoice":"alloy","wakeSensitivity":"SENSITIVE","speechStartThreshold":350,"speechSilenceThreshold":200,"apiKeyConfigured":true,"updatedAt":"2026-07-19T10:30:00Z"}
                        """, STRICT));
    }

    @Test
    void savesSpeechSettings() throws Exception {
        when(settingsService.saveSettings(any())).thenReturn(new SpeechSettingsService.SpeechSettingsSnapshot(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1", "", "whisper-1", SpeechAccessMode.NON_REALTIME,
                "tts-1", SpeechAccessMode.NON_REALTIME, "alloy", true,
                Instant.parse("2026-07-19T10:30:00Z")
        ));

        mockMvc.perform(put("/api/v1/settings/speech")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"providerType":"OPENAI_COMPATIBLE","baseUrl":"https://speech.example.com/v1","workspaceId":"","asrModel":"whisper-1","asrMode":"NON_REALTIME","ttsModel":"tts-1","ttsMode":"NON_REALTIME","ttsVoice":"alloy","wakeSensitivity":"SENSITIVE","speechStartThreshold":350,"speechSilenceThreshold":200,"apiKey":"secret"}
                                """))
                .andExpect(status().isOk());

        verify(settingsService).saveSettings(new SpeechSettingsService.UpdateSpeechSettingsCommand(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1", "", "whisper-1", SpeechAccessMode.NON_REALTIME,
                "tts-1", SpeechAccessMode.NON_REALTIME, "alloy", VoiceWakeSensitivity.SENSITIVE,
                350, 200, "secret"
        ));
        verify(deviceVoiceSettingsCoordinator).broadcast(any());
    }

    @Test
    void testsSpeechWithoutReturningAudioOrSecrets() throws Exception {
        mockMvc.perform(post("/api/v1/settings/speech/test")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"ok":true,"message":"测试音频已成功生成并识别。"}
                        """, STRICT));

        verify(speechRuntimeClient).testConnection();
    }
}
