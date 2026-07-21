package com.kj.stackchan.llm;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "llm_provider_settings")
public class LlmProviderSettingsEntity {

    public static final short CURRENT_SETTINGS_ID = 1;

    @Id
    private Short id = CURRENT_SETTINGS_ID;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Column(nullable = false, length = 160)
    private String model;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "api_key_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String apiKeyCiphertext;

    @Column(name = "api_key_iv", nullable = false, length = 64)
    private String apiKeyIv;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LlmProviderSettingsEntity() {
    }

    public LlmProviderSettingsEntity(
            String baseUrl,
            String model,
            String systemPrompt,
            String apiKeyCiphertext,
            String apiKeyIv,
            Instant updatedAt
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyIv = apiKeyIv;
        this.updatedAt = updatedAt;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getApiKeyCiphertext() {
        return apiKeyCiphertext;
    }

    public String getApiKeyIv() {
        return apiKeyIv;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void update(
            String baseUrl,
            String model,
            String systemPrompt,
            String apiKeyCiphertext,
            String apiKeyIv,
            Instant updatedAt
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyIv = apiKeyIv;
        this.updatedAt = updatedAt;
    }
}
