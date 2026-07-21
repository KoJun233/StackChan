package com.kj.stackchan.speech;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class DashScopeAsrProtocol {

    private DashScopeAsrProtocol() {
    }

    static String runTask(ObjectMapper objectMapper, String taskId, String model) {
        return write(objectMapper, Map.of(
                "header", Map.of(
                        "action", "run-task",
                        "task_id", taskId,
                        "streaming", "duplex"
                ),
                "payload", Map.of(
                        "task_group", "audio",
                        "task", "asr",
                        "function", "recognition",
                        "model", model,
                        "parameters", Map.of(
                                "format", "pcm",
                                "sample_rate", 16000
                        ),
                        "input", Map.of()
                )
        ));
    }

    static String finishTask(ObjectMapper objectMapper, String taskId) {
        return write(objectMapper, Map.of(
                "header", Map.of(
                        "action", "finish-task",
                        "task_id", taskId,
                        "streaming", "duplex"
                ),
                "payload", Map.of("input", Map.of())
        ));
    }

    static ServerEvent parseServerEvent(ObjectMapper objectMapper, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String event = root.path("header").path("event").asText("");
            JsonNode sentence = root.path("payload").path("output").path("sentence");
            return new ServerEvent(
                    event,
                    sentence.path("text").asText("").trim(),
                    sentence.path("sentence_end").asBoolean(false),
                    root.path("header").path("error_code").asText("")
            );
        } catch (JsonProcessingException exception) {
            throw new SpeechProviderUnavailableException("dashscope_asr_event_invalid", exception);
        }
    }

    private static String write(ObjectMapper objectMapper, Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record ServerEvent(String event, String text, boolean sentenceEnd, String errorCode) {
    }
}
