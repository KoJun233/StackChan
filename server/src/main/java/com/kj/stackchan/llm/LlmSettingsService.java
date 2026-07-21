package com.kj.stackchan.llm;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LlmSettingsService {

    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个温暖、可靠、有边界感的 AI 陪伴伙伴。请使用简体中文，主动关心用户的长期状态，但不要编造记忆。";

    private final LlmProviderSettingsRepository settingsRepository;
    private final SecretCipher secretCipher;
    private final Clock clock;

    public LlmSettingsService(
            LlmProviderSettingsRepository settingsRepository,
            SecretCipher secretCipher,
            Clock clock
    ) {
        this.settingsRepository = settingsRepository;
        this.secretCipher = secretCipher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public LlmSettingsSnapshot getSettings() {
        return settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)
                .map(this::toSnapshot)
                .orElseGet(LlmSettingsSnapshot::empty);
    }

    @Transactional(readOnly = true)
    public ResolvedLlmSettings resolveForInvocation() {
        return settingsRepository.findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)
                .map(settings -> new ResolvedLlmSettings(
                        settings.getBaseUrl(),
                        settings.getModel(),
                        normalizeSystemPrompt(settings.getSystemPrompt()),
                        secretCipher.decrypt(new SecretCipher.EncryptedSecret(
                                settings.getApiKeyCiphertext(),
                                settings.getApiKeyIv()
                        ))
                ))
                .orElseThrow(() -> new InvalidLlmSettingsException("请先完成 AI 配置"));
    }

    @Transactional
    public LlmSettingsSnapshot saveSettings(UpdateLlmSettingsCommand command) {
        String baseUrl = normalizeBaseUrl(command.baseUrl());
        String model = command.model().trim();
        if (model.isBlank()) {
            throw new InvalidLlmSettingsException("模型名称不能为空");
        }
        String systemPrompt = normalizeSystemPrompt(command.systemPrompt());
        String apiKey = command.apiKey() == null ? "" : command.apiKey().trim();
        LlmProviderSettingsEntity settings = settingsRepository
                .findById(LlmProviderSettingsEntity.CURRENT_SETTINGS_ID)
                .orElse(null);

        SecretCipher.EncryptedSecret encryptedSecret;
        if (apiKey.isBlank()) {
            if (settings == null) {
                throw new InvalidLlmSettingsException("首次保存时必须填写 API 密钥");
            }
            encryptedSecret = new SecretCipher.EncryptedSecret(settings.getApiKeyCiphertext(), settings.getApiKeyIv());
        } else {
            encryptedSecret = secretCipher.encrypt(apiKey);
        }

        Instant updatedAt = clock.instant();
        if (settings == null) {
            settings = new LlmProviderSettingsEntity(
                    baseUrl,
                    model,
                    systemPrompt,
                    encryptedSecret.ciphertext(),
                    encryptedSecret.initializationVector(),
                    updatedAt
            );
        } else {
            settings.update(
                    baseUrl,
                    model,
                    systemPrompt,
                    encryptedSecret.ciphertext(),
                    encryptedSecret.initializationVector(),
                    updatedAt
            );
        }

        return toSnapshot(settingsRepository.save(settings));
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = value.trim();
        try {
            URI uri = new URI(baseUrl);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getRawFragment() != null) {
                throw new InvalidLlmSettingsException("接口地址必须是有效的 HTTP 或 HTTPS 地址");
            }
            return baseUrl;
        } catch (URISyntaxException exception) {
            throw new InvalidLlmSettingsException("接口地址必须是有效的 HTTP 或 HTTPS 地址");
        }
    }

    private LlmSettingsSnapshot toSnapshot(LlmProviderSettingsEntity settings) {
        return new LlmSettingsSnapshot(
                settings.getBaseUrl(),
                settings.getModel(),
                normalizeSystemPrompt(settings.getSystemPrompt()),
                true,
                settings.getUpdatedAt()
        );
    }

    private String normalizeSystemPrompt(String value) {
        return value == null || value.isBlank() ? DEFAULT_SYSTEM_PROMPT : value;
    }

    public record UpdateLlmSettingsCommand(String baseUrl, String model, String systemPrompt, String apiKey) {
    }

    public record LlmSettingsSnapshot(
            String baseUrl,
            String model,
            String systemPrompt,
            boolean apiKeyConfigured,
            Instant updatedAt
    ) {
        static LlmSettingsSnapshot empty() {
            return new LlmSettingsSnapshot("", "", "", false, null);
        }
    }
}
