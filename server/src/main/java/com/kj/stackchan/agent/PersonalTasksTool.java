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

    @Tool(name = ID, description = "读取当前设备和角色尚未完成的个人待办，以及设备时区下今天完成的待办；不得创建或修改待办。")
    public String currentTasks() {
        List<TaskResult> tasks = taskService.openForAgent(deviceId, roleId).stream()
                .map(task -> new TaskResult(task.id(), task.title(), task.priority(),
                        task.dueAt() == null ? null : task.dueAt().toString()))
                .toList();
        String zoneId = workdaySettingsService.resolve(deviceId).zoneId();
        List<CompletedTaskResult> completedToday = taskService.completedTodayForAgent(
                        deviceId, roleId, ZoneId.of(zoneId)).stream()
                .map(task -> new CompletedTaskResult(task.title()))
                .toList();
        try {
            return objectMapper.writeValueAsString(new Result(
                    tasks, tasks.size(), completedToday, completedToday.size(), zoneId));
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
            String zoneId
    ) { }
}
