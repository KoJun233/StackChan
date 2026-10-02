package com.kj.stackchan.speech;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoiceTurnDiagnosticsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");

    @Mock
    private VoiceTurnRepository turnRepository;
    @Mock
    private VoiceTurnEventRepository eventRepository;

    @Test
    void persistsBoundedPhaseDurationAndSafeCodeWithoutDeviceClockValues() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        when(turnRepository.findById(turnId)).thenReturn(Optional.empty());
        service().recordServerStage(deviceId, turnId, VoiceTurnStage.FAILED,
                VoiceTurnFailureCode.ASR_UNAVAILABLE, 1250, "dashscope_free_quota_only");
        ArgumentCaptor<VoiceTurnEventEntity> event = ArgumentCaptor.forClass(VoiceTurnEventEntity.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().getDurationMs()).isEqualTo(1250);
        assertThat(event.getValue().getDiagnosticCode()).isEqualTo("dashscope_free_quota_only");
        assertThat(event.getValue().getElapsedMs()).isNull();
    }

    @Test
    void rejectsUnboundedDurationsAndUnstructuredDiagnosticPayloads() {
        assertThatThrownBy(() -> service().recordServerStage(UUID.randomUUID(), UUID.randomUUID(),
                VoiceTurnStage.ASR_COMPLETED, null, 300001, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().recordServerStage(UUID.randomUUID(), UUID.randomUUID(),
                VoiceTurnStage.FAILED, VoiceTurnFailureCode.ASR_UNAVAILABLE, 10, "Bearer secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recordsOnlyStructuredDeviceStageMetadata() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        when(turnRepository.findById(turnId)).thenReturn(Optional.empty());

        service().recordDeviceStage(
                deviceId,
                turnId,
                VoiceTurnStage.FAILED,
                1250,
                VoiceTurnFailureCode.NO_SPEECH
        );

        ArgumentCaptor<VoiceTurnEntity> turn = ArgumentCaptor.forClass(VoiceTurnEntity.class);
        ArgumentCaptor<VoiceTurnEventEntity> event = ArgumentCaptor.forClass(VoiceTurnEventEntity.class);
        verify(turnRepository).save(turn.capture());
        verify(eventRepository).save(event.capture());
        assertThat(turn.getValue().getDeviceId()).isEqualTo(deviceId);
        assertThat(turn.getValue().getStatus()).isEqualTo(VoiceTurnStatus.FAILED);
        assertThat(event.getValue().getStage()).isEqualTo(VoiceTurnStage.FAILED);
        assertThat(event.getValue().getElapsedMs()).isEqualTo(1250);
        assertThat(event.getValue().getFailureCode()).isEqualTo(VoiceTurnFailureCode.NO_SPEECH);
    }

    @Test
    void rejectsServerOnlyStagesAndUnboundedElapsedTimeFromADevice() {
        assertThatThrownBy(() -> service().recordDeviceStage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                VoiceTurnStage.LLM_COMPLETED,
                20,
                null
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().recordDeviceStage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                VoiceTurnStage.LISTENING,
                300001,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancellationIsTerminalAndLateStagesCannotChangeTheOutcome() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        VoiceTurnEntity turn = new VoiceTurnEntity(turnId, deviceId, NOW);
        when(turnRepository.findById(turnId)).thenReturn(Optional.of(turn));

        service().recordDeviceStage(deviceId, turnId, VoiceTurnStage.CANCELLED, 100, null);
        service().recordServerStage(deviceId, turnId, VoiceTurnStage.TTS_COMPLETED, null);

        assertThat(turn.getStatus()).isEqualTo(VoiceTurnStatus.CANCELLED);
        assertThat(turn.getFailureCode()).isNull();
    }

    @Test
    void followUpTimeoutAndConversationEndAreNormalCompletionStages() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        VoiceTurnEntity turn = new VoiceTurnEntity(turnId, deviceId, NOW);
        when(turnRepository.findById(turnId)).thenReturn(Optional.of(turn));

        service().recordDeviceStage(deviceId, turnId, VoiceTurnStage.FOLLOW_UP_LISTENING, 0, null);
        service().recordDeviceStage(deviceId, turnId, VoiceTurnStage.FOLLOW_UP_TIMEOUT, 8000, null);
        service().recordDeviceStage(deviceId, turnId, VoiceTurnStage.CONVERSATION_ENDED, 8001, null);

        assertThat(turn.getStatus()).isEqualTo(VoiceTurnStatus.COMPLETED);
        assertThat(turn.getFailureCode()).isNull();
    }

    @Test
    void manualInputReadyCompletesWithoutClaimingWakeListeningAndPreservesFailures() {
        UUID deviceId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        VoiceTurnEntity turn = new VoiceTurnEntity(turnId, deviceId, NOW);
        when(turnRepository.findById(turnId)).thenReturn(Optional.of(turn));
        service().recordDeviceStage(deviceId, turnId, VoiceTurnStage.MANUAL_INPUT_READY, 1000, null);
        assertThat(turn.getStatus()).isEqualTo(VoiceTurnStatus.COMPLETED);

        VoiceTurnEntity failed = new VoiceTurnEntity(UUID.randomUUID(), deviceId, NOW);
        failed.apply(VoiceTurnStage.FAILED, VoiceTurnFailureCode.NO_SPEECH, NOW);
        failed.apply(VoiceTurnStage.MANUAL_INPUT_READY, null, NOW);
        assertThat(failed.getStatus()).isEqualTo(VoiceTurnStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo(VoiceTurnFailureCode.NO_SPEECH);
    }

    @Test
    void cancelsEveryActiveTurnForADevice() {
        UUID deviceId = UUID.randomUUID();
        VoiceTurnEntity first = new VoiceTurnEntity(UUID.randomUUID(), deviceId, NOW);
        VoiceTurnEntity second = new VoiceTurnEntity(UUID.randomUUID(), deviceId, NOW);
        second.apply(VoiceTurnStage.TTS_COMPLETED, null, NOW);
        when(turnRepository.findByDeviceIdAndStatusIn(any(), any())).thenReturn(List.of(first, second));
        when(turnRepository.findById(first.getId())).thenReturn(Optional.of(first));
        when(turnRepository.findById(second.getId())).thenReturn(Optional.of(second));

        int cancelled = service().cancelActiveTurns(deviceId);

        assertThat(cancelled).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(VoiceTurnStatus.CANCELLED);
        assertThat(second.getStatus()).isEqualTo(VoiceTurnStatus.CANCELLED);
    }

    @Test
    void deletesOnlyTurnsOutsideTheSevenDayRetentionWindow() {
        service().deleteExpired();

        verify(turnRepository).deleteByStartedAtBefore(NOW.minus(VoiceTurnDiagnosticsService.RETENTION));
    }

    private VoiceTurnDiagnosticsService service() {
        return new VoiceTurnDiagnosticsService(
                turnRepository,
                eventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
