package com.kj.stackchan.reminder;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(
        name = {"companion.device-transport-enabled", "companion.reminder-scheduler-enabled"},
        havingValue = "true",
        matchIfMissing = true
)
public class ReminderScheduler {

    private final ReminderDeliveryService reminderDeliveryService;

    public ReminderScheduler(ReminderDeliveryService reminderDeliveryService) {
        this.reminderDeliveryService = reminderDeliveryService;
    }

    @Scheduled(fixedDelay = 1000)
    public void dispatchDueReminders() {
        reminderDeliveryService.dispatchDueReminders();
    }

    @Scheduled(fixedDelay = 60000)
    public void recoverStaleDispatches() {
        reminderDeliveryService.recoverStaleDispatches();
    }
}
