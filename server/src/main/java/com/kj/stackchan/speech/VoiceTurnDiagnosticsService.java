package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.kj.stackchan.conversation.DeviceVoiceConversationRepository;
import com.kj.stackchan.conversation.ConversationRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceTurnDiagnosticsService {

    static final Duration RETENTION = Duration.ofDays(7);
    private static final List<VoiceTurnStatus> ACTIVE_STATUSES = List.of(
            VoiceTurnStatus.IN_PROGRESS, VoiceTurnStatus.RESPONSE_READY
    );
    private static final int MAX_ELAPSED_MS = 300_000;

    private final VoiceTurnRepository turnRepository;
    private final VoiceTurnEventRepository eventRepository;
    private final Clock clock;
    private final DeviceVoiceConversationRepository conversationBindings;
    private final ConversationRepository conversations;

    public VoiceTurnDiagnosticsService(
            VoiceTurnRepository turnRepository,
            VoiceTurnEventRepository eventRepository,
            Clock clock,
            DeviceVoiceConversationRepository conversationBindings,
            ConversationRepository conversations
    ) {
        this.turnRepository = turnRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
        this.conversationBindings = conversationBindings;
        this.conversations = conversations;
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
    public void recordDeviceStage(UUID deviceId, UUID turnId, VoiceTurnStage stage, int elapsedMs,
                                  VoiceTurnFailureCode failureCode, String measurementSource,
                                  Integer endpointToLocalFeedbackMs, Boolean captureLimitReached) {
        if (!stage.isDeviceStage() || elapsedMs < 0 || elapsedMs > MAX_ELAPSED_MS
                || !"DEVICE_MONOTONIC".equals(measurementSource)
                || endpointToLocalFeedbackMs != null && (stage != VoiceTurnStage.SPEECH_CAPTURED
                    || endpointToLocalFeedbackMs < 0 || endpointToLocalFeedbackMs > MAX_ELAPSED_MS)
                || captureLimitReached != null && stage != VoiceTurnStage.SPEECH_CAPTURED) {
            throw new IllegalArgumentException("Invalid device measurement metadata");
        }
        String code = stage == VoiceTurnStage.PLAYBACK_STARTED ? "playback_codec_write" : "device_monotonic";
        if (stage == VoiceTurnStage.SPEECH_CAPTURED && captureLimitReached != null) {
            code = captureLimitReached ? "capture_limit" : "capture_endpoint";
        }
        record(deviceId, turnId, stage, VoiceTurnStageSource.DEVICE, elapsedMs, failureCode,
                endpointToLocalFeedbackMs, code);
    }

    @Transactional(readOnly = true)
    public Boolean captureLimitReached(UUID deviceId, UUID turnId) {
        VoiceTurnEntity turn = turnRepository.findById(turnId).orElse(null);
        if (turn == null || !turn.getDeviceId().equals(deviceId)) return null;
        return eventRepository.findByTurnIdAndSourceAndStage(turnId, VoiceTurnStageSource.DEVICE,
                VoiceTurnStage.SPEECH_CAPTURED).map(event -> captureLimit(event.getDiagnosticCode())).orElse(null);
    }

    private static Boolean captureLimit(String code) {
        if ("capture_limit".equals(code)) return true;
        if ("capture_endpoint".equals(code)) return false;
        return null;
    }

    private static String measurementSource(VoiceTurnStageSource source, Integer durationMs, String code) {
        if (source == VoiceTurnStageSource.SERVER) return durationMs == null ? null : "SERVER_DURATION";
        return Set.of("capture_limit", "capture_endpoint", "playback_codec_write", "device_monotonic")
                .contains(code == null ? "" : code) ? "DEVICE_MONOTONIC" : null;
    }

    @Transactional
    public void bindConversation(UUID deviceId, UUID turnId, UUID conversationId) {
        conversationBindings.findByConversationId(conversationId).filter(binding -> binding.getDeviceId().equals(deviceId))
                .orElseThrow(() -> new IllegalArgumentException("Conversation is not bound to this device"));
        // Lock order matches privacy deletion: conversation first, then anonymous turn ID.
        conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation was removed"));
        turnRepository.lockDiagnosticTurn(turnId);
        if (turnRepository.isPrivacyErased(turnId, clock.instant())) {
            throw new IllegalArgumentException("Voice turn has been erased");
        }
        VoiceTurnEntity turn = turnRepository.findById(turnId)
                .orElseGet(() -> new VoiceTurnEntity(turnId, deviceId, clock.instant()));
        if (!turn.getDeviceId().equals(deviceId)) throw new IllegalArgumentException("Voice turn belongs to another device");
        turn.bindConversation(conversationId);
        turnRepository.save(turn);
    }

    @Transactional
    public void recordServerStage(
            UUID deviceId,
            UUID turnId,
            VoiceTurnStage stage,
            VoiceTurnFailureCode failureCode
    ) {
        recordServerStage(deviceId, turnId, stage, failureCode, null, null);
    }

    @Transactional
    public void recordServerStage(UUID deviceId, UUID turnId, VoiceTurnStage stage,
                                  VoiceTurnFailureCode failureCode, Integer durationMs,
                                  String diagnosticCode) {
        if (stage.isDeviceStage() && stage != VoiceTurnStage.CANCELLED && stage != VoiceTurnStage.FAILED) {
            throw new IllegalArgumentException("Invalid server voice turn stage");
        }
        if (durationMs != null && (durationMs < 0 || durationMs > MAX_ELAPSED_MS) ||
                diagnosticCode != null && !diagnosticCode.matches("[a-z0-9_]{1,80}")) {
            throw new IllegalArgumentException("Invalid voice phase diagnostics");
        }
        record(deviceId, turnId, stage, VoiceTurnStageSource.SERVER, null, failureCode,
                durationMs, diagnosticCode);
    }

    @Transactional
    public int cancelActiveTurns(UUID deviceId) {
        List<VoiceTurnEntity> activeTurns = turnRepository.findByDeviceIdAndStatusIn(deviceId, ACTIVE_STATUSES);
        activeTurns.forEach(turn -> record(
                deviceId,
                turn.getId(),
                VoiceTurnStage.CANCELLED,
                VoiceTurnStageSource.SERVER,
                null,
                null
        ));
        return activeTurns.size();
    }

    private void record(
            UUID deviceId,
            UUID turnId,
            VoiceTurnStage stage,
            VoiceTurnStageSource source,
            Integer elapsedMs,
            VoiceTurnFailureCode failureCode
    ) {
        record(deviceId, turnId, stage, source, elapsedMs, failureCode, null, null);
    }

    private void record(UUID deviceId, UUID turnId, VoiceTurnStage stage, VoiceTurnStageSource source,
                        Integer elapsedMs, VoiceTurnFailureCode failureCode, Integer durationMs,
                        String diagnosticCode) {
        if ((stage == VoiceTurnStage.FAILED) != (failureCode != null)) {
            throw new IllegalArgumentException("Voice turn failure code does not match stage");
        }
        turnRepository.lockDiagnosticTurn(turnId);
        if (turnRepository.isPrivacyErased(turnId, clock.instant())) return;
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
                failureCode,
                durationMs,
                diagnosticCode
        ));
    }

    @Transactional(readOnly = true)
    public List<VoiceTurnSnapshot> recent(UUID deviceId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        List<VoiceTurnEntity> turns = turnRepository.findByDeviceIdOrderByStartedAtDesc(
                deviceId,
                PageRequest.of(0, safeLimit)
        );
        return snapshots(turns);
    }

    @Transactional(readOnly = true)
    public List<VoiceTurnSnapshot> between(UUID deviceId, Instant from, Instant to) {
        return snapshots(turnRepository
                .findByDeviceIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(deviceId, from, to));
    }

    private List<VoiceTurnSnapshot> snapshots(List<VoiceTurnEntity> turns) {
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
                events.getOrDefault(turn.getId(), List.of()),
                completeness(turn.getStatus(), events.getOrDefault(turn.getId(), List.of()))
        )).toList();
    }

    @Scheduled(cron = "0 23 3 * * *")
    @Transactional
    public void deleteExpired() {
        turnRepository.deleteByStartedAtBefore(clock.instant().minus(RETENTION));
        turnRepository.deleteExpiredPrivacyTombstones(clock.instant());
    }

    private VoiceTurnEventSnapshot snapshot(VoiceTurnEventEntity event) {
        return new VoiceTurnEventSnapshot(
                event.getStage(),
                event.getSource(),
                event.getOccurredAt(),
                event.getElapsedMs(),
                event.getFailureCode(),
                event.getDurationMs(),
                event.getDiagnosticCode(),
                measurementSource(event.getSource(), event.getDurationMs(), event.getDiagnosticCode()),
                event.getSource() == VoiceTurnStageSource.DEVICE && event.getStage() == VoiceTurnStage.SPEECH_CAPTURED
                    ? event.getDurationMs() : null,
                event.getSource() == VoiceTurnStageSource.DEVICE && event.getStage() == VoiceTurnStage.SPEECH_CAPTURED
                    ? captureLimit(event.getDiagnosticCode()) : null
        );
    }

    public record VoiceTurnSnapshot(
            UUID turnId,
            VoiceTurnStatus status,
            VoiceTurnFailureCode failureCode,
            Instant startedAt,
            Instant updatedAt,
            List<VoiceTurnEventSnapshot> events,
            String diagnosticCompleteness
    ) {
        public VoiceTurnSnapshot(UUID turnId, VoiceTurnStatus status, VoiceTurnFailureCode failureCode,
                                 Instant startedAt, Instant updatedAt, List<VoiceTurnEventSnapshot> events) {
            this(turnId, status, failureCode, startedAt, updatedAt, events, completeness(status, events));
        }
    }

    private static String completeness(VoiceTurnStatus status, List<VoiceTurnEventSnapshot> events) {
        if (events.isEmpty()) return "UNKNOWN";
        if (status != VoiceTurnStatus.COMPLETED) return "PARTIAL";
        boolean server = List.of(VoiceTurnStage.REQUEST_RECEIVED, VoiceTurnStage.ASR_COMPLETED,
                VoiceTurnStage.LLM_COMPLETED, VoiceTurnStage.TTS_COMPLETED).stream()
                .allMatch(stage -> events.stream().anyMatch(e -> e.source() == VoiceTurnStageSource.SERVER && e.stage() == stage));
        boolean device = List.of(VoiceTurnStage.SPEECH_CAPTURED, VoiceTurnStage.PLAYBACK_STARTED,
                VoiceTurnStage.PLAYBACK_COMPLETED).stream()
                .allMatch(stage -> events.stream().anyMatch(e -> e.source() == VoiceTurnStageSource.DEVICE && e.stage() == stage));
        boolean measurements = events.stream().anyMatch(e -> e.stage() == VoiceTurnStage.SPEECH_CAPTURED
                && e.source() == VoiceTurnStageSource.DEVICE && e.endpointToLocalFeedbackMs() != null
                && e.captureLimitReached() != null)
                && events.stream().anyMatch(e -> e.stage() == VoiceTurnStage.PLAYBACK_STARTED
                && e.source() == VoiceTurnStageSource.DEVICE && "playback_codec_write".equals(e.diagnosticCode()));
        List<VoiceTurnEventSnapshot> capture = deviceEvents(events, VoiceTurnStage.SPEECH_CAPTURED);
        List<VoiceTurnEventSnapshot> playback = deviceEvents(events, VoiceTurnStage.PLAYBACK_STARTED);
        List<VoiceTurnEventSnapshot> completed = deviceEvents(events, VoiceTurnStage.PLAYBACK_COMPLETED);
        boolean ordered = capture.size() == 1 && playback.size() == 1 && completed.size() == 1
                && capture.getFirst().elapsedMs() != null && playback.getFirst().elapsedMs() != null
                && completed.getFirst().elapsedMs() != null && capture.getFirst().elapsedMs() >= 0
                && capture.getFirst().elapsedMs() <= playback.getFirst().elapsedMs()
                && playback.getFirst().elapsedMs() <= completed.getFirst().elapsedMs()
                && completed.getFirst().elapsedMs() <= MAX_ELAPSED_MS;
        return server && device && measurements && ordered ? "COMPLETE" : "PARTIAL";
    }

    private static List<VoiceTurnEventSnapshot> deviceEvents(List<VoiceTurnEventSnapshot> events, VoiceTurnStage stage) {
        return events.stream().filter(e -> e.source() == VoiceTurnStageSource.DEVICE && e.stage() == stage).toList();
    }

    public record VoiceTurnEventSnapshot(
            VoiceTurnStage stage,
            VoiceTurnStageSource source,
            Instant occurredAt,
            Integer elapsedMs,
            VoiceTurnFailureCode failureCode,
            Integer durationMs,
            String diagnosticCode,
            String measurementSource,
            Integer endpointToLocalFeedbackMs,
            Boolean captureLimitReached
    ) {
        public VoiceTurnEventSnapshot(VoiceTurnStage stage, VoiceTurnStageSource source, Instant occurredAt,
                                      Integer elapsedMs, VoiceTurnFailureCode failureCode,
                                      Integer durationMs, String diagnosticCode) {
            this(stage, source, occurredAt, elapsedMs, failureCode, durationMs, diagnosticCode,
                    VoiceTurnDiagnosticsService.measurementSource(source, durationMs, diagnosticCode),
                    source == VoiceTurnStageSource.DEVICE && stage == VoiceTurnStage.SPEECH_CAPTURED ? durationMs : null,
                    source == VoiceTurnStageSource.DEVICE && stage == VoiceTurnStage.SPEECH_CAPTURED
                        ? captureLimit(diagnosticCode) : null);
        }
        public VoiceTurnEventSnapshot(VoiceTurnStage stage, VoiceTurnStageSource source,
                                      Instant occurredAt, Integer elapsedMs, VoiceTurnFailureCode failureCode) {
            this(stage, source, occurredAt, elapsedMs, failureCode, null, null);
        }
    }
}
