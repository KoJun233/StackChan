package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CompanionRoleService roleService;

    @Autowired
    public ProactiveInteractionService(
            InteractionSettingsService settingsService,
            ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository,
            DeviceCommandGateway commandGateway,
            LongTermMemoryService memoryService,
            ProactiveTopicCooldownService topicCooldownService,
            ProactiveMessageGenerator messageGenerator,
            Clock clock,
            CompanionRoleService roleService
    ) {
        this.settingsService = settingsService;
        this.reminderRepository = reminderRepository;
        this.voiceTurnRepository = voiceTurnRepository;
        this.commandGateway = commandGateway;
        this.memoryService = memoryService;
        this.topicCooldownService = topicCooldownService;
        this.messageGenerator = messageGenerator;
        this.clock = clock;
        this.roleService = roleService;
    }

    public ProactiveInteractionService(
            InteractionSettingsService settingsService, ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository, DeviceCommandGateway commandGateway,
            LongTermMemoryService memoryService, ProactiveTopicCooldownService topicCooldownService,
            ProactiveMessageGenerator messageGenerator, Clock clock
    ) {
        this(settingsService, reminderRepository, voiceTurnRepository, commandGateway, memoryService,
                topicCooldownService, messageGenerator, clock, null);
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
            UUID roleId = activeRoleId(settings.deviceId());
            if (!settingsService.recordProactiveIfEligible(settings.deviceId(), now)) continue;
            ProactiveMessageGenerator.GenerationResult wording = messageGenerator.generate(
                    settings.proactiveContent(), memory
            );
            String topicKey = memory == null ? null : memory.topicKey();
            reminderRepository.save(new ReminderEntity(
                    roleId, settings.deviceId(), wording.content(), now, settings.zoneId(),
                    ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                    topicKey, wording.status(), now
            ));
            if (topicKey != null) {
                if (roleService == null) topicCooldownService.recordMention(settings.deviceId(), topicKey, now);
                else topicCooldownService.recordMention(settings.deviceId(), roleId, topicKey, now);
            }
            generated++;
        }
        return generated;
    }

    private LongTermMemoryService.MemorySnapshot selectMemory(
            InteractionSettingsService.InteractionSettingsSnapshot settings,
            Instant now
    ) {
        if (!settings.proactivePersonalizationEnabled()) return null;
        UUID roleId = activeRoleId(settings.deviceId());
        var memories = roleService == null
                ? memoryService.loadProactiveCandidates(settings.deviceId(), 8)
                : memoryService.loadProactiveCandidates(roleId, settings.deviceId(), 8);
        return memories.stream()
                .filter(memory -> roleService == null
                        ? topicCooldownService.isEligible(settings.deviceId(), memory.topicKey(), now)
                        : topicCooldownService.isEligible(settings.deviceId(), roleId, memory.topicKey(), now))
                .findFirst()
                .orElse(null);
    }

    private UUID activeRoleId(UUID deviceId) {
        return roleService == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : roleService.getActive(deviceId).id();
    }
}
