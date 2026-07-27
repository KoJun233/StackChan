package com.kj.stackchan.interaction;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {"companion.device-transport-enabled", "companion.reminder-scheduler-enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class ProactiveInteractionScheduler {

    private final ProactiveInteractionService service;

    public ProactiveInteractionScheduler(ProactiveInteractionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "PT1M")
    public void generateDueGreetings() {
        service.generateDueGreetings();
    }
}
