package com.kj.stackchan.agent;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.task.PersonalTaskStatus;
import com.kj.stackchan.workday.WorkdaySettingsService;
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
        WorkdaySettingsService settingsService = mock(WorkdaySettingsService.class);
        when(service.openForAgent(deviceId, roleId)).thenReturn(List.of(new PersonalTaskService.TaskSnapshot(
                taskId, deviceId, roleId, "整理会议材料", "不应发送给模型的备注", PersonalTaskPriority.HIGH,
                PersonalTaskStatus.OPEN, Instant.parse("2026-08-31T01:00:00Z"), "Asia/Shanghai", null,
                null, Instant.EPOCH, Instant.EPOCH)));
        when(settingsService.resolve(deviceId)).thenReturn(settings(deviceId));
        when(service.completedTodayForAgent(deviceId, roleId, java.time.ZoneId.of("Asia/Shanghai")))
                .thenReturn(List.of(new PersonalTaskService.CompletedTaskItem("发送周报")));

        String result = new PersonalTasksTool(
                deviceId, roleId, service, settingsService, new ObjectMapper()).currentTasks();
        JsonNode json = new ObjectMapper().readTree(result);

        assertThat(json.path("count").asInt()).isEqualTo(1);
        assertThat(json.path("tasks").get(0).path("title").asText()).isEqualTo("整理会议材料");
        assertThat(json.path("completedTodayCount").asInt()).isEqualTo(1);
        assertThat(json.path("completedToday").get(0).path("title").asText()).isEqualTo("发送周报");
        assertThat(json.path("completedToday").get(0).has("completedAt")).isFalse();
        assertThat(json.path("zoneId").asText()).isEqualTo("Asia/Shanghai");
        assertThat(result).doesNotContain("备注").doesNotContain("notes");
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings(UUID deviceId) {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                deviceId, true, 31, LocalTime.of(9, 0), LocalTime.of(18, 0),
                50, 10, 10, 45, "上海", 31.2, 121.5, "Asia/Shanghai", Instant.EPOCH);
    }
}
