package com.kj.stackchan.agent;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import org.springframework.ai.tool.annotation.Tool;

public class PersonalTasksTool {
    public static final String ID = "current_personal_tasks";

    private final UUID deviceId;
    private final UUID roleId;
    private final PersonalTaskService taskService;
    private final ObjectMapper objectMapper;

    public PersonalTasksTool(UUID deviceId, UUID roleId, PersonalTaskService taskService, ObjectMapper objectMapper) {
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @Tool(name = ID, description = "读取当前设备和角色尚未完成的个人待办，最多返回二十条；不得创建或修改待办。")
    public String currentTasks() {
        List<TaskResult> tasks = taskService.openForAgent(deviceId, roleId).stream()
                .map(task -> new TaskResult(task.id(), task.title(), task.priority(),
                        task.dueAt() == null ? null : task.dueAt().toString()))
                .toList();
        try {
            return objectMapper.writeValueAsString(new Result(tasks, tasks.size()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize personal tasks", exception);
        }
    }

    private record TaskResult(UUID id, String title, PersonalTaskPriority priority, String dueAt) { }
    private record Result(List<TaskResult> tasks, int count) { }
}
