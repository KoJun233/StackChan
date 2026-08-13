package com.kj.stackchan.speech;

import java.util.UUID;

import com.kj.stackchan.role.CompanionRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SpeechRuntimeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpeechRuntimeClient.class);

    private final SpeechSettingsService settingsService;
    private final OpenAiCompatibleSpeechProviderAdapter openAiCompatible;
    private final DashScopeSpeechProviderAdapter dashScope;
    private final CompanionRoleService roleService;

    @Autowired
    public SpeechRuntimeClient(
            SpeechSettingsService settingsService,
            OpenAiCompatibleSpeechProviderAdapter openAiCompatible,
            DashScopeSpeechProviderAdapter dashScope,
            CompanionRoleService roleService
    ) {
        this.settingsService = settingsService;
        this.openAiCompatible = openAiCompatible;
        this.dashScope = dashScope;
        this.roleService = roleService;
    }

    SpeechRuntimeClient(SpeechSettingsService settingsService,
                        OpenAiCompatibleSpeechProviderAdapter openAiCompatible,
                        DashScopeSpeechProviderAdapter dashScope) {
        this(settingsService, openAiCompatible, dashScope, null);
    }

    public String transcribe(byte[] wavAudio) {
        if (wavAudio == null || wavAudio.length == 0) {
            throw new IllegalArgumentException("WAV audio is required");
        }
        ResolvedSpeechSettings settings = settingsService.resolveForInvocation();
        try {
            return adapter(settings).transcribe(settings, wavAudio);
        } catch (InvalidSpeechSettingsException exception) {
            throw exception;
        } catch (VoiceInputException exception) {
            throw exception;
        } catch (SpeechProviderUnavailableException exception) {
            throw logProviderFailure(exception);
        } catch (RuntimeException exception) {
            throw logProviderFailure(new SpeechProviderUnavailableException(exception));
        }
    }

    public byte[] synthesize(String text) {
        return synthesize(text, null);
    }

    public byte[] synthesize(String text, UUID roleId) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Speech text is required");
        }
        ResolvedSpeechSettings settings = settingsService.resolveForInvocation();
        String override = roleId == null || roleService == null ? null : roleService.get(roleId).ttsVoiceOverride();
        if (override != null && !override.equals(settings.ttsVoice())) {
            try {
                return adapter(settings).synthesize(settings.withTtsVoice(override), text);
            } catch (InvalidSpeechSettingsException | SpeechProviderUnavailableException exception) {
                LOGGER.warn("Role voice override unavailable; retrying the global voice");
            } catch (RuntimeException exception) {
                LOGGER.warn("Role voice override failed; retrying the global voice");
            }
        }
        try {
            return adapter(settings).synthesize(settings, text);
        } catch (InvalidSpeechSettingsException exception) {
            throw exception;
        } catch (SpeechProviderUnavailableException exception) {
            throw logProviderFailure(exception);
        } catch (RuntimeException exception) {
            throw logProviderFailure(new SpeechProviderUnavailableException(exception));
        }
    }

    public void testConnection() {
        ResolvedSpeechSettings settings = settingsService.resolveForInvocation();
        SpeechProviderAdapter adapter = adapter(settings);
        try {
            byte[] audio = adapter.synthesize(settings, "你好，我是 StackChan。语音服务连接正常。");
            if (adapter.transcribeSynthesized(settings, audio).isBlank()) {
                throw new SpeechProviderUnavailableException();
            }
            LOGGER.info("Speech provider connection test succeeded for provider={}", settings.providerType());
        } catch (InvalidSpeechSettingsException exception) {
            throw exception;
        } catch (SpeechProviderUnavailableException exception) {
            throw logProviderFailure(exception);
        } catch (RuntimeException exception) {
            throw logProviderFailure(new SpeechProviderUnavailableException(exception));
        }
    }

    private SpeechProviderUnavailableException logProviderFailure(
            SpeechProviderUnavailableException exception
    ) {
        LOGGER.warn("Speech provider unavailable at stage={}", exception.diagnosticCode());
        return exception;
    }

    private SpeechProviderAdapter adapter(ResolvedSpeechSettings settings) {
        return switch (settings.providerType()) {
            case OPENAI_COMPATIBLE -> openAiCompatible;
            case DASHSCOPE -> dashScope;
        };
    }
}
