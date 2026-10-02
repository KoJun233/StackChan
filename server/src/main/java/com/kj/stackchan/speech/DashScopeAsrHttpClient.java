package com.kj.stackchan.speech;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
class DashScopeAsrHttpClient {

    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WebClient.Builder webClientBuilder;

    DashScopeAsrHttpClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    String transcribe(
            URI endpoint,
            String apiKey,
            String model,
            byte[] wavAudio
    ) {
        JsonNode response;
        try {
            response = webClientBuilder.clone()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-DashScope-SSE", "disable")
                    .bodyValue(request(model, wavAudio))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(PROVIDER_TIMEOUT);
        } catch (WebClientResponseException exception) {
            if (isNoSpeechResponse(exception)) {
                throw new VoiceInputException("没有识别到清晰语音");
            }
            if (exception.getStatusCode().value() == 403 &&
                    "AllocationQuota.FreeTierOnly".equals(providerCode(exception))) {
                throw SpeechProviderUnavailableException.freeQuotaOnly();
            }
            throw new SpeechProviderUnavailableException(
                    SpeechProviderUnavailableException.httpDiagnosticCode(
                            "dashscope_asr_http_request", exception.getStatusCode().value()
                    ),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new SpeechProviderUnavailableException("dashscope_asr_http_request", exception);
        }

        String transcript = transcript(response);
        if (transcript.isBlank()) {
            if (response != null && (response.path("output").path("text").isTextual() ||
                    response.path("output").path("sentence").path("text").isTextual())) {
                throw new VoiceInputException("没有识别到清晰语音");
            }
            throw new SpeechProviderUnavailableException("dashscope_asr_http_result_invalid");
        }
        return transcript;
    }

    static Map<String, ?> request(String model, byte[] wavAudio) {
        String dataUri = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wavAudio);
        return Map.of(
                "model", model,
                "input", Map.of(
                        "messages", List.of(Map.of(
                                "role", "user",
                                "content", List.of(Map.of(
                                        "type", "input_audio",
                                        "input_audio", Map.of("data", dataUri)
                                ))
                        ))
                ),
                "parameters", Map.of(
                        "format", "wav",
                        "sample_rate", "16000"
                )
        );
    }

    static String transcript(JsonNode response) {
        if (response == null) {
            return "";
        }
        String text = response.path("output").path("text").asText("").trim();
        if (!text.isBlank()) return text;
        String documented = response.path("output").path("sentence").path("text").asText("").trim();
        if (!documented.isBlank()) return documented;
        String sentence = response.path("output")
                .path("output")
                .path("sentence")
                .path("text")
                .asText("")
                .trim();
        if (!sentence.isBlank()) {
            return sentence;
        }
        return response.path("output").path("text").asText("").trim();
    }

    static boolean isNoSpeechResponse(WebClientResponseException exception) {
        if (exception == null || exception.getStatusCode().value() != 400) return false;
        String code = providerCode(exception);
        return "SUCCESS_WITH_NO_VALID_FRAGMENT".equalsIgnoreCase(code)
                || "ASR_RESPONSE_HAVE_NO_WORDS".equalsIgnoreCase(code);
    }

    static String providerCode(WebClientResponseException exception) {
        if (exception == null || exception.getResponseBodyAsByteArray().length > 4096) return "-";
        try {
            String code = OBJECT_MAPPER.readTree(exception.getResponseBodyAsByteArray()).path("code").asText("");
            return code.matches("[A-Za-z0-9._:-]{1,128}") ? code : "-";
        } catch (Exception ignored) {
            return "-";
        }
    }
}
