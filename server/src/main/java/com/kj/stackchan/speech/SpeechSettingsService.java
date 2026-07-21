package com.kj.stackchan.speech;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;

import com.kj.stackchan.llm.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpeechSettingsService {

    public static final VoiceWakeSensitivity DEFAULT_WAKE_SENSITIVITY = VoiceWakeSensitivity.SENSITIVE;
    public static final int DEFAULT_SPEECH_START_THRESHOLD = 350;
    public static final int DEFAULT_SPEECH_SILENCE_THRESHOLD = 200;
    public static final int MIN_SPEECH_START_THRESHOLD = 100;
    public static final int MAX_SPEECH_START_THRESHOLD = 5000;
    public static final int MIN_SPEECH_SILENCE_THRESHOLD = 50;
    public static final int MAX_SPEECH_SILENCE_THRESHOLD = 4000;

    private static final String WORKSPACE_ID_PATTERN =
            "(?=.{3,63}$)[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?";

    private final SpeechProviderSettingsRepository settingsRepository;
    private final SecretCipher secretCipher;
    private final Clock clock;

    public SpeechSettingsService(
            SpeechProviderSettingsRepository settingsRepository,
            SecretCipher secretCipher,
            Clock clock
    ) {
        this.settingsRepository = settingsRepository;
        this.secretCipher = secretCipher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SpeechSettingsSnapshot getSettings() {
        return settingsRepository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)
                .map(this::toSnapshot)
                .orElseGet(SpeechSettingsSnapshot::empty);
    }

    @Transactional(readOnly = true)
    public ResolvedSpeechSettings resolveForInvocation() {
        return settingsRepository.findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)
                .map(this::resolveSettings)
                .orElseThrow(() -> new InvalidSpeechSettingsException("请先完成语音配置"));
    }

    @Transactional
    public SpeechSettingsSnapshot saveSettings(UpdateSpeechSettingsCommand command) {
        SpeechProviderType providerType = command.providerType() == null
                ? SpeechProviderType.OPENAI_COMPATIBLE
                : command.providerType();
        String baseUrl = providerType == SpeechProviderType.OPENAI_COMPATIBLE
                ? normalizeBaseUrl(command.baseUrl())
                : "";
        String workspaceId = providerType == SpeechProviderType.DASHSCOPE
                ? normalizeWorkspaceId(command.workspaceId())
                : "";
        String asrModel = requiredValue(command.asrModel(), "语音识别模型不能为空");
        SpeechAccessMode asrMode = requiredMode(command.asrMode(), "请选择语音识别接入方式");
        String ttsModel = requiredValue(command.ttsModel(), "语音合成模型不能为空");
        SpeechAccessMode ttsMode = requiredMode(command.ttsMode(), "请选择语音合成接入方式");
        String ttsVoice = requiredValue(command.ttsVoice(), "语音音色不能为空");
        VoiceWakeSensitivity wakeSensitivity = requiredWakeSensitivity(command.wakeSensitivity());
        validateVoiceDetectionThresholds(command.speechStartThreshold(), command.speechSilenceThreshold());
        String apiKey = command.apiKey() == null ? "" : command.apiKey().trim();
        SpeechProviderSettingsEntity settings = settingsRepository
                .findById(SpeechProviderSettingsEntity.CURRENT_SETTINGS_ID)
                .orElse(null);

        SecretCipher.EncryptedSecret encryptedSecret;
        if (apiKey.isBlank()) {
            if (settings == null) {
                throw new InvalidSpeechSettingsException("首次保存时必须填写 API 密钥");
            }
            encryptedSecret = new SecretCipher.EncryptedSecret(
                    settings.getApiKeyCiphertext(),
                    settings.getApiKeyIv()
            );
        } else {
            encryptedSecret = secretCipher.encrypt(apiKey);
        }

        Instant updatedAt = clock.instant();
        if (settings == null) {
            settings = new SpeechProviderSettingsEntity(
                    providerType,
                    baseUrl,
                    workspaceId,
                    asrModel,
                    asrMode,
                    ttsModel,
                    ttsMode,
                    ttsVoice,
                    wakeSensitivity,
                    command.speechStartThreshold(),
                    command.speechSilenceThreshold(),
                    encryptedSecret.ciphertext(),
                    encryptedSecret.initializationVector(),
                    updatedAt
            );
        } else {
            settings.update(
                    providerType,
                    baseUrl,
                    workspaceId,
                    asrModel,
                    asrMode,
                    ttsModel,
                    ttsMode,
                    ttsVoice,
                    wakeSensitivity,
                    command.speechStartThreshold(),
                    command.speechSilenceThreshold(),
                    encryptedSecret.ciphertext(),
                    encryptedSecret.initializationVector(),
                    updatedAt
            );
        }
        return toSnapshot(settingsRepository.save(settings));
    }

    private String requiredValue(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new InvalidSpeechSettingsException(message);
        }
        return normalized;
    }

    private SpeechAccessMode requiredMode(SpeechAccessMode mode, String message) {
        if (mode == null) {
            throw new InvalidSpeechSettingsException(message);
        }
        return mode;
    }

    private VoiceWakeSensitivity requiredWakeSensitivity(VoiceWakeSensitivity sensitivity) {
        if (sensitivity == null) {
            throw new InvalidSpeechSettingsException("请选择唤醒灵敏度");
        }
        return sensitivity;
    }

    private void validateVoiceDetectionThresholds(int startThreshold, int silenceThreshold) {
        if (startThreshold < MIN_SPEECH_START_THRESHOLD || startThreshold > MAX_SPEECH_START_THRESHOLD) {
            throw new InvalidSpeechSettingsException("开始说话阈值必须在 100 到 5000 之间");
        }
        if (silenceThreshold < MIN_SPEECH_SILENCE_THRESHOLD
                || silenceThreshold > MAX_SPEECH_SILENCE_THRESHOLD) {
            throw new InvalidSpeechSettingsException("静音阈值必须在 50 到 4000 之间");
        }
        if (silenceThreshold >= startThreshold) {
            throw new InvalidSpeechSettingsException("静音阈值必须小于开始说话阈值");
        }
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = value == null ? "" : value.trim();
        try {
            URI uri = new URI(baseUrl);
            if (!uri.isAbsolute() || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getRawFragment() != null || uri.getRawQuery() != null) {
                throw new InvalidSpeechSettingsException("接口地址必须是有效的 HTTP 或 HTTPS 地址");
            }
            while (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            return baseUrl;
        } catch (URISyntaxException exception) {
            throw new InvalidSpeechSettingsException("接口地址必须是有效的 HTTP 或 HTTPS 地址");
        }
    }

    private String normalizeWorkspaceId(String value) {
        String workspaceId = requiredValue(value, "阿里云 Workspace ID 不能为空");
        if (!workspaceId.matches(WORKSPACE_ID_PATTERN)) {
            throw new InvalidSpeechSettingsException("阿里云 Workspace ID 格式无效");
        }
        return workspaceId;
    }

    private ResolvedSpeechSettings resolveSettings(SpeechProviderSettingsEntity settings) {
        SpeechProviderType providerType = settings.getProviderType() == null
                ? SpeechProviderType.OPENAI_COMPATIBLE
                : settings.getProviderType();
        String baseUrl = providerType == SpeechProviderType.OPENAI_COMPATIBLE
                ? normalizeBaseUrl(settings.getBaseUrl())
                : "";
        String workspaceId = providerType == SpeechProviderType.DASHSCOPE
                ? normalizeWorkspaceId(settings.getWorkspaceId())
                : "";
        return new ResolvedSpeechSettings(
                providerType,
                baseUrl,
                workspaceId,
                requiredValue(settings.getAsrModel(), "语音识别模型不能为空"),
                requiredMode(settings.getAsrMode(), "请选择语音识别接入方式"),
                requiredValue(settings.getTtsModel(), "语音合成模型不能为空"),
                requiredMode(settings.getTtsMode(), "请选择语音合成接入方式"),
                requiredValue(settings.getTtsVoice(), "语音音色不能为空"),
                requiredValue(secretCipher.decrypt(new SecretCipher.EncryptedSecret(
                        settings.getApiKeyCiphertext(),
                        settings.getApiKeyIv()
                )), "API 密钥不能为空")
        );
    }

    private SpeechSettingsSnapshot toSnapshot(SpeechProviderSettingsEntity settings) {
        return new SpeechSettingsSnapshot(
                settings.getProviderType(),
                settings.getBaseUrl(),
                settings.getWorkspaceId(),
                settings.getAsrModel(),
                settings.getAsrMode(),
                settings.getTtsModel(),
                settings.getTtsMode(),
                settings.getTtsVoice(),
                settings.getWakeSensitivity(),
                settings.getSpeechStartThreshold(),
                settings.getSpeechSilenceThreshold(),
                true,
                settings.getUpdatedAt()
        );
    }

    public record UpdateSpeechSettingsCommand(
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
            String apiKey
    ) {
        public UpdateSpeechSettingsCommand(
                SpeechProviderType providerType,
                String baseUrl,
                String workspaceId,
                String asrModel,
                SpeechAccessMode asrMode,
                String ttsModel,
                SpeechAccessMode ttsMode,
                String ttsVoice,
                String apiKey
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
                    DEFAULT_WAKE_SENSITIVITY,
                    DEFAULT_SPEECH_START_THRESHOLD,
                    DEFAULT_SPEECH_SILENCE_THRESHOLD,
                    apiKey
            );
        }
    }

    public record SpeechSettingsSnapshot(
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
            boolean apiKeyConfigured,
            Instant updatedAt
    ) {
        public SpeechSettingsSnapshot(
                SpeechProviderType providerType,
                String baseUrl,
                String workspaceId,
                String asrModel,
                SpeechAccessMode asrMode,
                String ttsModel,
                SpeechAccessMode ttsMode,
                String ttsVoice,
                boolean apiKeyConfigured,
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
                    DEFAULT_WAKE_SENSITIVITY,
                    DEFAULT_SPEECH_START_THRESHOLD,
                    DEFAULT_SPEECH_SILENCE_THRESHOLD,
                    apiKeyConfigured,
                    updatedAt
            );
        }

        static SpeechSettingsSnapshot empty() {
            return new SpeechSettingsSnapshot(
                    SpeechProviderType.OPENAI_COMPATIBLE,
                    "",
                    "",
                    "",
                    SpeechAccessMode.NON_REALTIME,
                    "",
                    SpeechAccessMode.NON_REALTIME,
                    "",
                    DEFAULT_WAKE_SENSITIVITY,
                    DEFAULT_SPEECH_START_THRESHOLD,
                    DEFAULT_SPEECH_SILENCE_THRESHOLD,
                    false,
                    null
            );
        }
    }
}
