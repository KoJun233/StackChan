package com.kj.stackchan.llm;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

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
class LlmSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Mock
    private LlmProviderSettingsRepository settingsRepository;

    @Mock
    private SecretCipher secretCipher;

    @Test
    void encryptsTheApiKeyAndNeverExposesItFromTheSnapshot() {
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(secretCipher.encrypt("sk-super-secret"))
                .thenReturn(new SecretCipher.EncryptedSecret("encrypted-value", "initialization-vector"));
        when(settingsRepository.save(any(LlmProviderSettingsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LlmSettingsService.LlmSettingsSnapshot saved = service.saveSettings(new LlmSettingsService.UpdateLlmSettingsCommand(
                "https://api.example.com/v1",
                "my-model",
                "你是一个温柔的陪伴机器人。",
                "sk-super-secret"
        ));

        ArgumentCaptor<LlmProviderSettingsEntity> entityCaptor = ArgumentCaptor.forClass(LlmProviderSettingsEntity.class);
        verify(settingsRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getApiKeyCiphertext()).isEqualTo("encrypted-value");
        assertThat(entityCaptor.getValue().getApiKeyCiphertext()).doesNotContain("sk-super-secret");
        assertThat(saved.apiKeyConfigured()).isTrue();
        assertThat(saved.toString()).doesNotContain("sk-super-secret");
        assertThat(saved.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void keepsTheExistingEncryptedKeyWhenTheApiKeyFieldIsLeftEmpty() {
        LlmProviderSettingsEntity existing = new LlmProviderSettingsEntity(
                "https://api.example.com/v1", "old-model", "old prompt", "old-ciphertext", "old-iv", NOW.minusSeconds(60)
        );
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.of(existing));
        when(settingsRepository.save(existing)).thenReturn(existing);

        service.saveSettings(new LlmSettingsService.UpdateLlmSettingsCommand(
                "http://192.168.1.2:11434/v1", "new-model", "new prompt", ""
        ));

        assertThat(existing.getApiKeyCiphertext()).isEqualTo("old-ciphertext");
        assertThat(existing.getApiKeyIv()).isEqualTo("old-iv");
        assertThat(existing.getBaseUrl()).isEqualTo("http://192.168.1.2:11434/v1");
    }

    @Test
    void requiresAnApiKeyOnTheFirstSave() {
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveSettings(new LlmSettingsService.UpdateLlmSettingsCommand(
                "https://api.example.com/v1", "my-model", "prompt", ""
        )))
                .isInstanceOf(InvalidLlmSettingsException.class)
                .hasMessageContaining("首次保存");
    }

    @Test
    void rejectsUnsupportedProviderUrls() {
        LlmSettingsService service = service();

        assertThatThrownBy(() -> service.saveSettings(new LlmSettingsService.UpdateLlmSettingsCommand(
                "ftp://api.example.com", "my-model", "prompt", "sk-secret"
        )))
                .isInstanceOf(InvalidLlmSettingsException.class)
                .hasMessageContaining("HTTP 或 HTTPS");
    }

    @Test
    void usesTheDefaultCompanionPromptForLegacyBlankSettings() {
        LlmProviderSettingsEntity settings = new LlmProviderSettingsEntity(
                "https://api.example.com/v1", "qwen3.7-plus", "   ", "ciphertext", "iv", NOW
        );
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID))
                .thenReturn(Optional.of(settings));
        when(secretCipher.decrypt(new SecretCipher.EncryptedSecret("ciphertext", "iv")))
                .thenReturn("sk-secret");

        assertThat(service.getSettings().systemPrompt()).isEqualTo(LlmSettingsService.DEFAULT_SYSTEM_PROMPT);
        assertThat(service.resolveForInvocation().systemPrompt()).isEqualTo(LlmSettingsService.DEFAULT_SYSTEM_PROMPT);
    }

    @Test
    void storesTheDefaultCompanionPromptWhenASettingsUpdateIsBlank() {
        LlmProviderSettingsEntity existing = new LlmProviderSettingsEntity(
                "https://api.example.com/v1", "qwen3.7-plus", "old prompt", "ciphertext", "iv", NOW
        );
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID))
                .thenReturn(Optional.of(existing));
        when(settingsRepository.save(existing)).thenReturn(existing);

        LlmSettingsService.LlmSettingsSnapshot saved = service.saveSettings(
                new LlmSettingsService.UpdateLlmSettingsCommand(
                        "https://api.example.com/v1", "qwen3.7-plus", "", ""
                )
        );

        assertThat(existing.getSystemPrompt()).isEqualTo(LlmSettingsService.DEFAULT_SYSTEM_PROMPT);
        assertThat(saved.systemPrompt()).isEqualTo(LlmSettingsService.DEFAULT_SYSTEM_PROMPT);
    }

    @Test
    void resolvesTheEncryptedApiKeyOnlyForAnInvocation() {
        LlmProviderSettingsEntity settings = new LlmProviderSettingsEntity(
                "https://api.example.com/v1", "qwen3.7-plus", "companion prompt", "ciphertext", "iv", NOW
        );
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.of(settings));
        when(secretCipher.decrypt(new SecretCipher.EncryptedSecret("ciphertext", "iv"))).thenReturn("sk-secret");

        ResolvedLlmSettings resolved = service.resolveForInvocation();

        assertThat(resolved.baseUrl()).isEqualTo("https://api.example.com/v1");
        assertThat(resolved.model()).isEqualTo("qwen3.7-plus");
        assertThat(resolved.systemPrompt()).isEqualTo("companion prompt");
        assertThat(resolved.apiKey()).isEqualTo("sk-secret");
    }

    @Test
    void requiresSavedSettingsBeforeAnInvocation() {
        LlmSettingsService service = service();
        when(settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(service::resolveForInvocation)
                .isInstanceOf(InvalidLlmSettingsException.class)
                .hasMessage("请先完成 AI 配置");
    }

    private LlmSettingsService service() {
        return new LlmSettingsService(
                settingsRepository,
                secretCipher,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
