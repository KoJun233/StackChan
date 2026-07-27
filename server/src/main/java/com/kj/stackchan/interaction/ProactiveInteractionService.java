package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProactiveInteractionService {

    private static final Duration ACTIVE_VOICE_MAX_AGE = Duration.ofMinutes(15);
    private static final List<VoiceTurnStatus> ACTIVE_VOICE_STATUSES = List.of(
            VoiceTurnStatus.IN_PROGRESS, VoiceTurnStatus.RESPONSE_READY
    );

    private final InteractionSettingsService settingsService;
    private final ReminderRepository reminderRepository;
    private final VoiceTurnRepository voiceTurnRepository;
    private final DeviceCommandGateway commandGateway;
    private final Clock clock;

    public ProactiveInteractionService(
            InteractionSettingsService settingsService,
            ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository,
            DeviceCommandGateway commandGateway,
            Clock clock
    ) {
        this.settingsService = settingsService;
        this.reminderRepository = reminderRepository;
        this.voiceTurnRepository = voiceTurnRepository;
        this.commandGateway = commandGateway;
        this.clock = clock;
    }

    @Transactional
    public int generateDueGreetings() {
        Instant now = clock.instant();
        int generated = 0;
        for (var settings : settingsService.proactiveCandidates()) {
            if (!settingsService.isProactiveEligible(settings, now)
                    || !commandGateway.isConnected(settings.deviceId())
                    || voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                            settings.deviceId(), ACTIVE_VOICE_STATUSES, now.minus(ACTIVE_VOICE_MAX_AGE)
                    )
                    || reminderRepository.existsByDeviceIdAndStatus(settings.deviceId(), ReminderStatus.DISPATCHED)
                    || reminderRepository.existsByDeviceIdAndSourceAndStatus(
                            settings.deviceId(), ReminderSource.PROACTIVE, ReminderStatus.PENDING
                    )
                    || !settingsService.recordProactiveIfEligible(settings.deviceId(), now)) {
                continue;
            }
            reminderRepository.save(new ReminderEntity(
                    settings.deviceId(), settings.proactiveContent(), now, settings.zoneId(),
                    ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE, now
            ));
            generated++;
        }
        return generated;
    }
}
