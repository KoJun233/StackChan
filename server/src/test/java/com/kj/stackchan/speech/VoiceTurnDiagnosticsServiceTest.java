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
