package com.kj.stackchan.workday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkdayPilotCompletionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkdayPilotCompletionScheduler.class);

    private final WorkdayPilotCompletionService completionService;

    public WorkdayPilotCompletionScheduler(WorkdayPilotCompletionService completionService) {
        this.completionService = completionService;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT30S")
    public void queueCompletedPilotNotifications() {
        for (var deviceId : completionService.candidateDeviceIds()) {
            try {
                completionService.queueIfComplete(deviceId);
            } catch (RuntimeException exception) {
                LOGGER.warn("Workday pilot completion notification failed for device {}: {}",
                        deviceId, exception.getClass().getSimpleName());
            }
        }
    }
}
