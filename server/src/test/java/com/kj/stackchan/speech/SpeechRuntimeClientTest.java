package com.kj.stackchan.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpeechRuntimeClientTest {

    @Mock
    private SpeechSettingsService settingsService;

    @Mock
    private OpenAiCompatibleSpeechProviderAdapter openAiCompatible;

    @Mock
    private DashScopeSpeechProviderAdapter dashScope;

    @Test
    void routesDashScopeCallsThroughTheNativeAdapter() {
        ResolvedSpeechSettings settings = new ResolvedSpeechSettings(
                SpeechProviderType.DASHSCOPE,
                "",
                "llm-workspace123",
                "fun-asr-realtime",
                SpeechAccessMode.REALTIME,
                "qwen-audio-3.0-tts-flash",
                SpeechAccessMode.NON_REALTIME,
                "longanhuan_v3.6",
                "secret"
        );
        byte[] wav = new byte[] {1, 2, 3};
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(dashScope.transcribe(settings, wav)).thenReturn("测试语音");

        assertThat(client().transcribe(wav)).isEqualTo("测试语音");
        verify(dashScope).transcribe(settings, wav);
    }

    @Test
    void connectionTestExercisesSynthesisAndRecognition() {
        ResolvedSpeechSettings settings = new ResolvedSpeechSettings(
                SpeechProviderType.OPENAI_COMPATIBLE,
                "https://speech.example.com/v1",
                "",
                "asr",
                SpeechAccessMode.NON_REALTIME,
                "tts",
                SpeechAccessMode.NON_REALTIME,
                "voice",
                "secret"
        );
        byte[] wav = new byte[] {1, 2, 3};
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(openAiCompatible.synthesize(settings, "你好，我是 StackChan。语音服务连接正常。"))
                .thenReturn(wav);
        when(openAiCompatible.transcribeSynthesized(settings, wav)).thenReturn("你好");

        client().testConnection();

        verify(openAiCompatible).synthesize(settings, "你好，我是 StackChan。语音服务连接正常。");
        verify(openAiCompatible).transcribeSynthesized(settings, wav);
    }

    @Test
    void preservesSafeProviderStageForConnectionDiagnostics() {
        ResolvedSpeechSettings settings = new ResolvedSpeechSettings(
                SpeechProviderType.DASHSCOPE,
                "",
                "llm-workspace123",
                "fun-asr-realtime",
                SpeechAccessMode.REALTIME,
                "qwen-audio-3.0-tts-flash",
                SpeechAccessMode.NON_REALTIME,
                "longanhuan_v3.6",
                "secret"
        );
        when(settingsService.resolveForInvocation()).thenReturn(settings);
        when(dashScope.synthesize(settings, "你好，我是 StackChan。语音服务连接正常。"))
                .thenThrow(new SpeechProviderUnavailableException("dashscope_tts_request_http_401"));

        assertThatThrownBy(() -> client().testConnection())
                .isInstanceOf(SpeechProviderUnavailableException.class)
                .extracting(exception -> ((SpeechProviderUnavailableException) exception).diagnosticCode())
                .isEqualTo("dashscope_tts_request_http_401");
    }

    private SpeechRuntimeClient client() {
        return new SpeechRuntimeClient(settingsService, openAiCompatible, dashScope);
    }
}
