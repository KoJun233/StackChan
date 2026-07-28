package com.kj.stackchan.expression;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = {"companion.device-transport-enabled", "companion.expression-pack-scheduler-enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class ExpressionPackScheduler {

    private final ExpressionPackService service;

    public ExpressionPackScheduler(ExpressionPackService service) {
        this.service = service;
    }

    @Scheduled(fixedDelay = 5000)
    public void dispatch() {
        service.recoverStaleInstalls();
        service.dispatchReady();
    }
}
