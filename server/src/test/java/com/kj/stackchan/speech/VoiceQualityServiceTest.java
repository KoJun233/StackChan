package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService.VoiceTurnEventSnapshot;
import com.kj.stackchan.speech.VoiceTurnDiagnosticsService.VoiceTurnSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceQualityServiceTest {
    static final Instant NOW = Instant.parse("2026-10-13T04:00:00Z");
    static final UUID DEVICE = UUID.randomUUID();
    @Mock DeviceRepository devices;
    @Mock VoiceTurnDiagnosticsService diagnostics;
    VoiceQualityService service;

    @BeforeEach void setup() {
        service = new VoiceQualityService(devices, diagnostics, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    void samples(List<VoiceTurnSnapshot> turns) {
        when(devices.existsById(DEVICE)).thenReturn(true);
        when(diagnostics.between(DEVICE, NOW.minusSeconds(86_400), NOW)).thenReturn(turns);
    }

    @Test void defaultWindowReturnsEmptyUnknownInsteadOfZeroLatency() {
        samples(List.of());
        var result = service.summary(DEVICE, null, null);
        assertThat(result.from()).isEqualTo(NOW.minusSeconds(86_400));
        assertThat(result.to()).isEqualTo(NOW);
        assertThat(result.totalAttempts()).isZero();
        assertThat(result.endpointToFirstSound().p50Ms()).isNull();
        assertThat(result.endpointToFirstSound().maximumMs()).isNull();
        assertThat(result.physicalObservationRequired()).isTrue();
    }

    @Test void insufficientSamplesStillDiscloseEverySlowSampleAndMaximum() {
        samples(IntStream.rangeClosed(1,29).mapToObj(i -> turn(VoiceTurnStatus.COMPLETED, null,
                "voice_casual", "playback_codec_write", i * 1000, 201, false)).toList());
        var result = service.summary(DEVICE, null, null);
        assertThat(result.endpointToFirstSound().applicableSamples()).isEqualTo(29);
        assertThat(result.endpointToFirstSound().p95Ms()).isNull();
        assertThat(result.endpointToFirstSound().maximumMs()).isEqualTo(29_000);
        assertThat(result.endpointToFirstSound().overThresholdSamples()).isEqualTo(24);
        assertThat(result.endpointToLocalFeedback().overThresholdSamples()).isEqualTo(29);
    }

    @Test void nearestRankUsesAllThirtySamplesAndExcludesFailedCancelledAndComplexTurns() {
        var turns = new ArrayList<>(IntStream.rangeClosed(1,30).mapToObj(i -> turn(
                VoiceTurnStatus.COMPLETED, null, "voice_casual", "playback_codec_write", i*100, 50, false)).toList());
        turns.add(turn(VoiceTurnStatus.COMPLETED, null, "voice_required_tool", "playback_codec_write", 50_000, 50, false));
        turns.add(turn(VoiceTurnStatus.FAILED, VoiceTurnFailureCode.TTS_UNAVAILABLE, "voice_casual", null, 0, 100, false));
        turns.add(turn(VoiceTurnStatus.CANCELLED, null, "voice_casual", "playback_codec_write", 10, 100, false));
        turns.add(turn(VoiceTurnStatus.IN_PROGRESS, null, null, null, 0, null, null));
        samples(turns);
        var result = service.summary(DEVICE, null, null);
        assertThat(result.totalAttempts()).isEqualTo(34);
        assertThat(result.successfulTurns()).isEqualTo(31);
        assertThat(result.failedTurns()).isEqualTo(1);
        assertThat(result.cancelledTurns()).isEqualTo(1);
        assertThat(result.incompleteTurns()).isEqualTo(1);
        assertThat(result.failureCounts()).containsEntry("TTS_UNAVAILABLE",1);
        assertThat(result.ordinaryChatSuccessfulTurns()).isEqualTo(30);
        assertThat(result.endpointToFirstSound().applicableSamples()).isEqualTo(30);
        assertThat(result.endpointToFirstSound().p50Ms()).isEqualTo(1500);
        assertThat(result.endpointToFirstSound().p95Ms()).isEqualTo(2900);
        assertThat(result.endpointToLocalFeedback().applicableSamples()).isEqualTo(33);
    }

    @Test void legacyPrematurePlaybackMissingCaptureAndNegativeElapsedRemainUnknown() {
        var old = turn(VoiceTurnStatus.COMPLETED, null,"voice_casual", null, 2000, null, null);
        var negative = turn(VoiceTurnStatus.COMPLETED, null,"voice_casual", "playback_codec_write", -1, 20, false);
        var capped = turn(VoiceTurnStatus.COMPLETED,null,"voice_casual","playback_codec_write",100,20,true);
        var duplicateEvents = new ArrayList<>(negative.events());
        duplicateEvents.add(negative.events().getFirst());
        var duplicate = new VoiceTurnSnapshot(UUID.randomUUID(),VoiceTurnStatus.COMPLETED,null,NOW,NOW,duplicateEvents);
        samples(List.of(old,negative,capped,duplicate));
        var result = service.summary(DEVICE,null,null);
        assertThat(result.endpointToFirstSound().applicableSamples()).isZero();
        assertThat(result.endpointToFirstSound().missingSamples()).isEqualTo(4);
    }

    @Test void invalidTimeWindowsAreRejectedBeforeDeviceAndDataLookup() {
        for (Instant[] range : List.of(new Instant[]{NOW,NOW}, new Instant[]{NOW,NOW.minusSeconds(1)},
                new Instant[]{NOW.minusSeconds(604_801),NOW})) {
            assertThatThrownBy(() -> service.summary(DEVICE,range[0],range[1]))
                    .isInstanceOf(VoiceQualityService.QualityRequestException.class);
        }
        verifyNoInteractions(devices,diagnostics);
    }

    @Test void invertedPlaybackCompletionCannotProduceSuccessfulLatencyOrCompleteEvidence() {
        var valid = turn(VoiceTurnStatus.COMPLETED,null,"voice_casual","playback_codec_write",2000,30,false);
        var events = new ArrayList<>(valid.events());
        events.set(3,new VoiceTurnEventSnapshot(VoiceTurnStage.PLAYBACK_COMPLETED,VoiceTurnStageSource.DEVICE,NOW,100,null));
        var inverted = new VoiceTurnSnapshot(UUID.randomUUID(),VoiceTurnStatus.COMPLETED,null,NOW,NOW,events);
        samples(List.of(inverted));
        var result = service.summary(DEVICE,null,null);
        assertThat(result.endpointToFirstSound().applicableSamples()).isZero();
        assertThat(result.endpointToFirstSound().missingSamples()).isEqualTo(1);
        assertThat(inverted.diagnosticCompleteness()).isEqualTo("PARTIAL");
    }

    @Test void exactlySevenDaysAndHalfOpenBoundaryAreAccepted() {
        when(devices.existsById(DEVICE)).thenReturn(true);
        when(diagnostics.between(DEVICE,NOW.minusSeconds(604_800),NOW)).thenReturn(List.of());
        service.summary(DEVICE,NOW.minusSeconds(604_800),NOW);
        verify(diagnostics).between(DEVICE,NOW.minusSeconds(604_800),NOW);
    }

    @Test void inaccessibleDeviceNeverQueriesTimingData() {
        assertThatThrownBy(() -> service.summary(DEVICE,null,null))
                .isInstanceOf(VoiceQualityService.QualityRequestException.class)
                .satisfies(e -> assertThat(((VoiceQualityService.QualityRequestException)e).notFound()).isTrue());
        verifyNoInteractions(diagnostics);
    }

    static VoiceTurnSnapshot turn(VoiceTurnStatus status, VoiceTurnFailureCode failure, String route,
                                  String playbackCode, int latency, Integer feedback, Boolean capped) {
        List<VoiceTurnEventSnapshot> events = List.of(
                new VoiceTurnEventSnapshot(VoiceTurnStage.SPEECH_CAPTURED,VoiceTurnStageSource.DEVICE,NOW,100,null,
                        feedback,capped == null ? null : capped ? "capture_limit" : "capture_endpoint"),
                new VoiceTurnEventSnapshot(VoiceTurnStage.LLM_COMPLETED,VoiceTurnStageSource.SERVER,NOW,null,null,10,route),
                new VoiceTurnEventSnapshot(VoiceTurnStage.PLAYBACK_STARTED,VoiceTurnStageSource.DEVICE,NOW,100+latency,null,null,playbackCode),
                new VoiceTurnEventSnapshot(VoiceTurnStage.PLAYBACK_COMPLETED,VoiceTurnStageSource.DEVICE,NOW,150+Math.max(latency,0),null));
        return new VoiceTurnSnapshot(UUID.randomUUID(),status,failure,NOW,NOW,events);
    }
}
