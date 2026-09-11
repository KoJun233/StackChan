package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.expression.CompanionEmotion;
import com.kj.stackchan.expression.DeviceExpressionService;
import com.kj.stackchan.expression.EmotionIntensity;
import com.kj.stackchan.expression.ExpressionSuggestionParser;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderStatus;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SilentPresenceService {

    private static final Duration ACTIVE_VOICE_MAX_AGE = Duration.ofMinutes(15);
    private static final List<VoiceTurnStatus> ACTIVE_VOICE_STATUSES = List.of(
            VoiceTurnStatus.IN_PROGRESS, VoiceTurnStatus.RESPONSE_READY
    );

    private final InteractionSettingsService settingsService;
    private final DeviceCommandGateway commandGateway;
    private final VoiceTurnRepository voiceTurnRepository;
    private final ReminderRepository reminderRepository;
    private final DeviceExpressionService expressionService;
    private final CompanionRoleService roleService;
    private final Clock clock;

    public SilentPresenceService(
            InteractionSettingsService settingsService,
            DeviceCommandGateway commandGateway,
            VoiceTurnRepository voiceTurnRepository,
            ReminderRepository reminderRepository,
            DeviceExpressionService expressionService,
            CompanionRoleService roleService,
            Clock clock
    ) {
        this.settingsService = settingsService;
        this.commandGateway = commandGateway;
        this.voiceTurnRepository = voiceTurnRepository;
        this.reminderRepository = reminderRepository;
        this.expressionService = expressionService;
        this.roleService = roleService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    public synchronized int showDueExpressions() {
        Instant now = clock.instant();
        int shown = 0;
        for (var settings : settingsService.silentPresenceCandidates()) {
            if (!settingsService.isSilentPresenceEligible(settings, now)
                    || !commandGateway.isConnected(settings.deviceId())
                    || voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                            settings.deviceId(), ACTIVE_VOICE_STATUSES, now.minus(ACTIVE_VOICE_MAX_AGE)
                    )
                    || reminderRepository.existsByDeviceIdAndStatus(settings.deviceId(), ReminderStatus.DISPATCHED)) {
                continue;
            }
            var role = roleService.getActive(settings.deviceId());
            var suggestion = suggestion(settings, now);
            if (expressionService.apply(settings.deviceId(), role.id(), suggestion)
                    && settingsService.recordSilentPresenceIfEligible(settings.deviceId(), now)) {
                shown++;
            }
        }
        return shown;
    }

    private ExpressionSuggestionParser.Suggestion suggestion(
            InteractionSettingsService.InteractionSettingsSnapshot settings,
            Instant now
    ) {
        int hour = now.atZone(ZoneId.of(settings.zoneId())).getHour();
        if (hour >= 20 || hour < 7) {
            return new ExpressionSuggestionParser.Suggestion("", CompanionEmotion.TIRED, EmotionIntensity.WEAK, 8);
        }
        return switch (settings.silentPresenceCounter() % 3) {
            case 0 -> new ExpressionSuggestionParser.Suggestion(
                    "", CompanionEmotion.CONTENT, EmotionIntensity.MEDIUM, 8);
            case 1 -> new ExpressionSuggestionParser.Suggestion(
                    "", CompanionEmotion.HAPPY, EmotionIntensity.WEAK, 6);
            default -> new ExpressionSuggestionParser.Suggestion(
                    "", CompanionEmotion.FOCUSED, EmotionIntensity.WEAK, 8);
        };
    }
}
