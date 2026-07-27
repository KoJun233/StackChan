package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.speech.VoiceTurnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProactiveInteractionServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T12:30:00Z");

    @Mock private InteractionSettingsService settingsService;
    @Mock private ReminderRepository reminderRepository;
    @Mock private VoiceTurnRepository voiceTurnRepository;
    @Mock private DeviceCommandGateway commandGateway;

    @Test
    void blocksOnlyOnRecentlyUpdatedVoiceTurns() {
        UUID deviceId = UUID.randomUUID();
        var settings = new InteractionSettingsService.InteractionSettingsSnapshot(
                deviceId, 50, false, false, LocalTime.of(22, 0), LocalTime.of(7, 0), "UTC",
                MissedReminderPolicy.PLAY_NOW, 10, true, LocalTime.MIN, LocalTime.MAX,
                30, 2, "hello", null, LocalDate.of(2026, 7, 27), 0, NOW
        );
        when(settingsService.proactiveCandidates()).thenReturn(List.of(settings));
        when(settingsService.isProactiveEligible(settings, NOW)).thenReturn(true);
        when(commandGateway.isConnected(deviceId)).thenReturn(true);
        when(voiceTurnRepository.existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                eq(deviceId), any(), eq(NOW.minusSeconds(15 * 60))
        )).thenReturn(true);

        int generated = service().generateDueGreetings();

        assertThat(generated).isZero();
        verify(voiceTurnRepository).existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                eq(deviceId), any(), eq(NOW.minusSeconds(15 * 60))
        );
    }

    private ProactiveInteractionService service() {
        return new ProactiveInteractionService(
                settingsService,
                reminderRepository,
                voiceTurnRepository,
                commandGateway,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
