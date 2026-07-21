package com.kj.stackchan.speech;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleSpeechProviderAdapterTest {

    @Test
    void realtimeModesFailInsteadOfFallingBackToHttp() {
        OpenAiCompatibleSpeechProviderAdapter adapter =
                new OpenAiCompatibleSpeechProviderAdapter(WebClient.builder());
        ResolvedSpeechSettings realtimeAsr = settings(
                SpeechAccessMode.REALTIME,
                SpeechAccessMode.NON_REALTIME
        );
        ResolvedSpeechSettings realtimeTts = settings(
                SpeechAccessMode.NON_REALTIME,
                SpeechAccessMode.REALTIME
        );

        assertThatThrownBy(() -> adapter.transcribe(realtimeAsr, new byte[] {1}))
                .isInstanceOf(SpeechProviderUnavailableException.class)
                .extracting(exception -> ((SpeechProviderUnavailableException) exception).diagnosticCode())
                .isEqualTo("openai_compatible_asr_realtime_unsupported");
        assertThatThrownBy(() -> adapter.synthesize(realtimeTts, "你好"))
                .isInstanceOf(SpeechProviderUnavailableException.class)
                .extracting(exception -> ((SpeechProviderUnavailableException) exception).diagnosticCode())
                .isEqualTo("openai_compatible_tts_realtime_unsupported");
    }

    private static ResolvedSpeechSettings settings(
            SpeechAccessMode asrMode,
            SpeechAccessMode ttsMode
    ) {
        return new ResolvedSpeechSettings(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1",
                "",
                "custom-asr-model",
                asrMode,
                "custom-tts-model",
                ttsMode,
                "custom-voice",
                "secret"
        );
    }
}
