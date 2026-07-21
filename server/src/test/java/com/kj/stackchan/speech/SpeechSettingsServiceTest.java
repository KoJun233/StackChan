package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.kj.stackchan.llm.SecretCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:30:00Z");

    @Mock
    private SpeechProviderSettingsRepository repository;

    @Mock
    private SecretCipher secretCipher;

    @Test
    void encryptsTheApiKeyAndDoesNotExposeIt() {
        when(repository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(secretCipher.encrypt("speech-secret"))
                .thenReturn(new SecretCipher.EncryptedSecret("ciphertext", "iv"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpeechSettingsService.SpeechSettingsSnapshot result = service().saveSettings(
                new SpeechSettingsService.UpdateSpeechSettingsCommand(
                        SpeechProviderType.OPENAI_COMPATIBLE,
                        "https://speech.example.com/v1/",
                        "",
                        "whisper-1",
                        SpeechAccessMode.NON_REALTIME,
                        "tts-1",
                        SpeechAccessMode.NON_REALTIME,
                        "alloy",
                        "speech-secret"
                )
        );

        ArgumentCaptor<SpeechProviderSettingsEntity> captor = ArgumentCaptor.forClass(SpeechProviderSettingsEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://speech.example.com/v1");
        assertThat(captor.getValue().getApiKeyCiphertext()).isEqualTo("ciphertext");
        assertThat(result.apiKeyConfigured()).isTrue();
        assertThat(result.toString()).doesNotContain("speech-secret");
    }

    @Test
    void retainsTheExistingEncryptedKeyWhenTheFieldIsBlank() {
        SpeechProviderSettingsEntity existing = new SpeechProviderSettingsEntity(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://old.example.com/v1", "", "old-asr", SpeechAccessMode.NON_REALTIME,
                "old-tts", SpeechAccessMode.NON_REALTIME, "old-voice",
                "old-cipher", "old-iv", NOW
        );
        when(repository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        service().saveSettings(new SpeechSettingsService.UpdateSpeechSettingsCommand(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "http://192.168.1.10:8000/v1", "", "asr", SpeechAccessMode.NON_REALTIME,
                "tts", SpeechAccessMode.NON_REALTIME, "voice", ""
        ));

        assertThat(existing.getApiKeyCiphertext()).isEqualTo("old-cipher");
        assertThat(existing.getApiKeyIv()).isEqualTo("old-iv");
    }

    @Test
    void requiresAnApiKeyOnFirstSave() {
        when(repository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().saveSettings(new SpeechSettingsService.UpdateSpeechSettingsCommand(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1", "", "asr", SpeechAccessMode.NON_REALTIME,
                "tts", SpeechAccessMode.NON_REALTIME, "voice", ""
        )))
                .isInstanceOf(InvalidSpeechSettingsException.class)
                .hasMessageContaining("首次保存");
    }

    @Test
    void resolvesTheSecretOnlyForInvocation() {
        SpeechProviderSettingsEntity existing = new SpeechProviderSettingsEntity(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1", "", "asr", SpeechAccessMode.NON_REALTIME,
                "tts", SpeechAccessMode.NON_REALTIME, "voice", "cipher", "iv", NOW
        );
        when(repository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.of(existing));
        when(secretCipher.decrypt(new SecretCipher.EncryptedSecret("cipher", "iv"))).thenReturn("secret");

        ResolvedSpeechSettings resolved = service().resolveForInvocation();

        assertThat(resolved.apiKey()).isEqualTo("secret");
        assertThat(resolved.providerType()).isEqualTo(SpeechProviderType.OPENAI_COMPATIBLE);
        assertThat(resolved.asrModel()).isEqualTo("asr");
        assertThat(resolved.asrMode()).isEqualTo(SpeechAccessMode.NON_REALTIME);
        assertThat(resolved.ttsMode()).isEqualTo(SpeechAccessMode.NON_REALTIME);
        assertThat(resolved.ttsVoice()).isEqualTo("voice");
    }

    @Test
    void savesDashScopeWorkspaceWithoutRequiringABaseUrl() {
        when(repository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(secretCipher.encrypt("dashscope-secret"))
                .thenReturn(new SecretCipher.EncryptedSecret("ciphertext", "iv"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpeechSettingsService.SpeechSettingsSnapshot result = service().saveSettings(
                new SpeechSettingsService.UpdateSpeechSettingsCommand(
                        SpeechProviderType.DASHSCOPE,
                        "",
                        "llm-workspace123",
                        "custom-asr-model",
                        SpeechAccessMode.NON_REALTIME,
                        "custom-tts-model",
                        SpeechAccessMode.REALTIME,
                        "longanhuan_v3.6",
                        "dashscope-secret"
                )
        );

        assertThat(result.providerType()).isEqualTo(SpeechProviderType.DASHSCOPE);
        assertThat(result.baseUrl()).isEmpty();
        assertThat(result.workspaceId()).isEqualTo("llm-workspace123");
        assertThat(result.asrModel()).isEqualTo("custom-asr-model");
        assertThat(result.asrMode()).isEqualTo(SpeechAccessMode.NON_REALTIME);
        assertThat(result.ttsModel()).isEqualTo("custom-tts-model");
        assertThat(result.ttsMode()).isEqualTo(SpeechAccessMode.REALTIME);
    }

    @Test
    void rejectsWorkspaceIdsThatCouldAlterTheProviderHost() {
        assertThatThrownBy(() -> service().saveSettings(
                new SpeechSettingsService.UpdateSpeechSettingsCommand(
                        SpeechProviderType.DASHSCOPE,
                        "",
                        "workspace.example.com",
                        "fun-asr-realtime",
                        SpeechAccessMode.REALTIME,
                        "qwen-audio-3.0-tts-flash",
                        SpeechAccessMode.NON_REALTIME,
                        "longanhuan_v3.6",
                        "secret"
                )
        ))
                .isInstanceOf(InvalidSpeechSettingsException.class)
                .hasMessageContaining("Workspace ID");
    }

    @Test
    void persistsDeviceVoiceDetectionSettings() {
        when(repository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(secretCipher.encrypt("speech-secret"))
                .thenReturn(new SecretCipher.EncryptedSecret("ciphertext", "iv"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SpeechSettingsService.SpeechSettingsSnapshot result = service().saveSettings(
                new SpeechSettingsService.UpdateSpeechSettingsCommand(
                        SpeechProviderType.OPENAI_COMPATIBLE,
                        "https://speech.example.com/v1",
                        "",
                        "asr",
                        SpeechAccessMode.NON_REALTIME,
                        "tts",
                        SpeechAccessMode.NON_REALTIME,
                        "voice",
                        VoiceWakeSensitivity.NORMAL,
                        480,
                        240,
                        "speech-secret"
                )
        );

        assertThat(result.wakeSensitivity()).isEqualTo(VoiceWakeSensitivity.NORMAL);
        assertThat(result.speechStartThreshold()).isEqualTo(480);
        assertThat(result.speechSilenceThreshold()).isEqualTo(240);
    }

    @Test
    void rejectsVoiceDetectionThresholdsThatCannotReturnToSilence() {
        assertThatThrownBy(() -> service().saveSettings(
                new SpeechSettingsService.UpdateSpeechSettingsCommand(
                        SpeechProviderType.OPENAI_COMPATIBLE,
                        "https://speech.example.com/v1",
                        "",
                        "asr",
                        SpeechAccessMode.NON_REALTIME,
                        "tts",
                        SpeechAccessMode.NON_REALTIME,
                        "voice",
                        VoiceWakeSensitivity.SENSITIVE,
                        300,
                        300,
                        "speech-secret"
                )
        ))
                .isInstanceOf(InvalidSpeechSettingsException.class)
                .hasMessageContaining("静音阈值必须小于");
    }

    private SpeechSettingsService service() {
        return new SpeechSettingsService(repository, secretCipher, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
