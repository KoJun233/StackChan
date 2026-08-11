package com.kj.stackchan.notification;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationMcpConfiguration {

    @Bean
    ToolCallbackProvider notificationMcpToolCallbacks(ExternalNotificationService notificationService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(new NotificationMcpTools(notificationService))
                .build();
    }
}
