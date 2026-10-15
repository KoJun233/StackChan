package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService.VoiceTurnEventSnapshot;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService.VoiceTurnSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoiceQualityService {
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_WINDOW = Duration.ofDays(7);
    private static final int MINIMUM_SAMPLES = 30;
    private final DeviceRepository devices;
    private final VoiceTurnDiagnosticsService diagnostics;
    private final Clock clock;

    public VoiceQualityService(DeviceRepository devices, VoiceTurnDiagnosticsService diagnostics, Clock clock) {
        this.devices = devices;
        this.diagnostics = diagnostics;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID deviceId, Instant requestedFrom, Instant requestedTo) {
        Instant to = requestedTo == null ? clock.instant() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(DEFAULT_WINDOW) : requestedFrom;
        if (deviceId == null || !from.isBefore(to) || Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new QualityRequestException(false);
        }
        // This repository has one administrator owner; only their managed, existing devices are selectable.
        if (!devices.existsById(deviceId)) throw new QualityRequestException(true);
        List<VoiceTurnSnapshot> turns = diagnostics.between(deviceId, from, to);
        List<Integer> latency = new ArrayList<>();
        List<Integer> feedback = new ArrayList<>();
        int successful = 0, failed = 0, cancelled = 0, ordinary = 0, feedbackEligible = 0;
        Map<String, Integer> failures = new TreeMap<>();
        for (VoiceTurnSnapshot turn : turns) {
            if (turn.status() == VoiceTurnStatus.COMPLETED) successful++;
            else if (turn.status() == VoiceTurnStatus.FAILED) {
                failed++;
                if (turn.failureCode() != null) failures.merge(turn.failureCode().name(), 1, Integer::sum);
            } else if (turn.status() == VoiceTurnStatus.CANCELLED) cancelled++;
            VoiceTurnEventSnapshot capture = one(turn, VoiceTurnStageSource.DEVICE, VoiceTurnStage.SPEECH_CAPTURED);
            if (capture != null) {
                feedbackEligible++;
                if (capture.endpointToLocalFeedbackMs() != null && "DEVICE_MONOTONIC".equals(capture.measurementSource())
                        && capture.endpointToLocalFeedbackMs() >= 0 && capture.endpointToLocalFeedbackMs() <= 300_000) {
                    feedback.add(capture.endpointToLocalFeedbackMs());
                }
            }
            VoiceTurnEventSnapshot llm = one(turn, VoiceTurnStageSource.SERVER, VoiceTurnStage.LLM_COMPLETED);
            if (turn.status() != VoiceTurnStatus.COMPLETED || llm == null || !"voice_casual".equals(llm.diagnosticCode())
                    || one(turn, VoiceTurnStageSource.DEVICE, VoiceTurnStage.PLAYBACK_COMPLETED) == null) continue;
            ordinary++;
            VoiceTurnEventSnapshot playback = one(turn, VoiceTurnStageSource.DEVICE, VoiceTurnStage.PLAYBACK_STARTED);
            VoiceTurnEventSnapshot completed = one(turn, VoiceTurnStageSource.DEVICE, VoiceTurnStage.PLAYBACK_COMPLETED);
            if (capture == null || playback == null || !Boolean.FALSE.equals(capture.captureLimitReached())
                    || !"playback_codec_write".equals(playback.diagnosticCode())
                    || !"DEVICE_MONOTONIC".equals(capture.measurementSource())
                    || !"DEVICE_MONOTONIC".equals(playback.measurementSource())
                    || capture.elapsedMs() == null || playback.elapsedMs() == null
                    || completed == null || completed.elapsedMs() == null
                    || completed.elapsedMs() < playback.elapsedMs() || completed.elapsedMs() > 300_000) continue;
            int value = playback.elapsedMs() - capture.elapsedMs();
            if (capture.elapsedMs() >= 0 && playback.elapsedMs() <= 300_000 && value >= 0) latency.add(value);
        }
        return new Summary(deviceId, from, to, turns.size(), successful, failed, cancelled,
                turns.size() - successful - failed - cancelled, Map.copyOf(failures),
                metric(latency, ordinary, 5_000), metric(feedback, feedbackEligible, 200), true,
                ordinary, turns.size() - ordinary);
    }

    private VoiceTurnEventSnapshot one(VoiceTurnSnapshot turn, VoiceTurnStageSource source, VoiceTurnStage stage) {
        List<VoiceTurnEventSnapshot> matching = turn.events().stream()
                .filter(e -> e.source() == source && e.stage() == stage).toList();
        return matching.size() == 1 ? matching.getFirst() : null;
    }

    private Metric metric(List<Integer> samples, int eligible, int threshold) {
        samples.sort(Integer::compareTo);
        boolean enough = samples.size() >= MINIMUM_SAMPLES;
        return new Metric("DEVICE_MONOTONIC", samples.size(), eligible - samples.size(), MINIMUM_SAMPLES,
                enough ? nearestRank(samples, 0.5) : null, enough ? nearestRank(samples, 0.95) : null,
                samples.isEmpty() ? null : samples.getLast(), (int) samples.stream().filter(value -> value > threshold).count());
    }

    private int nearestRank(List<Integer> sorted, double fraction) {
        return sorted.get((int) Math.ceil(fraction * sorted.size()) - 1);
    }

    public record Summary(UUID deviceId, Instant from, Instant to, int totalAttempts, int successfulTurns,
                          int failedTurns, int cancelledTurns, int incompleteTurns, Map<String, Integer> failureCounts,
                          Metric endpointToFirstSound, Metric endpointToLocalFeedback, boolean physicalObservationRequired,
                          int ordinaryChatSuccessfulTurns, int excludedLatencyTurns) { }
    public record Metric(String measurementSource, int applicableSamples, int missingSamples, int minimumSamples,
                         Integer p50Ms, Integer p95Ms, Integer maximumMs, int overThresholdSamples) { }
    public static class QualityRequestException extends RuntimeException {
        private final boolean notFound;
        public QualityRequestException(boolean notFound) { this.notFound = notFound; }
        public boolean notFound() { return notFound; }
    }
}
