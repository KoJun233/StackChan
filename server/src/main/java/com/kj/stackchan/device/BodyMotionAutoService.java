package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.interaction.DeviceQuietTodayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class BodyMotionAutoService {
    private static final Duration MIN_INTERVAL = Duration.ofMinutes(10);
    private static final int DAILY_LIMIT = 8;
    private static final Duration RESULT_DEADLINE = Duration.ofSeconds(15);
    private final BodyMotionAutoSettingsRepository settingsRepository;
    private final BodyMotionCommandRepository commandRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceCommandGateway gateway;
    private final InteractionSettingsService interactionSettings;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private DeviceQuietTodayService quietService;

    @Autowired(required = false)
    public void setQuietService(DeviceQuietTodayService quietService) { this.quietService = quietService; }

    public BodyMotionAutoService(BodyMotionAutoSettingsRepository settingsRepository,
                                 BodyMotionCommandRepository commandRepository,
                                 DeviceRepository deviceRepository,
                                 DeviceCommandGateway gateway,
                                 InteractionSettingsService interactionSettings,
                                 Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.commandRepository = commandRepository;
        this.deviceRepository = deviceRepository;
        this.gateway = gateway;
        this.interactionSettings = interactionSettings;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public boolean isEnabled(UUID deviceId) {
        return settingsRepository.findById(deviceId)
                .map(BodyMotionAutoSettingsEntity::isEnabled).orElse(false);
    }

    public void setEnabled(UUID deviceId, boolean enabled) {
        transactions.executeWithoutResult(status -> {
            if (!deviceRepository.existsById(deviceId)) throw new IllegalArgumentException("Unknown device");
            BodyMotionAutoSettingsEntity settings = settingsRepository.lockByDeviceId(deviceId)
                    .orElseGet(() -> new BodyMotionAutoSettingsEntity(deviceId, false));
            settings.setEnabled(enabled);
            settingsRepository.save(settings);
        });
    }

    /** Only immediate, named business events may request motion; there is no offline queue. */
    public boolean request(UUID deviceId, String motion, String eventKey) {
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 120 ||
                !List.of("WAKE", "LOOK_USER", "NOD_SMALL", "THINK", "DROWSY").contains(motion)) return false;
        BodyMotionCommandEntity command = transactions.execute(status -> reserve(deviceId, motion, eventKey));
        if (command == null) return false;
        if (!gateway.playBodyMotion(deviceId, motion, command.getId().toString())) {
            commandRepository.markDeliveryFailed(deviceId, command.getId(), clock.instant());
            return false;
        }
        return true;
    }

    /** Close missing results without waiting for another business event. Never retry the motion. */
    @Scheduled(fixedDelay = 5000)
    public void expireMissingResults() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(RESULT_DEADLINE);
        for (BodyMotionCommandEntity candidate : commandRepository
                .findTop100ByAutomaticTrueAndStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                        List.of("SENT", "ACCEPTED"), cutoff)) {
            transactions.executeWithoutResult(status -> {
                BodyMotionAutoSettingsEntity setting = settingsRepository
                        .lockByDeviceId(candidate.getDeviceId()).orElse(null);
                if (commandRepository.markUnconfirmed(candidate.getDeviceId(), candidate.getId(), cutoff, now) > 0
                        && setting != null && setting.isEnabled()) {
                    setting.setEnabled(false);
                    settingsRepository.save(setting);
                }
            });
        }
    }

    private BodyMotionCommandEntity reserve(UUID deviceId, String motion, String eventKey) {
        BodyMotionAutoSettingsEntity setting = settingsRepository.lockByDeviceId(deviceId).orElse(null);
        if (setting == null || !setting.isEnabled() || !gateway.isConnected(deviceId)) return null;
        DeviceEntity device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null || !DeviceEventService.MOTION_ARMED.equals(device.getSafetyState()) ||
                !device.getBodyDiagnostics().bodyMotionSupported() ||
                !device.getBodyDiagnostics().servoFeedbackSupported() ||
                !device.getBodyDiagnostics().calibrated() ||
                commandRepository.existsByDeviceIdAndEventKey(deviceId, eventKey)) return null;
        Instant now = clock.instant();
        var pending = commandRepository.findAllByDeviceIdAndAutomaticTrueAndStatusIn(
                deviceId, List.of("SENT", "ACCEPTED"));
        if (!pending.isEmpty()) {
            if (pending.stream().anyMatch(command ->
                    command.getCreatedAt().plus(RESULT_DEADLINE).isAfter(now))) return null;
            for (BodyMotionCommandEntity command : pending) {
                commandRepository.markUnconfirmed(deviceId, command.getId(),
                        now.minus(RESULT_DEADLINE), now);
            }
            setting.setEnabled(false);
            settingsRepository.save(setting);
            return null;
        }
        if (quietService != null && quietService.isQuiet(deviceId, now)) return null;
        var interaction = interactionSettings.resolve(deviceId);
        if (interactionSettings.isDnd(interaction, now)) return null;
        ZoneId zone = ZoneId.of(interaction.zoneId());
        LocalDate day = now.atZone(zone).toLocalDate();
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        if (commandRepository.countByDeviceIdAndAutomaticTrueAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
                deviceId, "COMPLETED", dayStart, dayEnd) >= DAILY_LIMIT) return null;
        if (commandRepository.findFirstByDeviceIdAndAutomaticTrueAndStatusOrderByUpdatedAtDesc(
                deviceId, "COMPLETED").map(last -> last.getUpdatedAt().plus(MIN_INTERVAL).isAfter(now))
                .orElse(false)) return null;
        return commandRepository.save(new BodyMotionCommandEntity(
                UUID.randomUUID(), deviceId, motion, true, eventKey, now));
    }
}
