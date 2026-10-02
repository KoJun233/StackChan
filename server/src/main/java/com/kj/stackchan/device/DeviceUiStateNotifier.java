package com.kj.stackchan.device;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Only committed server facts may invalidate a device's cached menu and motion gates. */
@Component
public class DeviceUiStateNotifier {
    private final ObjectProvider<DeviceConnectionRegistry> registries;

    public DeviceUiStateNotifier(ObjectProvider<DeviceConnectionRegistry> registries) {
        this.registries = registries;
    }

    public void changed(UUID deviceId) {
        Runnable publish = () -> {
            var registry = registries.getIfAvailable();
            if (registry != null) registry.sendDeviceUiStateChanged(deviceId);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
    }
}
