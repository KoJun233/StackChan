package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandAcknowledgementService;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.speech.SpeechProviderUnavailableException;
import com.kj.stackchan.speech.InvalidSpeechSettingsException;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class ReminderDeliveryService implements DeviceCommandAcknowledgementService {

    private static final Duration STALE_DISPATCH_AGE = Duration.ofMinutes(5);

    private final ReminderRepository reminderRepository;
    private final DeviceCommandGateway deviceCommandGateway;
    private final SpeechRuntimeClient speechRuntimeClient;
    private final Clock clock;

    public ReminderDeliveryService(
            ReminderRepository reminderRepository,
            DeviceCommandGateway deviceCommandGateway,
            SpeechRuntimeClient speechRuntimeClient,
            Clock clock
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceCommandGateway = deviceCommandGateway;
        this.speechRuntimeClient = speechRuntimeClient;
        this.clock = clock;
    }

    public void dispatchDueReminders() {
        Instant now = clock.instant();
        for (ReminderEntity reminder : reminderRepository
                .findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(ReminderStatus.PENDING, now)) {
            if (!deviceCommandGateway.isConnected(reminder.getDeviceId())) {
                continue;
            }
            try {
                byte[] audio = speechRuntimeClient.synthesize(reminder.getContent());
                String commandId = UUID.randomUUID().toString();
                reminder.markDispatched(commandId, audio, now);
                reminderRepository.saveAndFlush(reminder);
                if (!deviceCommandGateway.speakReminder(reminder.getDeviceId(), reminder.getId(), commandId)) {
                    reminder.returnToPending(clock.instant());
                    reminderRepository.save(reminder);
                }
            } catch (SpeechProviderUnavailableException exception) {
                reminder.markFailed("speech_provider_unavailable", clock.instant());
                reminderRepository.save(reminder);
            } catch (InvalidSpeechSettingsException exception) {
                reminder.markFailed("invalid_speech_settings", clock.instant());
                reminderRepository.save(reminder);
            }
        }
    }

    @Override
    @Transactional
    public void record(UUID deviceId, String commandId, boolean accepted) {
        reminderRepository.findByCommandId(commandId)
                .filter(reminder -> reminder.getDeviceId().equals(deviceId))
                .filter(reminder -> reminder.getStatus() == ReminderStatus.DISPATCHED)
                .ifPresent(reminder -> {
                    if (accepted) {
                        reminder.markDelivered(clock.instant());
                    } else {
                        reminder.markFailed("device_playback_failed", clock.instant());
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
        reminders.forEach(reminder -> reminder.returnToPending(now));
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
}
