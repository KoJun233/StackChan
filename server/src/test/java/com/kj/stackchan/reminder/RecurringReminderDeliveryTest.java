package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import com.kj.stackchan.speech.VoiceTurnRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringReminderDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-07-27T10:30:00Z");

    @Mock private ReminderRepository repository;
    @Mock private DeviceCommandGateway gateway;
    @Mock private SpeechRuntimeClient speechRuntimeClient;
    @Mock private InteractionSettingsService interactionSettingsService;
    @Mock private VoiceTurnRepository voiceTurnRepository;

    @Test
    void acknowledgedOccurrenceReturnsToPendingAtNextLocalOccurrence() {
        UUID deviceId = UUID.randomUUID();
        Instant due = NOW.minusSeconds(1);
        ReminderEntity reminder = new ReminderEntity(
                deviceId, "喝水", due, "UTC", ReminderRecurrence.DAILY, 1,
                LocalDateTime.ofInstant(due, ZoneOffset.UTC), ReminderSource.USER, due
        );
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("喝水")).thenReturn(new byte[44]);
        when(gateway.speakReminder(org.mockito.ArgumentMatchers.eq(deviceId),
                org.mockito.ArgumentMatchers.eq(reminder.getId()), anyString())).thenReturn(true);

        ReminderDeliveryService service = service();
        service.dispatchDueReminders();
        when(repository.findByCommandId(reminder.getCommandId())).thenReturn(Optional.of(reminder));
        service.record(deviceId, reminder.getCommandId(), true);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(reminder.getLastOutcome()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(reminder.getScheduledAt()).isEqualTo(Instant.parse("2026-07-28T10:29:59Z"));
        assertThat(reminder.getCommandId()).isNull();
    }

    @Test
    void checksOnlyRecentlyUpdatedVoiceTurnsBeforeDispatching() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "reminder", NOW, "UTC", NOW);
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("reminder")).thenReturn(new byte[44]);
        when(gateway.speakReminder(eq(deviceId), eq(reminder.getId()), anyString())).thenReturn(true);

        service().dispatchDueReminders();

        verify(voiceTurnRepository).existsByDeviceIdAndStatusInAndUpdatedAtAfter(
                eq(deviceId), any(), eq(NOW.minusSeconds(15 * 60))
        );
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DISPATCHED);
    }

    private ReminderDeliveryService service() {
        return new ReminderDeliveryService(
                repository, gateway, speechRuntimeClient, Clock.fixed(NOW, ZoneOffset.UTC),
                interactionSettingsService, voiceTurnRepository, new ReminderScheduleCalculator()
        );
    }
}
