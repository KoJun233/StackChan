package com.kj.stackchan.speech;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DashScopeAsrProtocolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsTheDocumentedRunAndFinishEvents() throws Exception {
        JsonNode runTask = objectMapper.readTree(
                DashScopeAsrProtocol.runTask(objectMapper, "task-id", "fun-asr-realtime")
        );
        JsonNode finishTask = objectMapper.readTree(
                DashScopeAsrProtocol.finishTask(objectMapper, "task-id")
        );

        assertThat(runTask.path("header").path("action").asText()).isEqualTo("run-task");
        assertThat(runTask.path("header").path("streaming").asText()).isEqualTo("duplex");
        assertThat(runTask.path("payload").path("parameters").path("format").asText()).isEqualTo("pcm");
        assertThat(runTask.path("payload").path("parameters").path("sample_rate").asInt()).isEqualTo(16000);
        assertThat(finishTask.path("header").path("action").asText()).isEqualTo("finish-task");
    }

    @Test
    void parsesFinalRecognitionResultsWithoutKeepingIntermediateText() {
        DashScopeAsrProtocol.ServerEvent event = DashScopeAsrProtocol.parseServerEvent(objectMapper, """
                {
                  "header":{"event":"result-generated"},
                  "payload":{"output":{"sentence":{"text":"好，我知道了","sentence_end":true}}}
                }
                """);

        assertThat(event.event()).isEqualTo("result-generated");
        assertThat(event.text()).isEqualTo("好，我知道了");
        assertThat(event.sentenceEnd()).isTrue();
    }
}
