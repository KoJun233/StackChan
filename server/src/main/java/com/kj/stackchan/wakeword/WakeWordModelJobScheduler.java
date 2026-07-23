package com.kj.stackchan.wakeword;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {"companion.device-transport-enabled", "companion.wake-model-scheduler-enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class WakeWordModelJobScheduler {

    private final WakeWordModelJobService jobService;

    public WakeWordModelJobScheduler(WakeWordModelJobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        jobService.dispatchReadyJobs();
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStaleInstalls() {
        jobService.recoverStaleInstalls();
    }

}
