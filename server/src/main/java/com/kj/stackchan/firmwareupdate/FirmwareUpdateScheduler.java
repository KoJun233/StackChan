package com.kj.stackchan.firmwareupdate;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {"companion.device-transport-enabled", "companion.firmware-update-scheduler-enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class FirmwareUpdateScheduler {

    private final FirmwareUpdateService service;

    public FirmwareUpdateScheduler(FirmwareUpdateService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        service.dispatchReadyJobs();
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStaleInstalls() {
        service.recoverStaleInstalls();
    }
}
