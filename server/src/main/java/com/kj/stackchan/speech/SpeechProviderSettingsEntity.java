package com.kj.stackchan.speech;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "speech_provider_settings")
public class SpeechProviderSettingsEntity {

    public static final short CURRENT_SETTINGS_ID = 1;

    @Id
    private Short id = CURRENT_SETTINGS_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 32)
    private SpeechProviderType providerType;

    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @Column(name = "workspace_id", length = 160)
    private String workspaceId;

    @Column(name = "asr_model", nullable = false, length = 160)
    private String asrModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "asr_mode", nullable = false, length = 32)
    private SpeechAccessMode asrMode;

    @Column(name = "tts_model", nullable = false, length = 160)
    private String ttsModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "tts_mode", nullable = false, length = 32)
    private SpeechAccessMode ttsMode;

    @Column(name = "tts_voice", nullable = false, length = 160)
    private String ttsVoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "wake_sensitivity", nullable = false, length = 32)
    private VoiceWakeSensitivity wakeSensitivity;

    @Column(name = "speech_start_threshold", nullable = false)
    private int speechStartThreshold;

    @Column(name = "speech_silence_threshold", nullable = false)
    private int speechSilenceThreshold;

    @Column(name = "api_key_ciphertext", nullable = false, columnDefinition = "TEXT")
    private String apiKeyCiphertext;

    @Column(name = "api_key_iv", nullable = false, length = 64)
    private String apiKeyIv;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SpeechProviderSettingsEntity() {
    }

    public SpeechProviderSettingsEntity(
            SpeechProviderType providerType,
            String baseUrl,
            String workspaceId,
            String asrModel,
            SpeechAccessMode asrMode,
            String ttsModel,
            SpeechAccessMode ttsMode,
            String ttsVoice,
            VoiceWakeSensitivity wakeSensitivity,
            int speechStartThreshold,
            int speechSilenceThreshold,
            String apiKeyCiphertext,
            String apiKeyIv,
            Instant updatedAt
    ) {
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.workspaceId = workspaceId;
        this.asrModel = asrModel;
        this.asrMode = asrMode;
        this.ttsModel = ttsModel;
        this.ttsMode = ttsMode;
        this.ttsVoice = ttsVoice;
        this.wakeSensitivity = wakeSensitivity;
        this.speechStartThreshold = speechStartThreshold;
        this.speechSilenceThreshold = speechSilenceThreshold;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyIv = apiKeyIv;
        this.updatedAt = updatedAt;
    }

    public SpeechProviderSettingsEntity(
            SpeechProviderType providerType,
            String baseUrl,
            String workspaceId,
            String asrModel,
            SpeechAccessMode asrMode,
            String ttsModel,
            SpeechAccessMode ttsMode,
            String ttsVoice,
            String apiKeyCiphertext,
            String apiKeyIv,
            Instant updatedAt
    ) {
        this(
                providerType,
                baseUrl,
                workspaceId,
                asrModel,
                asrMode,
                ttsModel,
                ttsMode,
                ttsVoice,
                VoiceWakeSensitivity.SENSITIVE,
                350,
                200,
                apiKeyCiphertext,
                apiKeyIv,
                updatedAt
        );
    }

    public void update(
            SpeechProviderType providerType,
            String baseUrl,
            String workspaceId,
            String asrModel,
            SpeechAccessMode asrMode,
            String ttsModel,
            SpeechAccessMode ttsMode,
            String ttsVoice,
            VoiceWakeSensitivity wakeSensitivity,
            int speechStartThreshold,
            int speechSilenceThreshold,
            String apiKeyCiphertext,
            String apiKeyIv,
            Instant updatedAt
    ) {
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.workspaceId = workspaceId;
        this.asrModel = asrModel;
        this.asrMode = asrMode;
        this.ttsModel = ttsModel;
        this.ttsMode = ttsMode;
        this.ttsVoice = ttsVoice;
        this.wakeSensitivity = wakeSensitivity;
        this.speechStartThreshold = speechStartThreshold;
        this.speechSilenceThreshold = speechSilenceThreshold;
        this.apiKeyCiphertext = apiKeyCiphertext;
        this.apiKeyIv = apiKeyIv;
        this.updatedAt = updatedAt;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public SpeechProviderType getProviderType() {
        return providerType;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getAsrModel() {
        return asrModel;
    }

    public SpeechAccessMode getAsrMode() {
        return asrMode;
    }

    public String getTtsModel() {
        return ttsModel;
    }

    public SpeechAccessMode getTtsMode() {
        return ttsMode;
    }

    public String getTtsVoice() {
        return ttsVoice;
    }

    public VoiceWakeSensitivity getWakeSensitivity() {
        return wakeSensitivity;
    }

    public int getSpeechStartThreshold() {
        return speechStartThreshold;
    }

    public int getSpeechSilenceThreshold() {
        return speechSilenceThreshold;
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
}
