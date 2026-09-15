package com.kj.stackchan.agent;

import java.time.Instant;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.ZoneId;
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
        var task = new PersonalTaskService.TaskSnapshot(
                taskId, deviceId, roleId, "整理会议材料", "不应发送给模型的备注", PersonalTaskPriority.HIGH,
                PersonalTaskStatus.OPEN, Instant.parse("2026-08-31T01:00:00Z"), "Asia/Shanghai", null,
                null, Instant.EPOCH, Instant.EPOCH);
        when(settingsService.resolve(deviceId)).thenReturn(settings(deviceId));
        when(service.progressForAgent(deviceId, roleId, ZoneId.of("Asia/Shanghai")))
                .thenReturn(new PersonalTaskService.AgentTaskProgress(List.of(task), 1,
                        List.of(new PersonalTaskService.CompletedTaskItem("发送周报")), 1, LocalDate.of(2026, 8, 31)));

        String result = new PersonalTasksTool(
                deviceId, roleId, service, settingsService, new ObjectMapper()).currentTasks();
        JsonNode json = new ObjectMapper().readTree(result);

        assertThat(json.path("count").asInt()).isEqualTo(1);
        assertThat(json.path("tasks").get(0).path("title").asText()).isEqualTo("整理会议材料");
        assertThat(json.path("completedTodayCount").asInt()).isEqualTo(1);
        assertThat(json.path("completedToday").get(0).path("title").asText()).isEqualTo("发送周报");
        assertThat(json.path("completedToday").get(0).has("completedAt")).isFalse();
        assertThat(json.path("zoneId").asText()).isEqualTo("Asia/Shanghai");
        assertThat(json.path("totalCount").asLong()).isEqualTo(1);
        assertThat(json.path("completedTodayTotalCount").asLong()).isEqualTo(1);
        assertThat(json.path("hasMore").asBoolean()).isFalse();
        assertThat(json.path("completedTodayHasMore").asBoolean()).isFalse();
        assertThat(json.path("localDate").asText()).isEqualTo("2026-08-31");
        assertThat(result).doesNotContain("备注").doesNotContain("notes");
    }

    @Test
    void distinguishesTotalsFromBoundedLists() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        var service = mock(PersonalTaskService.class);
        var settings = mock(WorkdaySettingsService.class);
        when(settings.resolve(deviceId)).thenReturn(settings(deviceId));
        var task = new PersonalTaskService.TaskSnapshot(UUID.randomUUID(), deviceId, roleId, "待办",
                "私密备注", PersonalTaskPriority.NORMAL, PersonalTaskStatus.OPEN, null,
                "Asia/Shanghai", null, null, Instant.EPOCH, Instant.EPOCH);
        when(service.progressForAgent(deviceId, roleId, ZoneId.of("Asia/Shanghai")))
                .thenReturn(new PersonalTaskService.AgentTaskProgress(java.util.Collections.nCopies(20, task), 21,
                        java.util.Collections.nCopies(10, new PersonalTaskService.CompletedTaskItem("已完成")),
                        11, LocalDate.of(2026, 8, 31)));
        JsonNode json = new ObjectMapper().readTree(new PersonalTasksTool(
                deviceId, roleId, service, settings, new ObjectMapper()).currentTasks());
        assertThat(json.path("count").asInt()).isEqualTo(20);
        assertThat(json.path("totalCount").asLong()).isEqualTo(21);
        assertThat(json.path("completedTodayCount").asInt()).isEqualTo(10);
        assertThat(json.path("completedTodayTotalCount").asLong()).isEqualTo(11);
        assertThat(json.path("hasMore").asBoolean()).isTrue();
        assertThat(json.path("completedTodayHasMore").asBoolean()).isTrue();
        assertThat(json.toString()).doesNotContain("私密备注");
    }

    @Test
    void emptyProgressDoesNotClaimMoreTasks() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        var service = mock(PersonalTaskService.class);
        var settings = mock(WorkdaySettingsService.class);
        when(settings.resolve(deviceId)).thenReturn(settings(deviceId));
        when(service.progressForAgent(deviceId, roleId, ZoneId.of("Asia/Shanghai")))
                .thenReturn(new PersonalTaskService.AgentTaskProgress(List.of(), 0, List.of(), 0,
                        LocalDate.of(2026, 8, 31)));
        JsonNode json = new ObjectMapper().readTree(new PersonalTasksTool(
                deviceId, roleId, service, settings, new ObjectMapper()).currentTasks());
        assertThat(json.path("tasks").isEmpty()).isTrue();
        assertThat(json.path("totalCount").asLong()).isZero();
        assertThat(json.path("completedTodayTotalCount").asLong()).isZero();
        assertThat(json.path("hasMore").asBoolean()).isFalse();
        assertThat(json.path("completedTodayHasMore").asBoolean()).isFalse();
    }

    private WorkdaySettingsService.WorkdaySettingsSnapshot settings(UUID deviceId) {
        return new WorkdaySettingsService.WorkdaySettingsSnapshot(
                deviceId, true, 31, LocalTime.of(9, 0), LocalTime.of(18, 0),
                50, 10, 10, 45, "上海", 31.2, 121.5, "Asia/Shanghai", Instant.EPOCH);
    }
}
