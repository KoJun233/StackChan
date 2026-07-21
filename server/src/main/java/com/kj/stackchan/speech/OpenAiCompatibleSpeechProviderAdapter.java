package com.kj.stackchan.speech;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class OpenAiCompatibleSpeechProviderAdapter implements SpeechProviderAdapter {

    private static final Duration PROVIDER_TIMEOUT = Duration.ofSeconds(60);

    private final WebClient.Builder webClientBuilder;

    OpenAiCompatibleSpeechProviderAdapter(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
    }

    @Override
    public String transcribe(ResolvedSpeechSettings settings, byte[] wavAudio) {
        if (settings.asrMode() != SpeechAccessMode.NON_REALTIME) {
            throw new SpeechProviderUnavailableException(
                    "openai_compatible_asr_realtime_unsupported"
            );
        }
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("model", settings.asrModel());
        multipart.part("language", "zh");
        multipart.part("response_format", "json");
        multipart.part("file", new NamedByteArrayResource(wavAudio, "voice.wav"))
                .contentType(new MediaType("audio", "wav"));

        JsonNode response = client(settings)
                .post()
                .uri(settings.baseUrl() + "/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(PROVIDER_TIMEOUT);
        return response == null || response.path("text").isMissingNode()
                ? ""
                : response.path("text").asText("").trim();
    }

    @Override
    public byte[] synthesize(ResolvedSpeechSettings settings, String text) {
        if (settings.ttsMode() != SpeechAccessMode.NON_REALTIME) {
            throw new SpeechProviderUnavailableException(
                    "openai_compatible_tts_realtime_unsupported"
            );
        }
        byte[] audio = client(settings)
                .post()
                .uri(settings.baseUrl() + "/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(new MediaType("audio", "wav"), MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(Map.of(
                        "model", settings.ttsModel(),
                        "input", text,
                        "voice", settings.ttsVoice(),
                        "response_format", "wav"
                ))
                .retrieve()
                .bodyToMono(byte[].class)
                .block(PROVIDER_TIMEOUT);
        return requireWav(audio);
    }

    private WebClient client(ResolvedSpeechSettings settings) {
        return webClientBuilder.clone()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.apiKey())
                .build();
    }

    static byte[] requireWav(byte[] audio) {
        if (audio == null || audio.length < 44
                || audio[0] != 'R' || audio[1] != 'I' || audio[2] != 'F' || audio[3] != 'F'
                || audio[8] != 'W' || audio[9] != 'A' || audio[10] != 'V' || audio[11] != 'E') {
            throw new SpeechProviderUnavailableException();
        }
        return audio;
    }

    private static final class NamedByteArrayResource extends ByteArrayResource {

        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
