package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceCommandResult;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.MissedReminderPolicy;
import com.kj.stackchan.speech.SpeechProviderUnavailableException;
import com.kj.stackchan.speech.InvalidSpeechSettingsException;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import com.kj.stackchan.speech.VoiceTurnRepository;
import com.kj.stackchan.speech.VoiceTurnStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderDeliveryService {

    private static final Duration STALE_DISPATCH_AGE = Duration.ofMinutes(5);
    private static final Duration ACTIVE_VOICE_MAX_AGE = Duration.ofMinutes(15);

    private final ReminderRepository reminderRepository;
    private final DeviceCommandGateway deviceCommandGateway;
    private final SpeechRuntimeClient speechRuntimeClient;
    private final Clock clock;
    private final InteractionSettingsService interactionSettingsService;
    private final VoiceTurnRepository voiceTurnRepository;
    private final ReminderScheduleCalculator scheduleCalculator;

    @Autowired
    public ReminderDeliveryService(
            ReminderRepository reminderRepository,
            DeviceCommandGateway deviceCommandGateway,
            SpeechRuntimeClient speechRuntimeClient,
            Clock clock,
            InteractionSettingsService interactionSettingsService,
            VoiceTurnRepository voiceTurnRepository,
            ReminderScheduleCalculator scheduleCalculator
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceCommandGateway = deviceCommandGateway;
        this.speechRuntimeClient = speechRuntimeClient;
        this.clock = clock;
        this.interactionSettingsService = interactionSettingsService;
        this.voiceTurnRepository = voiceTurnRepository;
        this.scheduleCalculator = scheduleCalculator;
    }

    ReminderDeliveryService(
            ReminderRepository reminderRepository,
            DeviceCommandGateway deviceCommandGateway,
            SpeechRuntimeClient speechRuntimeClient,
            Clock clock
    ) {
        this(reminderRepository, deviceCommandGateway, speechRuntimeClient, clock, null, null, null);
    }

    public void dispatchDueReminders() {
        Instant now = clock.instant();
        expireExternalNotifications(now);
        for (ReminderEntity reminder : reminderRepository
                .findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(ReminderStatus.PENDING, now)) {
            if (isExpired(reminder, now)) {
                reminder.markExpired(now);
                reminderRepository.save(reminder);
                continue;
            }
            var settings = interactionSettingsService == null
                    ? null : interactionSettingsService.resolve(reminder.getDeviceId());
            if (settings != null && interactionSettingsService.isDnd(settings, now)) {
                reminder.deferUntil(interactionSettingsService.nextDndEnd(settings, now), now);
                reminderRepository.save(reminder);
                continue;
            }
            if (!deviceCommandGateway.isConnected(reminder.getDeviceId())) {
                handleOffline(reminder, settings, now);
                continue;
            }
            if (isBusy(reminder.getDeviceId())) {
                reminder.deferUntil(now.plus(Duration.ofMinutes(1)), now);
                reminderRepository.save(reminder);
                continue;
            }
            try {
                byte[] audio = speechRuntimeClient.synthesize(reminder.getContent(), reminder.getRoleId());
                String commandId = UUID.randomUUID().toString();
                reminder.markDispatched(commandId, audio, now);
                reminderRepository.saveAndFlush(reminder);
                if (!deviceCommandGateway.speakReminder(reminder.getDeviceId(), reminder.getId(), commandId)) {
                    reminder.returnToPending(clock.instant());
                    reminderRepository.save(reminder);
                }
            } catch (SpeechProviderUnavailableException exception) {
                completeFailure(reminder, "speech_provider_unavailable", clock.instant());
            } catch (InvalidSpeechSettingsException exception) {
                completeFailure(reminder, "invalid_speech_settings", clock.instant());
            }
        }
    }

    @Transactional
    public void record(UUID deviceId, String commandId, boolean accepted) {
        record(deviceId, commandId, accepted, null);
    }

    @Transactional
    public void record(UUID deviceId, String commandId, boolean accepted, DeviceCommandResult result) {
        reminderRepository.findByCommandId(commandId)
                .filter(reminder -> reminder.getDeviceId().equals(deviceId))
                .filter(reminder -> reminder.getStatus() == ReminderStatus.DISPATCHED)
                .ifPresent(reminder -> {
                    if (accepted) {
                        complete(reminder, ReminderStatus.DELIVERED, clock.instant());
                    } else if (result == DeviceCommandResult.CANCELLED) {
                        complete(reminder, ReminderStatus.CANCELLED, clock.instant());
                    } else {
                        completeFailure(reminder, "device_playback_failed", clock.instant());
                    }
                });
    }

    @Transactional
    public int recoverStaleDispatches() {
        Instant now = clock.instant();
        var reminders = reminderRepository.findAllByStatusAndLastAttemptAtBefore(
                ReminderStatus.DISPATCHED,
                now.minus(STALE_DISPATCH_AGE)
        );
        reminders.forEach(reminder -> {
            if (isExpired(reminder, now)) {
                reminder.markExpired(now);
            } else {
                reminder.returnToPending(now);
            }
        });
        return reminders.size();
    }

    @Transactional(readOnly = true)
    public byte[] getAudio(UUID reminderId, UUID deviceId) {
        ReminderEntity reminder = reminderRepository.findByIdAndDeviceId(reminderId, deviceId)
                .orElseThrow(ReminderNotFoundException::new);
        byte[] audio = reminder.getAudioPayload();
        if (reminder.getStatus() != ReminderStatus.DISPATCHED || audio == null || audio.length < 44) {
            throw new ReminderNotFoundException();
        }
        return audio;
    }

    private void handleOffline(
            ReminderEntity reminder,
            InteractionSettingsService.InteractionSettingsSnapshot settings,
            Instant now
    ) {
        if (reminder.getSource() == ReminderSource.EXTERNAL) {
            return;
        }
        if (settings == null || settings.missedReminderPolicy() == MissedReminderPolicy.PLAY_NOW) {
            return;
        }
        if (settings.missedReminderPolicy() == MissedReminderPolicy.SNOOZE) {
            reminder.deferUntil(now.plus(Duration.ofMinutes(settings.missedSnoozeMinutes())), now);
            reminderRepository.save(reminder);
            return;
        }
        complete(reminder, ReminderStatus.SKIPPED, now);
        reminderRepository.save(reminder);
    }

    private boolean isBusy(UUID deviceId) {
        if (reminderRepository.existsByDeviceIdAndStatus(deviceId, ReminderStatus.DISPATCHED)) {
            return true;
        }
        return voiceTurnRepository != null && voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                    deviceId,
                    java.util.List.of(VoiceTurnStatus.IN_PROGRESS, VoiceTurnStatus.RESPONSE_READY),
                    clock.instant().minus(ACTIVE_VOICE_MAX_AGE)
            );
    }

    private void expireExternalNotifications(Instant now) {
        reminderRepository.findTop100BySourceAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                ReminderSource.EXTERNAL, ReminderStatus.PENDING, now
        ).forEach(reminder -> {
            reminder.markExpired(now);
            reminderRepository.save(reminder);
        });
    }

    private boolean isExpired(ReminderEntity reminder, Instant now) {
        return reminder.getSource() == ReminderSource.EXTERNAL
                && reminder.getExpiresAt() != null
                && !reminder.getExpiresAt().isAfter(now);
    }

    private void completeFailure(ReminderEntity reminder, String failureCode, Instant now) {
        reminder.markFailed(failureCode, now);
        complete(reminder, ReminderStatus.FAILED, now);
        reminderRepository.save(reminder);
    }

    private void complete(ReminderEntity reminder, ReminderStatus outcome, Instant now) {
        Instant next = scheduleCalculator == null ? null : scheduleCalculator.nextAfter(reminder, now);
        reminder.completeOccurrence(outcome, next, now);
    }
}
