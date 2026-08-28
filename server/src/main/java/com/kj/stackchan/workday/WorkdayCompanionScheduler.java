package com.kj.stackchan.workday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkdayCompanionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkdayCompanionScheduler.class);

    private final WorkdayRuntimeService runtimeService;
    private final WorkdayCompanionService companionService;

    public WorkdayCompanionScheduler(
            WorkdayRuntimeService runtimeService,
            WorkdayCompanionService companionService
    ) {
        this.runtimeService = runtimeService;
        this.companionService = companionService;
    }

    @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT10S")
    public void processActiveWorkdays() {
        for (var deviceId : runtimeService.activeDeviceIds()) {
            try {
                companionService.processActiveDevice(deviceId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Workday companion cycle failed for device {}: {}",
                        deviceId, exception.getClass().getSimpleName());
            }
        }
    }
}
