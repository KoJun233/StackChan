package com.kj.stackchan.notification;

import java.util.UUID;

public record NotificationIntegrationPrincipal(UUID integrationId, UUID deviceId, String name) {
}
