package com.kj.stackchan.speech;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashScopeSpeechProviderAdapterTest {

    @Mock
    private DashScopeAsrWebSocketClient asrWebSocketClient;

    @Mock
    private DashScopeAsrHttpClient asrHttpClient;

    @Mock
    private DashScopeTtsWebSocketClient ttsWebSocketClient;

    @Mock
    private DashScopeTtsHttpClient ttsHttpClient;

    @Test
    void realtimeAsrUsesOnlyWebSocketAndPassesTheModelUnchanged() {
        byte[] wav = wav();
        ResolvedSpeechSettings settings = settings(
                "future-provider-asr-model", SpeechAccessMode.REALTIME,
                "tts-model", SpeechAccessMode.NON_REALTIME
        );
        when(asrWebSocketClient.transcribe(
                DashScopeEndpoints.webSocket("llm-workspace123"),
                "secret",
                "llm-workspace123",
                "future-provider-asr-model",
                DashScopeSpeechProviderAdapter.asrPcm(wav)
        )).thenReturn("实时结果");

        assertThat(adapter().transcribe(settings, wav)).isEqualTo("实时结果");

        verify(asrWebSocketClient).transcribe(
                DashScopeEndpoints.webSocket("llm-workspace123"),
                "secret",
                "llm-workspace123",
                "future-provider-asr-model",
                DashScopeSpeechProviderAdapter.asrPcm(wav)
        );
        verifyNoInteractions(asrHttpClient);
    }

    @Test
    void nonRealtimeAsrUsesOnlyHttpAndPassesTheModelUnchanged() {
        byte[] wav = wav();
        ResolvedSpeechSettings settings = settings(
                "another-asr-model", SpeechAccessMode.NON_REALTIME,
                "tts-model", SpeechAccessMode.NON_REALTIME
        );
        when(asrHttpClient.transcribe(
                DashScopeEndpoints.asrHttp("llm-workspace123"),
                "secret",
                "another-asr-model",
                wav
        )).thenReturn("非实时结果");

        assertThat(adapter().transcribe(settings, wav)).isEqualTo("非实时结果");

        verify(asrHttpClient).transcribe(
                DashScopeEndpoints.asrHttp("llm-workspace123"),
                "secret",
                "another-asr-model",
                wav
        );
        verifyNoInteractions(asrWebSocketClient);
    }

    @Test
    void realtimeTtsUsesOnlyWebSocketAndPassesTheModelUnchanged() {
        byte[] wav = wav();
        ResolvedSpeechSettings settings = settings(
                "asr-model", SpeechAccessMode.NON_REALTIME,
                "future-provider-tts-model", SpeechAccessMode.REALTIME
        );
        when(ttsWebSocketClient.synthesize(
                DashScopeEndpoints.webSocket("llm-workspace123"),
                "secret",
                "llm-workspace123",
                "future-provider-tts-model",
                "custom-voice",
                "你好"
        )).thenReturn(wav);

        assertThat(adapter().synthesize(settings, "你好")).startsWith('R', 'I', 'F', 'F');

        verify(ttsWebSocketClient).synthesize(
                DashScopeEndpoints.webSocket("llm-workspace123"),
                "secret",
                "llm-workspace123",
                "future-provider-tts-model",
                "custom-voice",
                "你好"
        );
        verifyNoInteractions(ttsHttpClient);
    }

    @Test
    void nonRealtimeTtsUsesOnlyHttpAndPassesTheModelUnchanged() {
        byte[] wav = wav();
        ResolvedSpeechSettings settings = settings(
                "asr-model", SpeechAccessMode.NON_REALTIME,
                "another-tts-model", SpeechAccessMode.NON_REALTIME
        );
        when(ttsHttpClient.synthesize(
                DashScopeEndpoints.ttsHttp("llm-workspace123"),
                "secret",
                "another-tts-model",
                "custom-voice",
                "你好"
        )).thenReturn(wav);

        assertThat(adapter().synthesize(settings, "你好")).startsWith('R', 'I', 'F', 'F');

        verify(ttsHttpClient).synthesize(
                DashScopeEndpoints.ttsHttp("llm-workspace123"),
                "secret",
                "another-tts-model",
                "custom-voice",
                "你好"
        );
        verifyNoInteractions(ttsWebSocketClient);
    }

    @Test
    void classifiesInvalidSynthesizedWavBeforeAsr() {
        assertThatThrownBy(() -> DashScopeSpeechProviderAdapter.asrPcm(new byte[] {1, 2, 3}))
                .isInstanceOf(SpeechProviderUnavailableException.class)
                .extracting(exception -> ((SpeechProviderUnavailableException) exception).diagnosticCode())
                .isEqualTo("dashscope_asr_input_header_invalid");
    }

    private DashScopeSpeechProviderAdapter adapter() {
        return new DashScopeSpeechProviderAdapter(
                asrWebSocketClient,
                asrHttpClient,
                ttsWebSocketClient,
                ttsHttpClient
        );
    }

    private static ResolvedSpeechSettings settings(
            String asrModel,
            SpeechAccessMode asrMode,
            String ttsModel,
            SpeechAccessMode ttsMode
    ) {
        return new ResolvedSpeechSettings(
                SpeechProviderType.DASHSCOPE,
                "",
                "llm-workspace123",
                asrModel,
                asrMode,
                ttsModel,
                ttsMode,
                "custom-voice",
                "secret"
        );
    }

    private static byte[] wav() {
        ByteBuffer buffer = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[] {'R', 'I', 'F', 'F'});
        buffer.putInt(38);
        buffer.put(new byte[] {'W', 'A', 'V', 'E'});
        buffer.put(new byte[] {'f', 'm', 't', ' '});
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(16000);
        buffer.putInt(32000);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put(new byte[] {'d', 'a', 't', 'a'});
        buffer.putInt(2);
        buffer.putShort((short) 0);
        return buffer.array();
    }
}
