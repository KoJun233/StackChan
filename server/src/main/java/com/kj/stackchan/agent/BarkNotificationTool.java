package com.kj.stackchan.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.delegation.BarkNotificationService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class BarkNotificationTool {
    public static final String ID="send_phone_notification";
    private final AgentInvocationContext context;
    private final BarkNotificationService notifications;
    private final ObjectMapper mapper;
    public BarkNotificationTool(AgentInvocationContext context,BarkNotificationService notifications,ObjectMapper mapper) {
        this.context=context; this.notifications=notifications; this.mapper=mapper;
    }

    @Tool(name=ID,description="Send a Bark notification to the user's configured phone. Use only when the user explicitly requests phone delivery. "
            + "Provide a title (1-80 Unicode characters) and body (1-2800 UTF-8 bytes); shorten longer content before calling. "
            + "No recipient, server URL or credentials are accepted. One notification per user turn or scheduled run; never retry UNKNOWN. "
            + "SENT means Bark accepted the message, not that the phone received it or the user read it. FAILED/UNKNOWN are not success. "
            + "For scheduled BARK tasks, ordinary final-result delivery is automatic; call this only when the task itself needs this action, as its final step.")
    public String send(@ToolParam(description="Plain text notification title, at most 80 Unicode characters") String title,
                       @ToolParam(description="Plain text notification content, at most 2800 UTF-8 bytes") String body) {
        try { return mapper.writeValueAsString(notifications.send(context,title,body)); }
        catch(Exception ignored) { throw new IllegalStateException("PHONE_PUSH_UNAVAILABLE"); }
    }
}
