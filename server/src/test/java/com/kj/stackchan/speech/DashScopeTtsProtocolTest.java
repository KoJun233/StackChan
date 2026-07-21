package com.kj.stackchan.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeTtsProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsRealtimeTtsLifecycleWithTheExactConfiguredModel() throws Exception {
        JsonNode runTask = objectMapper.readTree(DashScopeTtsProtocol.runTask(
                objectMapper,
                "task-id",
                "future-tts-model",
                "custom-voice"
        ));
        JsonNode continueTask = objectMapper.readTree(DashScopeTtsProtocol.continueTask(
                objectMapper,
                "task-id",
                "你好"
        ));
        JsonNode finishTask = objectMapper.readTree(DashScopeTtsProtocol.finishTask(
                objectMapper,
                "task-id"
        ));

        assertThat(runTask.path("header").path("action").asText()).isEqualTo("run-task");
        assertThat(runTask.path("payload").path("task").asText()).isEqualTo("tts");
        assertThat(runTask.path("payload").path("function").asText())
                .isEqualTo("SpeechSynthesizer");
        assertThat(runTask.path("payload").path("model").asText())
                .isEqualTo("future-tts-model");
        assertThat(runTask.path("payload").path("parameters").path("voice").asText())
                .isEqualTo("custom-voice");
        assertThat(runTask.path("payload").path("parameters").path("format").asText())
                .isEqualTo("wav");
        assertThat(runTask.path("payload").path("parameters").path("sample_rate").asInt())
                .isEqualTo(16000);
        assertThat(continueTask.path("header").path("action").asText())
                .isEqualTo("continue-task");
        assertThat(continueTask.path("payload").path("input").path("text").asText())
                .isEqualTo("你好");
        assertThat(finishTask.path("header").path("action").asText())
                .isEqualTo("finish-task");
    }

    @Test
    void parsesRealtimeTtsServerEvents() {
        DashScopeTtsProtocol.ServerEvent event = DashScopeTtsProtocol.parseServerEvent(
                objectMapper,
                """
                        {"header":{"event":"task-failed","error_code":"InvalidParameter"}}
                        """
        );

        assertThat(event.event()).isEqualTo("task-failed");
        assertThat(event.errorCode()).isEqualTo("InvalidParameter");
    }
}
