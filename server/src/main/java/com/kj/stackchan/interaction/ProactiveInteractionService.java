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
    private final ProactiveInterestBriefSource interestBriefSource;
    private ProactivePauseService pauseService;

    @Autowired
    public void setPauseService(ProactivePauseService pauseService) { this.pauseService = pauseService; }

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
            CompanionRoleService roleService,
            ProactiveInterestBriefSource interestBriefSource
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
        this.interestBriefSource = interestBriefSource;
    }

    public ProactiveInteractionService(
            InteractionSettingsService settingsService, ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository, DeviceCommandGateway commandGateway,
            LongTermMemoryService memoryService, ProactiveTopicCooldownService topicCooldownService,
            ProactiveMessageGenerator messageGenerator, Clock clock, CompanionRoleService roleService
    ) {
        this(settingsService, reminderRepository, voiceTurnRepository, commandGateway, memoryService,
                topicCooldownService, messageGenerator, clock, roleService, null);
    }

    public ProactiveInteractionService(
            InteractionSettingsService settingsService, ReminderRepository reminderRepository,
            VoiceTurnRepository voiceTurnRepository, DeviceCommandGateway commandGateway,
            LongTermMemoryService memoryService, ProactiveTopicCooldownService topicCooldownService,
            ProactiveMessageGenerator messageGenerator, Clock clock
    ) {
        this(settingsService, reminderRepository, voiceTurnRepository, commandGateway, memoryService,
                topicCooldownService, messageGenerator, clock, null, null);
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
            CompanionRoleService.RoleSnapshot role = activeRole(settings.deviceId());
            UUID roleId = role == null ? CompanionRoleEntity.DEFAULT_ROLE_ID : role.id();
            if (pauseService != null && pauseService.isPaused(settings.deviceId(), roleId, now)) continue;
            LongTermMemoryService.MemorySnapshot memory = selectMemory(settings, now, roleId);
            if (!settingsService.recordProactiveIfEligible(settings.deviceId(), now)) continue;
            List<InterestBrief> briefs = sourceCandidates(settings.deviceId(), memory);
            ProactiveMessageGenerator.GenerationResult wording;
            if (!briefs.isEmpty()) {
                wording = messageGenerator.generate(settings.proactiveContent(), memory, role, briefs);
            } else {
                wording = role == null
                        ? messageGenerator.generate(settings.proactiveContent(), memory)
                        : messageGenerator.generate(settings.proactiveContent(), memory, role);
            }
            String topicKey = memory == null ? null : memory.topicKey();
            ReminderEntity reminder = new ReminderEntity(
                    roleId, settings.deviceId(), wording.content(), now, settings.zoneId(),
                    ReminderRecurrence.NONE, 1, null, ReminderSource.PROACTIVE,
                    topicKey, wording.status(), now
            );
            if (wording.source() != null) {
                InterestBrief source = wording.source();
                reminder.assignProactiveSource(
                        source.sourceName(), source.title(), source.url(), source.publishedAt(), source.retrievedAt(), now
                );
            }
            reminderRepository.save(reminder);
            if (topicKey != null) {
                if (roleService == null) topicCooldownService.recordMention(settings.deviceId(), topicKey, now);
                else topicCooldownService.recordMention(settings.deviceId(), roleId, topicKey, now);
            }
            generated++;
        }
        return generated;
    }

    private List<InterestBrief> sourceCandidates(
            UUID deviceId,
            LongTermMemoryService.MemorySnapshot memory
    ) {
        if (memory == null || interestBriefSource == null) return List.of();
        return interestBriefSource.current().stream()
                .filter(source -> !reminderRepository.existsByDeviceIdAndSourceAndProactiveSourceUrl(
                        deviceId, ReminderSource.PROACTIVE, source.url()
                ))
                .limit(6)
                .toList();
    }

    private LongTermMemoryService.MemorySnapshot selectMemory(
            InteractionSettingsService.InteractionSettingsSnapshot settings,
            Instant now,
            UUID roleId
    ) {
        if (!settings.proactivePersonalizationEnabled()) return null;
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

    private CompanionRoleService.RoleSnapshot activeRole(UUID deviceId) {
        return roleService == null ? null : roleService.getActive(deviceId);
    }
}
