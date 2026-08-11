package com.kj.stackchan.notification;

import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class NotificationMcpTools {

    private final ExternalNotificationService notificationService;

    NotificationMcpTools(ExternalNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Tool(
            name = "push_notification",
            description = "把确定性原文加入当前集成绑定设备的可靠语音通知队列。相同幂等键可安全重试。"
    )
    public ExternalNotificationService.PublicNotificationSnapshot pushNotification(
            @ToolParam(description = "需要由机器人原文播报的 1–500 字正文") String content,
            @ToolParam(description = "调用方生成的 1–128 字幂等键") String idempotencyKey,
            @ToolParam(description = "60–86400 秒；省略时为 24 小时", required = false) Integer expiresInSeconds
    ) {
        return notificationService.create(principal(), idempotencyKey, content, expiresInSeconds).notification();
    }

    @Tool(
            name = "get_notification_status",
            description = "查询当前集成创建的一条语音通知状态；不能查询其他集成。"
    )
    public ExternalNotificationService.PublicNotificationSnapshot getNotificationStatus(
            @ToolParam(description = "push_notification 返回的通知 UUID") String notificationId
    ) {
        try {
            return notificationService.get(principal(), UUID.fromString(notificationId));
        } catch (IllegalArgumentException exception) {
            throw new NotificationApiException(
                    HttpStatus.BAD_REQUEST, "notification_invalid_request", "通知 ID 无效。"
            );
        }
    }

    private NotificationIntegrationPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof NotificationIntegrationPrincipal principal)) {
            throw new NotificationApiException(
                    HttpStatus.UNAUTHORIZED,
                    "notification_authentication_failed",
                    "通知令牌无效或已过期。"
            );
        }
        return principal;
    }
}
