package com.kj.stackchan.agent;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.reminder.ReminderService;
import org.springframework.ai.tool.annotation.Tool;

public class NextReminderTool {
    public static final String ID = "next_device_reminder";
    private final UUID deviceId;
    private final UUID roleId;
    private final ReminderService reminderService;
    private final ObjectMapper objectMapper;

    public NextReminderTool(UUID deviceId, ReminderService reminderService, ObjectMapper objectMapper) {
        this(deviceId, com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID, reminderService, objectMapper);
    }
    public NextReminderTool(UUID deviceId, UUID roleId, ReminderService reminderService, ObjectMapper objectMapper) {
        this.deviceId = deviceId; this.reminderService = reminderService; this.objectMapper = objectMapper;
        this.roleId = roleId;
    }

    @Tool(name = ID, description = "返回当前认证设备的下一条待处理提醒；不接受模型指定的设备。")
    public String nextReminder() {
        ReminderService.ReminderSnapshot next = reminderService.nextPending(deviceId, roleId);
        return json(next == null ? new Result(false, null, null, null) :
                new Result(true, next.content(), next.scheduledAt().toString(), next.zoneId()));
    }

    private String json(Result result) {
        try { return objectMapper.writeValueAsString(result); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize reminder", exception); }
    }
    private record Result(boolean found, String content, String scheduledAt, String zoneId) { }
}
