package com.kj.stackchan.speech;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
class DashScopeAsrHttpClient {

    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);

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
                    .bodyValue(request(model, wavAudio))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(PROVIDER_TIMEOUT);
        } catch (WebClientResponseException exception) {
            if (isNoSpeechResponse(exception)) {
                throw new VoiceInputException("没有识别到清晰语音");
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
                                        "audio", dataUri
                                ))
                        ))
                ),
                "parameters", Map.of(
                        "format", "wav",
                        "sample_rate", 16000
                )
        );
    }

    static String transcript(JsonNode response) {
        if (response == null) {
            return "";
        }
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
        String body = exception.getResponseBodyAsString();
        if (body == null || body.isBlank()) return false;
        String normalized = body.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("success_with_no_valid_fragment")
                || normalized.contains("asr_response_have_no_words")
                || normalized.contains("no valid speech")
                || normalized.contains("no speech")
                || normalized.contains("no words")
                || normalized.contains("silent audio")
                || normalized.contains("audio is silent");
    }
}
