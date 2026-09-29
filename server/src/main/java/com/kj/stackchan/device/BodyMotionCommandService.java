package com.kj.stackchan.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "companion.device-transport-enabled", havingValue = "true", matchIfMissing = true)
public class BodyMotionCommandService {
    private static final Duration RESULT_DEADLINE = Duration.ofSeconds(15);
    private final BodyMotionCommandRepository repository;
    private final DeviceCommandGateway gateway;
    private final Clock clock;

    public BodyMotionCommandService(BodyMotionCommandRepository repository,
                                    DeviceCommandGateway gateway, Clock clock) {
        this.repository = repository;
        this.gateway = gateway;
        this.clock = clock;
    }

    public BodyMotionCommandEntity issue(UUID deviceId, String motion) {
        UUID commandId = UUID.randomUUID();
        BodyMotionCommandEntity command = repository.save(
                new BodyMotionCommandEntity(commandId, deviceId, motion, clock.instant()));
        if (!gateway.playBodyMotion(deviceId, motion, commandId.toString())) {
            repository.markDeliveryFailed(deviceId, commandId, clock.instant());
            return repository.findByIdAndDeviceId(commandId, deviceId).orElseThrow();
        }
        return command;
    }

    @Transactional
    public BodyMotionCommandEntity get(UUID deviceId, UUID commandId) {
        Instant now = clock.instant();
        repository.markUnconfirmed(deviceId, commandId, now.minus(RESULT_DEADLINE), now);
        return repository.findByIdAndDeviceId(commandId, deviceId).orElse(null);
    }

    @Transactional
    public boolean recordAcknowledgement(UUID deviceId, String commandId, boolean accepted) {
        UUID id = parse(commandId);
        if (id == null || !repository.existsByIdAndDeviceId(id, deviceId)) return false;
        repository.markAcknowledged(deviceId, id, accepted ? "ACCEPTED" : "REJECTED", clock.instant());
        return true;
    }

    @Transactional
    public void recordResult(UUID deviceId, String commandId, String motion,
                             String status, String failureCode) {
        UUID id = parse(commandId);
        if (id != null) repository.markFinal(deviceId, id, motion, status, failureCode, clock.instant());
    }

    private UUID parse(String commandId) {
        try {
            return UUID.fromString(commandId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
