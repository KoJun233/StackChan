package com.kj.stackchan.agent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.task.PersonalTaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PersonalTasksToolTest {

    @Test
    void returnsOnlyMinimumOpenTaskFieldsWithoutNotes() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        PersonalTaskService service = mock(PersonalTaskService.class);
        when(service.openForAgent(deviceId, roleId)).thenReturn(List.of(new PersonalTaskService.TaskSnapshot(
                taskId, deviceId, roleId, "整理会议材料", "不应发送给模型的备注", PersonalTaskPriority.HIGH,
                PersonalTaskStatus.OPEN, Instant.parse("2026-08-31T01:00:00Z"), "Asia/Shanghai", null,
                null, Instant.EPOCH, Instant.EPOCH)));

        String result = new PersonalTasksTool(deviceId, roleId, service, new ObjectMapper()).currentTasks();
        JsonNode json = new ObjectMapper().readTree(result);

        assertThat(json.path("count").asInt()).isEqualTo(1);
        assertThat(json.path("tasks").get(0).path("title").asText()).isEqualTo("整理会议材料");
        assertThat(result).doesNotContain("备注").doesNotContain("notes");
    }
}
