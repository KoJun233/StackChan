package com.kj.stackchan.reminder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceCommandResult;
import com.kj.stackchan.notification.NotificationIntegrationEntity;
import com.kj.stackchan.notification.NotificationIntegrationRepository;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.speech.SpeechRuntimeClient;
import com.kj.stackchan.speech.SpeechProviderUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private NotificationIntegrationRepository integrationRepository;

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
    void waitsForDigestWindowThenDispatchesOneDeterministicAudioForAllItems() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity(
                "Codex", deviceId, roleId, true, 30, NOW.minusSeconds(60));
        ReminderEntity first = external(roleId, deviceId, integration.getId(), "第一项", NOW.minusSeconds(31));
        ReminderEntity second = external(roleId, deviceId, integration.getId(), "第二项", NOW.minusSeconds(10));
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW)).thenReturn(List.of(first, second));
        when(integrationRepository.findById(integration.getId())).thenReturn(Optional.of(integration));
        when(repository.findTop10ByNotificationIntegrationIdAndDeviceIdAndRoleIdAndSourceAndStatusAndScheduledAtLessThanEqualAndDeliveryGroupIdIsNullOrderByCreatedAtAscIdAsc(
                integration.getId(), deviceId, roleId, ReminderSource.EXTERNAL, ReminderStatus.PENDING, NOW))
                .thenReturn(List.of(first, second));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("收到 2 条通知：1，第一项；2，第二项。", roleId)).thenReturn(new byte[44]);
        when(gateway.speakReminder(org.mockito.ArgumentMatchers.eq(deviceId),
                org.mockito.ArgumentMatchers.eq(first.getId()), anyString())).thenReturn(true);

        serviceWithDigest().dispatchDueReminders();

        assertThat(first.getStatus()).isEqualTo(ReminderStatus.DISPATCHED);
        assertThat(second.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(first.getDeliveryGroupId()).isEqualTo(first.getId());
        assertThat(second.getDeliveryGroupId()).isEqualTo(first.getId());
        verify(gateway, org.mockito.Mockito.times(1)).speakReminder(
                org.mockito.ArgumentMatchers.eq(deviceId), org.mockito.ArgumentMatchers.eq(first.getId()), anyString());
    }

    @Test
    void keepsOldestNotificationAsDigestLeaderWhenDueListStartsWithAnotherItem() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity(
                "Codex", deviceId, roleId, true, 30, NOW.minusSeconds(60));
        ReminderEntity oldest = external(roleId, deviceId, integration.getId(), "oldest", NOW.minusSeconds(40));
        ReminderEntity later = external(roleId, deviceId, integration.getId(), "later", NOW.minusSeconds(35));
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW)).thenReturn(List.of(later, oldest));
        when(integrationRepository.findById(integration.getId())).thenReturn(Optional.of(integration));
        when(repository.findTop10ByNotificationIntegrationIdAndDeviceIdAndRoleIdAndSourceAndStatusAndScheduledAtLessThanEqualAndDeliveryGroupIdIsNullOrderByCreatedAtAscIdAsc(
                integration.getId(), deviceId, roleId, ReminderSource.EXTERNAL, ReminderStatus.PENDING, NOW))
                .thenReturn(List.of(oldest, later));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("收到 2 条通知：1，oldest；2，later。", roleId)).thenReturn(new byte[44]);
        when(gateway.speakReminder(org.mockito.ArgumentMatchers.eq(deviceId),
                org.mockito.ArgumentMatchers.eq(oldest.getId()), anyString())).thenReturn(true);

        serviceWithDigest().dispatchDueReminders();

        assertThat(oldest.getStatus()).isEqualTo(ReminderStatus.DISPATCHED);
        assertThat(oldest.getDeliveryGroupId()).isEqualTo(oldest.getId());
        assertThat(later.getDeliveryGroupId()).isEqualTo(oldest.getId());
        verify(gateway, org.mockito.Mockito.times(1)).speakReminder(
                org.mockito.ArgumentMatchers.eq(deviceId), org.mockito.ArgumentMatchers.eq(oldest.getId()), anyString());
    }

    @Test
    void digestAckCompletesEveryGroupedNotification() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity first = new ReminderEntity(deviceId, "第一项", NOW, "UTC", NOW);
        ReminderEntity second = new ReminderEntity(deviceId, "第二项", NOW, "UTC", NOW);
        first.markDigestLeader("digest-command", new byte[44], NOW);
        second.joinDeliveryGroup(first.getId(), NOW);
        when(repository.findByCommandId("digest-command")).thenReturn(Optional.of(first));
        when(repository.findAllByDeliveryGroupId(first.getId())).thenReturn(List.of(first, second));

        serviceWithDigest().record(deviceId, "digest-command", true);

        assertThat(first.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
        assertThat(second.getStatus()).isEqualTo(ReminderStatus.DELIVERED);
    }

    @Test
    void interactiveNotificationNeverJoinsDigest() {
        UUID deviceId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity(
                "Codex", deviceId, roleId, true, 30, NOW.minusSeconds(60));
        ReminderEntity interactive = external(roleId, deviceId, integration.getId(), "请确认", NOW.minusSeconds(31));
        interactive.assignExternalMetadata(integration.getId(), "interactive", "b".repeat(64),
                NOW.plusSeconds(3600), java.util.Set.of(com.kj.stackchan.notification.NotificationResponseAction.ACKNOWLEDGE), NOW.minusSeconds(31));
        when(repository.findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAscIdAsc(
                ReminderStatus.PENDING, NOW)).thenReturn(List.of(interactive));
        when(gateway.isConnected(deviceId)).thenReturn(true);
        when(speechRuntimeClient.synthesize("请确认", roleId)).thenReturn(new byte[44]);
        when(gateway.speakReminder(org.mockito.ArgumentMatchers.eq(deviceId),
                org.mockito.ArgumentMatchers.eq(interactive.getId()), anyString())).thenReturn(true);

        serviceWithDigest().dispatchDueReminders();

        assertThat(interactive.getStatus()).isEqualTo(ReminderStatus.DISPATCHED);
        assertThat(interactive.getDeliveryGroupId()).isNull();
        verify(integrationRepository, never()).findById(any());
    }

    @Test
    void staleDigestRecoveryReturnsEveryMemberToPending() {
        UUID deviceId = UUID.randomUUID();
        ReminderEntity first = new ReminderEntity(deviceId, "第一项", NOW, "UTC", NOW.minusSeconds(600));
        ReminderEntity second = new ReminderEntity(deviceId, "第二项", NOW, "UTC", NOW.minusSeconds(600));
        first.markDigestLeader("stale-digest", new byte[44], NOW.minusSeconds(301));
        second.joinDeliveryGroup(first.getId(), NOW.minusSeconds(301));
        when(repository.findAllByStatusAndLastAttemptAtBefore(
                ReminderStatus.DISPATCHED, NOW.minusSeconds(300))).thenReturn(List.of(first));
        when(repository.findAllByDeliveryGroupId(first.getId())).thenReturn(List.of(first, second));

        int recovered = serviceWithDigest().recoverStaleDispatches();

        assertThat(recovered).isOne();
        assertThat(first.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(second.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(first.getDeliveryGroupId()).isNull();
        assertThat(second.getDeliveryGroupId()).isNull();
        verify(repository).saveAll(List.of(first, second));
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

    private ReminderDeliveryService serviceWithDigest() {
        return new ReminderDeliveryService(
                repository, gateway, speechRuntimeClient, Clock.fixed(NOW, ZoneOffset.UTC),
                null, null, null, integrationRepository
        );
    }

    private ReminderEntity external(
            UUID roleId, UUID deviceId, UUID integrationId, String content, Instant createdAt
    ) {
        ReminderEntity reminder = new ReminderEntity(
                roleId, deviceId, content, createdAt, "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.EXTERNAL, createdAt);
        reminder.assignExternalMetadata(integrationId, UUID.randomUUID().toString(), "a".repeat(64),
                NOW.plusSeconds(3600), createdAt);
        return reminder;
    }
}
