package com.kj.stackchan.agent;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;

public class CapabilityListTool {

    public static final String ID = "list_agent_capabilities";

    private final List<String> tools;
    private final List<String> skills;
    private final ObjectMapper objectMapper;

    public CapabilityListTool(List<String> tools, List<String> skills, ObjectMapper objectMapper) {
        this.tools = List.copyOf(tools);
        this.skills = List.copyOf(skills);
        this.objectMapper = objectMapper;
    }

    @Tool(
            name = ID,
            description = "列出当前配置授权给 Agent 的 Tool 和 Skill 名称；发送通知仍须当前用户明确要求。不返回连接地址、schema 或认证信息。"
    )
    public String listCapabilities() {
        try {
            return objectMapper.writeValueAsString(new CapabilityResult(tools, skills));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize capabilities", exception);
        }
    }

    private record CapabilityResult(List<String> tools, List<String> skills) {
    }
}
