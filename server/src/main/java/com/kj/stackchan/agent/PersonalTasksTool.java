package com.kj.stackchan.agent;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.workday.WorkdaySettingsService;
import org.springframework.ai.tool.annotation.Tool;

public class PersonalTasksTool {
    public static final String ID = "current_personal_tasks";

    private final UUID deviceId;
    private final UUID roleId;
    private final PersonalTaskService taskService;
    private final WorkdaySettingsService workdaySettingsService;
    private final ObjectMapper objectMapper;

    public PersonalTasksTool(
            UUID deviceId,
            UUID roleId,
            PersonalTaskService taskService,
            WorkdaySettingsService workdaySettingsService,
            ObjectMapper objectMapper
    ) {
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.taskService = taskService;
        this.workdaySettingsService = workdaySettingsService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = ID, description = "读取当前设备和角色的待办及设备时区下今日完成项。总数使用 totalCount 和 completedTodayTotalCount；count 和 completedTodayCount 仅为本次展示数量，hasMore 为真时不能称已列出全部。不得创建或修改待办。")
    public String currentTasks() {
        String zoneId = workdaySettingsService.resolve(deviceId).zoneId();
        PersonalTaskService.AgentTaskProgress progress = taskService.progressForAgent(
                deviceId, roleId, ZoneId.of(zoneId));
        List<TaskResult> tasks = progress.tasks().stream()
                .map(task -> new TaskResult(task.id(), task.title(), task.priority(),
                        task.dueAt() == null ? null : task.dueAt().toString()))
                .toList();
        List<CompletedTaskResult> completedToday = progress.completedToday().stream()
                .map(task -> new CompletedTaskResult(task.title()))
                .toList();
        try {
            return objectMapper.writeValueAsString(new Result(
                    tasks, tasks.size(), completedToday, completedToday.size(), zoneId,
                    progress.totalCount(), progress.completedTodayTotalCount(),
                    progress.totalCount() > tasks.size(),
                    progress.completedTodayTotalCount() > completedToday.size(), progress.localDate().toString()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize personal tasks", exception);
        }
    }

    private record TaskResult(UUID id, String title, PersonalTaskPriority priority, String dueAt) { }
    private record CompletedTaskResult(String title) { }
    private record Result(
            List<TaskResult> tasks,
            int count,
            List<CompletedTaskResult> completedToday,
            int completedTodayCount,
            String zoneId,
            long totalCount,
            long completedTodayTotalCount,
            boolean hasMore,
            boolean completedTodayHasMore,
            String localDate
    ) { }
}
