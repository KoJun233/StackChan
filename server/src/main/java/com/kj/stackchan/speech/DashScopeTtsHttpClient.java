package com.kj.stackchan.speech;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
class DashScopeTtsHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashScopeTtsHttpClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    private static final int MAX_PROVIDER_MESSAGE_LENGTH = 240;

    private final WebClient.Builder webClientBuilder;

    DashScopeTtsHttpClient(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    byte[] synthesize(
            URI endpoint,
            String apiKey,
            String model,
            String voice,
            String text
    ) {
        JsonNode response = requestSynthesis(endpoint, apiKey, model, voice, text);
        String audioUrl = response == null
                ? ""
                : response.path("output").path("audio").path("url").asText("").trim();
        URI downloadUri;
        try {
            downloadUri = DashScopeEndpoints.validatedDownloadUri(audioUrl);
        } catch (SpeechProviderUnavailableException exception) {
            throw new SpeechProviderUnavailableException("dashscope_tts_http_result_invalid", exception);
        }
        byte[] audio = downloadAudio(downloadUri);
        if (audio != null && audio.length > MAX_AUDIO_BYTES) {
            throw new SpeechProviderUnavailableException("dashscope_tts_http_audio_too_large");
        }
        return audio;
    }

    private JsonNode requestSynthesis(
            URI endpoint,
            String apiKey,
            String model,
            String voice,
            String text
    ) {
        try {
            return webClientBuilder.clone()
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .build()
                    .post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request(model, voice, text))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(PROVIDER_TIMEOUT);
        } catch (WebClientResponseException exception) {
            ProviderError providerError = providerError(exception);
            LOGGER.warn(
                    "DashScope TTS HTTP request rejected: status={} request_id={} provider_code={} provider_message={} cause_type={}",
                    exception.getStatusCode().value(),
                    providerError.requestId(),
                    providerError.code(),
                    providerError.message(),
                    exception.getClass().getSimpleName()
            );
            throw new SpeechProviderUnavailableException(
                    SpeechProviderUnavailableException.httpDiagnosticCode(
                            "dashscope_tts_http_request", exception.getStatusCode().value()
                    ),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new SpeechProviderUnavailableException("dashscope_tts_http_request", exception);
        }
    }

    private byte[] downloadAudio(URI downloadUri) {
        try {
            return webClientBuilder.clone()
                    .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_AUDIO_BYTES))
                    .build()
                    .get()
                    .uri(downloadUri)
                    .accept(new MediaType("audio", "wav"), MediaType.APPLICATION_OCTET_STREAM)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block(PROVIDER_TIMEOUT);
        } catch (WebClientResponseException exception) {
            throw new SpeechProviderUnavailableException(
                    SpeechProviderUnavailableException.httpDiagnosticCode(
                            "dashscope_tts_http_download", exception.getStatusCode().value()
                    ),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new SpeechProviderUnavailableException("dashscope_tts_http_download", exception);
        }
    }

    static Map<String, ?> request(String model, String voice, String text) {
        return Map.of(
                "model", model,
                "input", Map.of(
                        "text", text,
                        "voice", voice
                )
        );
    }

    static ProviderError providerError(WebClientResponseException exception) {
        String requestId = safeToken(exception.getHeaders().getFirst("X-Request-Id"));
        String code = "-";
        String message = "-";
        try {
            JsonNode body = OBJECT_MAPPER.readTree(new String(
                    exception.getResponseBodyAsByteArray(), StandardCharsets.UTF_8
            ));
            if ("-".equals(requestId)) {
                requestId = safeToken(body.path("request_id").asText(""));
            }
            code = safeToken(body.path("code").asText(""));
            message = safeMessage(body.path("message").asText(""));
        } catch (RuntimeException | java.io.IOException ignored) {
            // Keep placeholders; never log the raw provider body.
        }
        return new ProviderError(requestId, code, message);
    }

    private static String safeToken(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            return "-";
        }
        return value;
    }

    private static String safeMessage(String value) {
        if (value == null) {
            return "-";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (sanitized.isEmpty()) {
            return "-";
        }
        return sanitized.substring(0, Math.min(sanitized.length(), MAX_PROVIDER_MESSAGE_LENGTH));
    }

    record ProviderError(String requestId, String code, String message) {
    }
}
