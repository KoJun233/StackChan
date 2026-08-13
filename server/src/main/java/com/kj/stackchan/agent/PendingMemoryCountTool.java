package com.kj.stackchan.agent;

import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.memory.LongTermMemoryService;
import org.springframework.ai.tool.annotation.Tool;

public class PendingMemoryCountTool {
    public static final String ID = "pending_device_memory_count";
    private final UUID deviceId;
    private final UUID roleId;
    private final LongTermMemoryService memoryService;
    private final ObjectMapper objectMapper;

    public PendingMemoryCountTool(UUID deviceId, LongTermMemoryService memoryService, ObjectMapper objectMapper) {
        this(deviceId, com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID, memoryService, objectMapper);
    }
    public PendingMemoryCountTool(UUID deviceId, UUID roleId, LongTermMemoryService memoryService, ObjectMapper objectMapper) {
        this.deviceId = deviceId; this.memoryService = memoryService; this.objectMapper = objectMapper;
        this.roleId = roleId;
    }

    @Tool(name = ID, description = "返回当前认证设备可见的待确认记忆数量；不接受模型指定的设备。")
    public String pendingCount() {
        try { return objectMapper.writeValueAsString(new Result(memoryService.pendingVisibleCount(roleId, deviceId))); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize memory count", exception); }
    }
    private record Result(long count) { }
}
