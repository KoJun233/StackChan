package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderDeliveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T10:30:00Z");

    @Mock
    private ReminderRepository repository;
    @Mock
    private DeviceCommandGateway gateway;
    @Mock
    private SpeechRuntimeClient speechRuntimeClient;

    @Test
    void synthesizesAndDispatchesADueReminderThenCompletesOnAck() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "去拿外卖", NOW.minusSeconds(1), "Asia/Shanghai", NOW);
        byte[] audio = new byte[44];
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("去拿外卖")).thenReturn(audio);
        when(repository.saveAndFlush(reminder)).thenReturn(reminder);
        when(gateway.speakReminder(org.mockito.ArgumentMatchers.eq(deviceId),
                org.mockito.ArgumentMatchers.eq(reminder.getId()), anyString())).thenReturn(true);

        ReminderDeliveryService service = service();
        service.dispatchDueReminders();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DISPATCHED);
        assertThat(reminder.getAudioPayload()).isEqualTo(audio);
        when(repository.findByCommandId(reminder.getCommandId())).thenReturn(Optional.of(reminder));
        service.record(deviceId, reminder.getCommandId(), true);
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(reminder.getAudioPayload()).isNull();
        verify(repository).saveAndFlush(reminder);
    }

    @Test
    void leavesDueReminderPendingWhileDeviceIsOffline() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "去拿外卖", NOW.minusSeconds(1), "Asia/Shanghai", NOW);
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(false);

        service().dispatchDueReminders();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
    }

    @Test
    void recoversOnlyDispatchesOlderThanFiveMinutes() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity stale = new ReminderEntity(deviceId, "stale", NOW.minusSeconds(600), "Asia/Shanghai", NOW);
        stale.markDispatched("cmd-stale", new byte[44], NOW.minusSeconds(301));
        when(repository.findAllByStatusAndLastAttemptAtBefore(
                ReminderStatus.DISPATCHED,
                NOW.minusSeconds(300)
        )).thenReturn(List.of(stale));

        int recovered = service().recoverStaleDispatches();

        assertThat(recovered).isOne();
        assertThat(stale.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(stale.getCommandId()).isNull();
        assertThat(stale.getAudioPayload()).isNull();
    }

    private ReminderDeliveryService service() {
        return new ReminderDeliveryService(
                repository,
                gateway,
                speechRuntimeClient,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
