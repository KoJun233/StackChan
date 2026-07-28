package com.kj.stackchan.device;

import java.util.UUID;

import com.kj.stackchan.expression.ExpressionPackService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceExpressionPackCoordinator {

    private final ExpressionPackService service;

    public DeviceExpressionPackCoordinator(ExpressionPackService service) {
        this.service = service;
    }

    public void sendCurrent(UUID deviceId) {
        service.syncConnectedDevice(deviceId);
    }
}
