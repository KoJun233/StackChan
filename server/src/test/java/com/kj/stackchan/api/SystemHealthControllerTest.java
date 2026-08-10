package com.kj.stackchan.api;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.kj.stackchan.backup.BackupStatusService;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.expression.DeviceExpressionPackRepository;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateJobRepository;
import com.kj.stackchan.health.ProviderHealthRegistry;
import com.kj.stackchan.llm.LlmSettingsService;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import com.kj.stackchan.speech.SpeechAccessMode;
import com.kj.stackchan.speech.SpeechProviderType;
import com.kj.stackchan.speech.SpeechSettingsService;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.wakeword.WakeWordModelJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemHealthController.class)
@Import(SecurityConfiguration.class)
class SystemHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private DeviceRepository deviceRepository;
    @MockitoBean private DeviceCommandGateway commandGateway;
    @MockitoBean private VoiceTurnRepository voiceTurnRepository;
    @MockitoBean private FirmwareUpdateJobRepository firmwareJobRepository;
    @MockitoBean private WakeWordModelJobRepository wakeJobRepository;
    @MockitoBean private DeviceExpressionPackRepository expressionRepository;
    @MockitoBean private ReminderRepository reminderRepository;
    @MockitoBean private BackupStatusService backupStatusService;
    @MockitoBean private LlmSettingsService llmSettingsService;
    @MockitoBean private SpeechSettingsService speechSettingsService;
    @MockitoBean private ProviderHealthRegistry providerHealthRegistry;
    @MockitoBean private AdminUserRepository adminUserRepository;
    @MockitoBean private Clock clock;

    @Test
    void returnsOnlySafeOperationalMetadataToAnAdministrator() throws Exception {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        when(clock.instant()).thenReturn(now);
        when(deviceRepository.findAll()).thenReturn(List.of());
        when(llmSettingsService.getSettings()).thenReturn(new LlmSettingsService.LlmSettingsSnapshot(
                "https://secret-provider.example/v1", "secret-model", "secret prompt", true, now
        ));
        when(speechSettingsService.getSettings()).thenReturn(new SpeechSettingsService.SpeechSettingsSnapshot(
                SpeechProviderType.OPENAI_COMPATIBLE, "https://secret-speech.example/v1", "workspace",
                "asr", SpeechAccessMode.NON_REALTIME, "tts", SpeechAccessMode.NON_REALTIME,
                "voice", true, now
        ));
        when(providerHealthRegistry.status(anyString(), anyBoolean())).thenReturn(
                new ProviderHealthRegistry.ProviderConnectivity("UNKNOWN", null, null)
        );
        when(backupStatusService.status()).thenReturn(new BackupStatusService.BackupStatus(
                false, null, null, null, null, null, null, null, 0, 0, 7, 4, 0
        ));

        mockMvc.perform(get("/api/v1/system/health").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedAt").value("2026-08-07T12:00:00Z"))
                .andExpect(jsonPath("$.devices").isEmpty())
                .andExpect(jsonPath("$.providers[0].connectivity.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.providers[0].baseUrl").doesNotExist())
                .andExpect(jsonPath("$.providers[0].model").doesNotExist())
                .andExpect(jsonPath("$.providers[0].apiKey").doesNotExist())
                .andExpect(jsonPath("$.recentSafeErrors").isEmpty());
    }
}
