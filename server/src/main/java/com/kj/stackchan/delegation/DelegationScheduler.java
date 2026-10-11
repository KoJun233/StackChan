package com.kj.stackchan.delegation;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.delegation.scheduler-enabled", matchIfMissing=true)
public class DelegationScheduler {
    private final DelegationService service;
    private volatile boolean ready;
    public DelegationScheduler(DelegationService service) { this.service=service; }
    @EventListener(ApplicationReadyEvent.class)
    public void ready() { service.recover(true); ready=true; }
    @Scheduled(fixedDelay = 30000)
    public void tick() { if(ready) service.tick(); }
}
