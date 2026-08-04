package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import org.springframework.stereotype.Service;

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
    private final LongTermMemoryService memoryService;
    private final ProactiveTopicCooldownService topicCooldownService;
    private final ProactiveMessageGenerator messageGenerator;
    private final Clock clock;

    public ProactiveInteractionService(
            InteractionSettingsService settingsService,
            ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository,
            DeviceCommandGateway commandGateway,
            LongTermMemoryService memoryService,
            ProactiveTopicCooldownService topicCooldownService,
            ProactiveMessageGenerator messageGenerator,
            Clock clock
    ) {
        this.settingsService = settingsService;
        this.reminderRepository = reminderRepository;
        this.voiceTurnRepository = voiceTurnRepository;
        this.commandGateway = commandGateway;
        this.memoryService = memoryService;
        this.topicCooldownService = topicCooldownService;
        this.messageGenerator = messageGenerator;
        this.clock = clock;
    }

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
                    )) {
                continue;
            }
            LongTermMemoryService.MemorySnapshot memory = selectMemory(settings, now);
            if (!settingsService.recordProactiveIfEligible(settings.deviceId(), now)) continue;
            ProactiveMessageGenerator.GenerationResult wording = messageGenerator.generate(
                    settings.proactiveContent(), memory
            );
            String topicKey = memory == null ? null : memory.topicKey();
            reminderRepository.save(new ReminderEntity(
                    settings.deviceId(), wording.content(), now, settings.zoneId(),
                    ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                    topicKey, wording.status(), now
            ));
            if (topicKey != null) topicCooldownService.recordMention(settings.deviceId(), topicKey, now);
            generated++;
        }
        return generated;
    }

    private LongTermMemoryService.MemorySnapshot selectMemory(
            InteractionSettingsService.InteractionSettingsSnapshot settings,
            Instant now
    ) {
        if (!settings.proactivePersonalizationEnabled()) return null;
        return memoryService.loadProactiveCandidates(settings.deviceId(), 8).stream()
                .filter(memory -> topicCooldownService.isEligible(settings.deviceId(), memory.topicKey(), now))
                .findFirst()
                .orElse(null);
    }
}
