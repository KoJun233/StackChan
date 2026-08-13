package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceCommandResult;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import com.kj.stackchan.speech.SpeechProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
        UUID roleId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(
                roleId, deviceId, "去拿外卖", NOW.minusSeconds(1), "Asia/Shanghai",
                ReminderRecurrence.NONE, 1, null, ReminderSource.USER, NOW
        );
        byte[] audio = new byte[44];
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("去拿外卖", roleId)).thenReturn(audio);
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
    void keepsExternalNotificationQueuedWhileDeviceIsOffline() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "external", NOW.minusSeconds(1), "Asia/Shanghai", NOW);
        reminder.assignExternalMetadata(UUID.randomUUID(), "agent-run-1", "hash", NOW.plusSeconds(3600), NOW);
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(false);

        service().dispatchDueReminders();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        verify(speechRuntimeClient, never()).synthesize(anyString(), org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void defersNotificationWhenAnotherReminderIsAlreadyDispatchedForTheDevice() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "external", NOW.minusSeconds(1), "Asia/Shanghai", NOW);
        reminder.assignExternalMetadata(UUID.randomUUID(), "agent-run-2", "hash", NOW.plusSeconds(3600), NOW);
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(repository.existsByDeviceIdAndStatus(deviceId, ReminderStatus.DISPATCHED)).thenReturn(true);

        service().dispatchDueReminders();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOW.plusSeconds(60));
        verify(repository).save(reminder);
        verify(speechRuntimeClient, never()).synthesize(anyString(), org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void expiresQueuedExternalNotificationBeforeDispatch() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "external", NOW.minusSeconds(10), "Asia/Shanghai", NOW);
        reminder.assignExternalMetadata(UUID.randomUUID(), "agent-run-3", "hash", NOW, NOW);
        when(repository.findTop100BySourceAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(
                ReminderSource.EXTERNAL, ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));

        service().dispatchDueReminders();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.EXPIRED);
        assertThat(reminder.getFailureCode()).isEqualTo("notification_expired");
        verify(repository).save(reminder);
        verify(speechRuntimeClient, never()).synthesize(anyString(), org.mockito.ArgumentMatchers.any(UUID.class));
    }

    @Test
    void recordsSafeFailureWhenExternalNotificationTtsIsUnavailable() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "external", NOW.minusSeconds(1), "Asia/Shanghai", NOW);
        reminder.assignExternalMetadata(UUID.randomUUID(), "agent-run-4", "hash", NOW.plusSeconds(3600), NOW);
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW
        )).thenReturn(List.of(reminder));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("external", CompanionRoleEntity.DEFAULT_ROLE_ID))
                .thenThrow(new SpeechProviderUnavailableException());

        service().dispatchDueReminders();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
        assertThat(reminder.getFailureCode()).isEqualTo("speech_provider_unavailable");
        verify(repository).save(reminder);
    }

    @Test
    void ignoresReplayedAcknowledgementAfterNotificationWasDelivered() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "external", NOW, "Asia/Shanghai", NOW);
        reminder.assignExternalMetadata(UUID.randomUUID(), "agent-run-5", "hash", NOW.plusSeconds(3600), NOW);
        reminder.markDispatched("cmd-delivered", new byte[44], NOW);
        when(repository.findByCommandId("cmd-delivered")).thenReturn(Optional.of(reminder));

        ReminderDeliveryService service = service();
        service.record(deviceId, "cmd-delivered", true);
        service.record(deviceId, "cmd-delivered", true);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(reminder.getLastCompletedAt()).isEqualTo(NOW);
    }

    @Test
    void marksADispatchedReminderCancelledWhenPlaybackIsStoppedByTheUser() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity reminder = new ReminderEntity(deviceId, "reminder", NOW, "Asia/Shanghai", NOW);
        reminder.markDispatched("cmd-cancel", new byte[44], NOW);
        when(repository.findByCommandId("cmd-cancel")).thenReturn(Optional.of(reminder));

        service().record(deviceId, "cmd-cancel", false, DeviceCommandResult.CANCELLED);

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.CANCELLED);
        assertThat(reminder.getAudioPayload()).isNull();
        assertThat(reminder.getFailureCode()).isNull();
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
