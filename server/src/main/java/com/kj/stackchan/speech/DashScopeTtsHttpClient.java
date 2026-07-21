package com.kj.stackchan.speech;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
class DashScopeTtsHttpClient {

    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;

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
                        "voice", voice,
                        "format", "wav",
                        "sample_rate", 16000
                )
        );
    }
}
