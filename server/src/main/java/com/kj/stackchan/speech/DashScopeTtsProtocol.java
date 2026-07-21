package com.kj.stackchan.speech;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class DashScopeTtsProtocol {

    private DashScopeTtsProtocol() {
    }

    static String runTask(
            ObjectMapper objectMapper,
            String taskId,
            String model,
            String voice
    ) {
        return write(objectMapper, Map.of(
                "header", header("run-task", taskId),
                "payload", Map.of(
                        "task_group", "audio",
                        "task", "tts",
                        "function", "SpeechSynthesizer",
                        "model", model,
                        "parameters", Map.of(
                                "voice", voice,
                                "format", "wav",
                                "sample_rate", 16000
                        ),
                        "input", Map.of()
                )
        ));
    }

    static String continueTask(ObjectMapper objectMapper, String taskId, String text) {
        return write(objectMapper, Map.of(
                "header", header("continue-task", taskId),
                "payload", Map.of("input", Map.of("text", text))
        ));
    }

    static String finishTask(ObjectMapper objectMapper, String taskId) {
        return write(objectMapper, Map.of(
                "header", header("finish-task", taskId),
                "payload", Map.of("input", Map.of())
        ));
    }

    static ServerEvent parseServerEvent(ObjectMapper objectMapper, String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            return new ServerEvent(
                    root.path("header").path("event").asText(""),
                    root.path("header").path("error_code").asText("")
            );
        } catch (JsonProcessingException exception) {
            throw new SpeechProviderUnavailableException("dashscope_tts_ws_event_invalid", exception);
        }
    }

    private static Map<String, ?> header(String action, String taskId) {
        return Map.of(
                "action", action,
                "task_id", taskId,
                "streaming", "duplex"
        );
    }

    private static String write(ObjectMapper objectMapper, Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record ServerEvent(String event, String errorCode) {
    }
}
