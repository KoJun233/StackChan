package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.reminder.ReminderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InteractiveNotificationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");
    @Mock private ReminderRepository reminderRepository;
    @Mock private NotificationResponseRepository responseRepository;
    private InteractiveNotificationService service;

    @BeforeEach
    void setUp() {
        service = new InteractiveNotificationService(
                reminderRepository, responseRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(responseRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void findsOnlyExplicitlyActionableDeliveredNotificationInDeviceAndRoleScope() {
        ReminderEntity notification = delivered(Set.of(NotificationResponseAction.ACKNOWLEDGE));
        when(reminderRepository
                .findTop20ByDeviceIdAndRoleIdAndSourceAndStatusAndLastCompletedAtAfterOrderByLastCompletedAtDescIdDesc(
                        notification.getDeviceId(), notification.getRoleId(), ReminderSource.EXTERNAL,
                        ReminderStatus.DELIVERED, NOW.minusSeconds(86_400)))
                .thenReturn(List.of(notification));

        assertThat(service.latestActionable(
                notification.getDeviceId(), notification.getRoleId(), NotificationResponseAction.ACKNOWLEDGE))
                .isEqualTo(notification.getId());
        assertThat(service.latestActionable(
                notification.getDeviceId(), notification.getRoleId(), NotificationResponseAction.COMPLETE))
                .isNull();
    }

    @Test
    void snoozeRequeuesNotificationAndExtendsExpiryWithoutChangingScope() {
        ReminderEntity notification = delivered(Set.of(NotificationResponseAction.SNOOZE));
        when(reminderRepository.findByIdAndSourceForUpdate(notification.getId(), ReminderSource.EXTERNAL))
                .thenReturn(java.util.Optional.of(notification));

        var response = service.respond(notification.getId(), notification.getDeviceId(), notification.getRoleId(),
                NotificationResponseAction.SNOOZE, 10);

        assertThat(response.action()).isEqualTo(NotificationResponseAction.SNOOZE);
        assertThat(notification.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(notification.getScheduledAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(notification.getExpiresAt()).isEqualTo(NOW.plusSeconds(87_000));
    }

    @Test
    void rejectsCrossRoleResponseAndUnadvertisedAction() {
        ReminderEntity notification = delivered(Set.of(NotificationResponseAction.ACKNOWLEDGE));
        when(reminderRepository.findByIdAndSourceForUpdate(notification.getId(), ReminderSource.EXTERNAL))
                .thenReturn(java.util.Optional.of(notification));

        assertThatThrownBy(() -> service.respond(notification.getId(), notification.getDeviceId(), UUID.randomUUID(),
                NotificationResponseAction.ACKNOWLEDGE, null))
                .isInstanceOfSatisfying(NotificationApiException.class,
                        exception -> assertThat(exception.getStatus().value()).isEqualTo(404));
        assertThatThrownBy(() -> service.respondAdmin(
                notification.getId(), NotificationResponseAction.COMPLETE, null))
                .isInstanceOfSatisfying(NotificationApiException.class,
                        exception -> assertThat(exception.getStatus().value()).isEqualTo(400));
    }

    @Test
    void terminalResponseIsIdempotent() {
        ReminderEntity notification = delivered(Set.of(NotificationResponseAction.ACKNOWLEDGE));
        NotificationResponseEntity existing = new NotificationResponseEntity(
                notification.getId(), notification.getNotificationIntegrationId(),
                NotificationResponseAction.ACKNOWLEDGE, null, NOW.minusSeconds(1));
        when(reminderRepository.findByIdAndSourceForUpdate(notification.getId(), ReminderSource.EXTERNAL))
                .thenReturn(java.util.Optional.of(notification));
        when(responseRepository.findFirstByNotificationIdOrderByCreatedAtDescIdDesc(notification.getId()))
                .thenReturn(java.util.Optional.of(existing));

        var response = service.respond(notification.getId(), notification.getDeviceId(), notification.getRoleId(),
                NotificationResponseAction.ACKNOWLEDGE, null);

        assertThat(response.respondedAt()).isEqualTo(NOW.minusSeconds(1));
    }

    private ReminderEntity delivered(Set<NotificationResponseAction> actions) {
        UUID integrationId = UUID.randomUUID();
        ReminderEntity notification = new ReminderEntity(
                UUID.randomUUID(), UUID.randomUUID(), "完成", NOW.minusSeconds(60), "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.EXTERNAL, NOW.minusSeconds(120));
        notification.assignExternalMetadata(
                integrationId, UUID.randomUUID().toString(), "hash", NOW.plusSeconds(3600), actions, NOW.minusSeconds(120));
        notification.completeOccurrence(ReminderStatus.DELIVERED, null, NOW.minusSeconds(30));
        return notification;
    }
}
