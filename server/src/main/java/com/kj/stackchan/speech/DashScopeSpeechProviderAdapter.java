package com.kj.stackchan.speech;

import org.springframework.stereotype.Component;

@Component
class DashScopeSpeechProviderAdapter implements SpeechProviderAdapter {

    private final DashScopeAsrWebSocketClient asrWebSocketClient;
    private final DashScopeAsrHttpClient asrHttpClient;
    private final DashScopeTtsWebSocketClient ttsWebSocketClient;
    private final DashScopeTtsHttpClient ttsHttpClient;

    DashScopeSpeechProviderAdapter(
            DashScopeAsrWebSocketClient asrWebSocketClient,
            DashScopeAsrHttpClient asrHttpClient,
            DashScopeTtsWebSocketClient ttsWebSocketClient,
            DashScopeTtsHttpClient ttsHttpClient
    ) {
        this.asrWebSocketClient = asrWebSocketClient;
        this.asrHttpClient = asrHttpClient;
        this.ttsWebSocketClient = ttsWebSocketClient;
        this.ttsHttpClient = ttsHttpClient;
    }

    @Override
    public String transcribe(ResolvedSpeechSettings settings, byte[] wavAudio) {
        return transcribe(settings, wavAudio, false);
    }

    @Override
    public String transcribeSynthesized(ResolvedSpeechSettings settings, byte[] wavAudio) {
        return transcribe(settings, wavAudio, true);
    }

    private String transcribe(
            ResolvedSpeechSettings settings,
            byte[] wavAudio,
            boolean allowProviderLengthPlaceholder
    ) {
        return switch (settings.asrMode()) {
            case REALTIME -> asrWebSocketClient.transcribe(
                    DashScopeEndpoints.webSocket(settings.workspaceId()),
                    settings.apiKey(),
                    settings.workspaceId(),
                    settings.asrModel(),
                    asrPcm(wavAudio, allowProviderLengthPlaceholder)
            );
            case NON_REALTIME -> {
                validateAsrWav(wavAudio, allowProviderLengthPlaceholder);
                yield asrHttpClient.transcribe(
                        DashScopeEndpoints.asrHttp(settings.workspaceId()),
                        settings.apiKey(),
                        settings.asrModel(),
                        wavAudio
                );
            }
        };
    }

    static byte[] asrPcm(byte[] wavAudio) {
        return asrPcm(wavAudio, false);
    }

    static byte[] asrPcm(byte[] wavAudio, boolean allowProviderLengthPlaceholder) {
        try {
            return WavPcmAudio.extractMono16KhzPcm(wavAudio, allowProviderLengthPlaceholder);
        } catch (SpeechProviderUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidAsrInput(wavAudio, exception);
        }
    }

    @Override
    public byte[] synthesize(ResolvedSpeechSettings settings, String text) {
        byte[] audio = switch (settings.ttsMode()) {
            case REALTIME -> ttsWebSocketClient.synthesize(
                    DashScopeEndpoints.webSocket(settings.workspaceId()),
                    settings.apiKey(),
                    settings.workspaceId(),
                    settings.ttsModel(),
                    settings.ttsVoice(),
                    text
            );
            case NON_REALTIME -> ttsHttpClient.synthesize(
                    DashScopeEndpoints.ttsHttp(settings.workspaceId()),
                    settings.apiKey(),
                    settings.ttsModel(),
                    settings.ttsVoice(),
                    text
            );
        };
        try {
            byte[] wav = OpenAiCompatibleSpeechProviderAdapter.requireWav(audio);
            return WavPcmAudio.normalizeSynthesizedMono16KhzWav(wav);
        } catch (SpeechProviderUnavailableException exception) {
            throw invalidTtsAudio(audio, exception);
        } catch (RuntimeException exception) {
            throw invalidTtsAudio(audio, exception);
        }
    }

    private static SpeechProviderUnavailableException invalidTtsAudio(byte[] audio, RuntimeException exception) {
        return new SpeechProviderUnavailableException(
                "dashscope_tts_audio_invalid_" + WavPcmAudio.layoutDiagnosticCode(audio),
                exception
        );
    }

    private static void validateAsrWav(byte[] wavAudio, boolean allowProviderLengthPlaceholder) {
        try {
            WavPcmAudio.extractMono16KhzPcm(wavAudio, allowProviderLengthPlaceholder);
        } catch (SpeechProviderUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidAsrInput(wavAudio, exception);
        }
    }

    private static SpeechProviderUnavailableException invalidAsrInput(
            byte[] wavAudio,
            RuntimeException exception
    ) {
        return new SpeechProviderUnavailableException(
                "dashscope_asr_input_" + WavPcmAudio.layoutDiagnosticCode(wavAudio),
                exception
        );
    }
}
