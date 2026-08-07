package com.kj.stackchan.device;

import java.util.UUID;

import com.kj.stackchan.reminder.ReminderDeliveryService;
import com.kj.stackchan.expression.ExpressionPackService;
import com.kj.stackchan.firmwareupdate.FirmwareUpdateService;
import com.kj.stackchan.wakeword.WakeWordModelJobService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class DeviceCommandAcknowledgementRouter implements DeviceCommandAcknowledgementService {

    private final WakeWordModelJobService wakeWordModelJobService;
    private final ReminderDeliveryService reminderDeliveryService;
    private final ExpressionPackService expressionPackService;
    private final FirmwareUpdateService firmwareUpdateService;

    public DeviceCommandAcknowledgementRouter(
            WakeWordModelJobService wakeWordModelJobService,
            ReminderDeliveryService reminderDeliveryService,
            ExpressionPackService expressionPackService,
            FirmwareUpdateService firmwareUpdateService
    ) {
        this.wakeWordModelJobService = wakeWordModelJobService;
        this.reminderDeliveryService = reminderDeliveryService;
        this.expressionPackService = expressionPackService;
        this.firmwareUpdateService = firmwareUpdateService;
    }

    @Override
    public void record(UUID deviceId, String commandId, boolean accepted) {
        record(deviceId, commandId, accepted, null);
    }

    @Override
    public void record(UUID deviceId, String commandId, boolean accepted, DeviceCommandResult result) {
        if (!firmwareUpdateService.recordCommandAcknowledgement(deviceId, commandId, accepted) &&
                !wakeWordModelJobService.recordCommandAcknowledgement(deviceId, commandId, accepted) &&
                !expressionPackService.recordCommandAcknowledgement(deviceId, commandId, accepted)) {
            reminderDeliveryService.record(deviceId, commandId, accepted, result);
        }
    }
}
