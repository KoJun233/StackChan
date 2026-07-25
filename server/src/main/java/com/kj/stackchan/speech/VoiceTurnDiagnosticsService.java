package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceTurnDiagnosticsService {

    static final Duration RETENTION = Duration.ofDays(7);
    private static final int MAX_ELAPSED_MS = 300_000;

    private final VoiceTurnRepository turnRepository;
    private final VoiceTurnEventRepository eventRepository;
    private final Clock clock;

    public VoiceTurnDiagnosticsService(
            VoiceTurnRepository turnRepository,
            VoiceTurnEventRepository eventRepository,
            Clock clock
    ) {
        this.turnRepository = turnRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordDeviceStage(
            UUID deviceId,
            UUID turnId,
            VoiceTurnStage stage,
            int elapsedMs,
            VoiceTurnFailureCode failureCode
    ) {
        if (!stage.isDeviceStage() || elapsedMs < 0 || elapsedMs > MAX_ELAPSED_MS) {
            throw new IllegalArgumentException("Invalid device voice turn stage");
        }
        record(deviceId, turnId, stage, VoiceTurnStageSource.DEVICE, elapsedMs, failureCode);
    }

    @Transactional
    public void recordServerStage(
            UUID deviceId,
            UUID turnId,
            VoiceTurnStage stage,
            VoiceTurnFailureCode failureCode
    ) {
        if (stage.isDeviceStage() && stage != VoiceTurnStage.FAILED) {
            throw new IllegalArgumentException("Invalid server voice turn stage");
        }
        record(deviceId, turnId, stage, VoiceTurnStageSource.SERVER, null, failureCode);
    }

    private void record(
            UUID deviceId,
            UUID turnId,
            VoiceTurnStage stage,
            VoiceTurnStageSource source,
            Integer elapsedMs,
            VoiceTurnFailureCode failureCode
    ) {
        if ((stage == VoiceTurnStage.FAILED) != (failureCode != null)) {
            throw new IllegalArgumentException("Voice turn failure code does not match stage");
        }
        VoiceTurnEntity turn = turnRepository.findById(turnId).orElse(null);
        Instant now = clock.instant();
        if (turn == null) {
            turn = new VoiceTurnEntity(turnId, deviceId, now);
        } else if (!turn.getDeviceId().equals(deviceId)) {
            throw new IllegalArgumentException("Voice turn belongs to another device");
        }
        if (eventRepository.existsByTurnIdAndSourceAndStage(turnId, source, stage)) {
            return;
        }
        turn.apply(stage, failureCode, now);
        turnRepository.save(turn);
        eventRepository.save(new VoiceTurnEventEntity(
                turnId,
                stage,
                source,
                now,
                elapsedMs,
                failureCode
        ));
    }

    @Transactional(readOnly = true)
    public List<VoiceTurnSnapshot> recent(UUID deviceId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<VoiceTurnEntity> turns = turnRepository.findByDeviceIdOrderByStartedAtDesc(
                deviceId,
                PageRequest.of(0, safeLimit)
        );
        if (turns.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<VoiceTurnEventSnapshot>> events = eventRepository
                .findByTurnIdInOrderByOccurredAtAscIdAsc(turns.stream().map(VoiceTurnEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        VoiceTurnEventEntity::getTurnId,
                        Collectors.mapping(this::snapshot, Collectors.toList())
                ));
        return turns.stream().map(turn -> new VoiceTurnSnapshot(
                turn.getId(),
                turn.getStatus(),
                turn.getFailureCode(),
                turn.getStartedAt(),
                turn.getUpdatedAt(),
                events.getOrDefault(turn.getId(), List.of())
        )).toList();
    }

    @Scheduled(cron = "0 23 3 * * *")
    @Transactional
    public void deleteExpired() {
        turnRepository.deleteByStartedAtBefore(clock.instant().minus(RETENTION));
    }

    private VoiceTurnEventSnapshot snapshot(VoiceTurnEventEntity event) {
        return new VoiceTurnEventSnapshot(
                event.getStage(),
                event.getSource(),
                event.getOccurredAt(),
                event.getElapsedMs(),
                event.getFailureCode()
        );
    }

    public record VoiceTurnSnapshot(
            UUID turnId,
            VoiceTurnStatus status,
            VoiceTurnFailureCode failureCode,
            Instant startedAt,
            Instant updatedAt,
            List<VoiceTurnEventSnapshot> events
    ) {
    }

    public record VoiceTurnEventSnapshot(
            VoiceTurnStage stage,
            VoiceTurnStageSource source,
            Instant occurredAt,
            Integer elapsedMs,
            VoiceTurnFailureCode failureCode
    ) {
    }
}
