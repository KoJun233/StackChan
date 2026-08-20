package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceCommandResult;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.MissedReminderPolicy;
import com.kj.stackchan.notification.NotificationIntegrationRepository;
import com.kj.stackchan.role.CompanionRoleService;
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
    private final NotificationIntegrationRepository notificationIntegrationRepository;
    private CompanionRoleService roleService;

    @Autowired(required = false)
    public void setRoleService(CompanionRoleService roleService) {
        this.roleService = roleService;
    }

    @Autowired
    public ReminderDeliveryService(
            ReminderRepository reminderRepository,
            DeviceCommandGateway deviceCommandGateway,
            SpeechRuntimeClient speechRuntimeClient,
            Clock clock,
            InteractionSettingsService interactionSettingsService,
            VoiceTurnRepository voiceTurnRepository,
            ReminderScheduleCalculator scheduleCalculator,
            NotificationIntegrationRepository notificationIntegrationRepository
    ) {
        this.reminderRepository = reminderRepository;
        this.deviceCommandGateway = deviceCommandGateway;
        this.speechRuntimeClient = speechRuntimeClient;
        this.clock = clock;
        this.interactionSettingsService = interactionSettingsService;
        this.voiceTurnRepository = voiceTurnRepository;
        this.scheduleCalculator = scheduleCalculator;
        this.notificationIntegrationRepository = notificationIntegrationRepository;
    }

    ReminderDeliveryService(
            ReminderRepository reminderRepository,
            DeviceCommandGateway deviceCommandGateway,
            SpeechRuntimeClient speechRuntimeClient,
            Clock clock
    ) {
        this(reminderRepository, deviceCommandGateway, speechRuntimeClient, clock, null, null, null, null);
    }

    ReminderDeliveryService(
            ReminderRepository reminderRepository,
            DeviceCommandGateway deviceCommandGateway,
            SpeechRuntimeClient speechRuntimeClient,
            Clock clock,
            InteractionSettingsService interactionSettingsService,
            VoiceTurnRepository voiceTurnRepository,
            ReminderScheduleCalculator scheduleCalculator
    ) {
        this(reminderRepository, deviceCommandGateway, speechRuntimeClient, clock,
                interactionSettingsService, voiceTurnRepository, scheduleCalculator, null);
    }

    public void dispatchDueReminders() {
        Instant now = clock.instant();
        Set<UUID> handledNotificationIds = new HashSet<>();
        expireExternalNotifications(now);
        for (ReminderEntity reminder : reminderRepository
                .findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                        ReminderStatus.PENDING, now)) {
            if (handledNotificationIds.contains(reminder.getId())
                    || reminder.getStatus() != ReminderStatus.PENDING || reminder.getDeliveryGroupId() != null) continue;
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
            ReminderEntity deliveryLeader = reminder;
            try {
                List<ReminderEntity> deliveryItems = digestCandidates(reminder, now);
                if (deliveryItems.isEmpty()) continue;
                ReminderEntity selectedLeader = deliveryItems.getFirst();
                deliveryLeader = selectedLeader;
                String deliveryText = digestText(deliveryItems);
                byte[] audio = speechRuntimeClient.synthesize(deliveryText, selectedLeader.getRoleId());
                String commandId = UUID.randomUUID().toString();
                if (deliveryItems.size() == 1) {
                    selectedLeader.markDispatched(commandId, audio, now);
                    reminderRepository.saveAndFlush(selectedLeader);
                } else {
                    selectedLeader.markDigestLeader(commandId, audio, now);
                    deliveryItems.stream()
                            .filter(item -> !item.getId().equals(selectedLeader.getId()))
                            .forEach(item -> item.joinDeliveryGroup(selectedLeader.getId(), now));
                    reminderRepository.saveAllAndFlush(deliveryItems);
                }
                handledNotificationIds.addAll(deliveryItems.stream().map(ReminderEntity::getId).toList());
                if (roleService != null) {
                    CompanionRoleService.RoleSnapshot role = roleService.get(selectedLeader.getRoleId());
                    deviceCommandGateway.configureExpression(
                            selectedLeader.getDeviceId(), role.expressionThemeColor(),
                            "CONTENT", "WEAK", 5);
                }
                if (!deviceCommandGateway.speakReminder(
                        selectedLeader.getDeviceId(), selectedLeader.getId(), commandId)) {
                    deliveryItems.forEach(item -> item.returnToPending(clock.instant()));
                    reminderRepository.saveAll(deliveryItems);
                }
            } catch (SpeechProviderUnavailableException exception) {
                completeFailure(deliveryLeader, "speech_provider_unavailable", clock.instant());
            } catch (InvalidSpeechSettingsException exception) {
                completeFailure(deliveryLeader, "invalid_speech_settings", clock.instant());
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
                    List<ReminderEntity> deliveryItems = deliveryItems(reminder);
                    if (accepted) {
                        deliveryItems.forEach(item -> complete(item, ReminderStatus.DELIVERED, clock.instant()));
                    } else if (result == DeviceCommandResult.CANCELLED) {
                        deliveryItems.forEach(item -> complete(item, ReminderStatus.CANCELLED, clock.instant()));
                    } else {
                        Instant failedAt = clock.instant();
                        deliveryItems.forEach(item -> item.failGroupedOccurrence("device_playback_failed", failedAt));
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
            List<ReminderEntity> items = deliveryItems(reminder);
            if (isExpired(reminder, now)) {
                items.forEach(item -> {
                    if (isExpired(item, now)) item.markExpired(now);
                    else item.returnToPending(now);
                });
            } else {
                items.forEach(item -> item.returnToPending(now));
            }
            reminderRepository.saveAll(items);
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
            if (reminder.getDeliveryGroupId() != null) return;
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

    private List<ReminderEntity> digestCandidates(ReminderEntity reminder, Instant now) {
        if (notificationIntegrationRepository == null || reminder.getSource() != ReminderSource.EXTERNAL
                || !reminder.getResponseActions().isEmpty() || reminder.getNotificationIntegrationId() == null) {
            return List.of(reminder);
        }
        int window = notificationIntegrationRepository.findById(reminder.getNotificationIntegrationId())
                .map(integration -> integration.getDigestWindowSeconds()).orElse(0);
        if (window == 0) return List.of(reminder);
        List<ReminderEntity> candidates = reminderRepository
                .findTop10ByNotificationIntegrationIdAndDeviceIdAndRoleIdAndSourceAndStatusAndScheduledAtLessThanEqualAndDeliveryGroupIdIsNullOrderByCreatedAtAscIdAsc(
                        reminder.getNotificationIntegrationId(), reminder.getDeviceId(), reminder.getRoleId(),
                        ReminderSource.EXTERNAL, ReminderStatus.PENDING, now);
        List<ReminderEntity> safeCandidates = candidates.stream()
                .filter(item -> item.getResponseActions().isEmpty())
                .filter(item -> !item.getCreatedAt().isAfter(now))
                .filter(item -> !isExpired(item, now))
                .toList();
        if (safeCandidates.isEmpty()) return List.of(reminder);
        ReminderEntity oldest = safeCandidates.getFirst();
        if (oldest.getCreatedAt().plusSeconds(window).isAfter(now)) return List.of();
        java.util.ArrayList<ReminderEntity> selected = new java.util.ArrayList<>();
        selected.add(oldest);
        for (ReminderEntity item : safeCandidates) {
            if (item.getId().equals(oldest.getId()) || selected.size() == 10) continue;
            selected.add(item);
            if (formatDigest(selected).length() > 1000) {
                selected.removeLast();
                break;
            }
        }
        return selected.size() > 1 ? List.copyOf(selected) : List.of(reminder);
    }

    private String digestText(List<ReminderEntity> items) {
        if (items.size() == 1) return items.getFirst().getContent();
        return formatDigest(items);
    }

    private String formatDigest(List<ReminderEntity> items) {
        StringBuilder text = new StringBuilder("收到 " + items.size() + " 条通知：");
        for (int i = 0; i < items.size(); i++) {
            String next = (i + 1) + "，" + items.get(i).getContent() + (i + 1 == items.size() ? "。" : "；");
            text.append(next);
        }
        return text.toString();
    }

    private List<ReminderEntity> deliveryItems(ReminderEntity reminder) {
        if (reminder.getDeliveryGroupId() == null) return List.of(reminder);
        List<ReminderEntity> items = reminderRepository.findAllByDeliveryGroupId(reminder.getDeliveryGroupId());
        return items.isEmpty() ? List.of(reminder) : items;
    }
}
