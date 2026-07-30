package com.kj.stackchan.speech;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeHttpProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsWorkspaceEndpointsWithoutInspectingModelNames() {
        assertThat(DashScopeEndpoints.webSocket("llm-workspace123").toString())
                .isEqualTo("wss://llm-workspace123.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference");
        assertThat(DashScopeEndpoints.asrHttp("llm-workspace123").toString())
                .isEqualTo("https://llm-workspace123.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
        assertThat(DashScopeEndpoints.ttsHttp("llm-workspace123").toString())
                .isEqualTo("https://llm-workspace123.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
    }

    @Test
    void buildsNonRealtimeAsrPayloadWithTheExactConfiguredModel() {
        Map<String, ?> request = DashScopeAsrHttpClient.request(
                "future-asr-model", new byte[] {1, 2, 3}
        );

        assertThat(request.get("model")).isEqualTo("future-asr-model");
        assertThat(request.get("parameters")).isEqualTo(Map.of(
                "format", "wav",
                "sample_rate", 16000
        ));
        assertThat(request.get("input")).isEqualTo(Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "input_audio",
                                "audio", "data:audio/wav;base64,AQID"
                        ))
                ))
        ));
    }

    @Test
    void readsBothDocumentedNonRealtimeAsrResponseShapes() throws Exception {
        JsonNode nested = objectMapper.readTree("""
                {"output":{"output":{"sentence":{"text":"嵌套结果"}}}}
                """);
        JsonNode flat = objectMapper.readTree("""
                {"output":{"text":"直接结果"}}
                """);

        assertThat(DashScopeAsrHttpClient.transcript(nested)).isEqualTo("嵌套结果");
        assertThat(DashScopeAsrHttpClient.transcript(flat)).isEqualTo("直接结果");
    }

    @Test
    void buildsNonRealtimeTtsPayloadWithTheExactConfiguredModel() {
        Map<String, ?> request = DashScopeTtsHttpClient.request(
                "future-tts-model", "custom-voice", "你好"
        );

        assertThat(request.get("model")).isEqualTo("future-tts-model");
        assertThat(request.get("input")).isEqualTo(Map.of(
                "text", "你好",
                "voice", "custom-voice"
        ));
        assertThat(request).doesNotContainKey("parameters");
    }

    @Test
    void upgradesTheDocumentedResultUrlToHttps() {
        URI result = DashScopeEndpoints.validatedDownloadUri(
                "http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/pre/audio.wav?signature=value%2Fpart"
        );

        assertThat(result.getScheme()).isEqualTo("https");
        assertThat(result.getHost()).isEqualTo("dashscope-result-bj.oss-cn-beijing.aliyuncs.com");
        assertThat(result.getRawQuery()).isEqualTo("signature=value%2Fpart");
    }

    @Test
    void acceptsTheObservedWulanchabuResultHostWithoutBroadeningTheAllowlist() {
        URI result = DashScopeEndpoints.validatedDownloadUri(
                "http://dashscope-result-wlcb.oss-cn-wulanchabu.aliyuncs.com/pre/audio.wav?signature=value%2Fpart"
        );

        assertThat(result.getScheme()).isEqualTo("https");
        assertThat(result.getHost()).isEqualTo("dashscope-result-wlcb.oss-cn-wulanchabu.aliyuncs.com");
        assertThat(result.getRawQuery()).isEqualTo("signature=value%2Fpart");
        assertThatThrownBy(() -> DashScopeEndpoints.validatedDownloadUri(
                "https://dashscope-result-wlcb.oss-cn-wulanchabu.aliyuncs.com.evil.example/audio.wav"
        )).isInstanceOf(SpeechProviderUnavailableException.class);
    }

    @Test
    void rejectsProviderControlledUrlsOutsideTheDocumentedResultHost() {
        assertThatThrownBy(() -> DashScopeEndpoints.validatedDownloadUri(
                "http://127.0.0.1/internal.wav"
        )).isInstanceOf(SpeechProviderUnavailableException.class);
    }

    @Test
    void extractsOnlyBoundedSafeProviderFailureDiagnostics() {
        WebClientResponseException response = WebClientResponseException.create(
                400,
                "Bad Request",
                HttpHeaders.EMPTY,
                """
                        {"request_id":"request-123","code":"InvalidParameter","message":"voice is invalid\\r\\nignored line"}
                        """.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        DashScopeTtsHttpClient.ProviderError error = DashScopeTtsHttpClient.providerError(response);

        assertThat(error.requestId()).isEqualTo("request-123");
        assertThat(error.code()).isEqualTo("InvalidParameter");
        assertThat(error.message()).isEqualTo("voice is invalid ignored line");
    }
}
