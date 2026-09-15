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
                .findRecentCompletedDeliveries(notification.getDeviceId(), notification.getRoleId(), NOW.minusSeconds(1800),
                        NOW, org.springframework.data.domain.PageRequest.of(0, 2)))
                .thenReturn(List.of(notification));

        assertThat(service.latestActionable(
                notification.getDeviceId(), notification.getRoleId(), NotificationResponseAction.ACKNOWLEDGE))
                .isEqualTo(notification.getId());
        assertThat(service.latestActionable(
                notification.getDeviceId(), notification.getRoleId(), NotificationResponseAction.COMPLETE))
                .isNull();
    }

    @Test
    void newerOrdinaryReminderUnsupportedActionAndTiedCompletionNeverFallBack() {
        var notification = delivered(Set.of(NotificationResponseAction.ACKNOWLEDGE));
        UUID device = notification.getDeviceId();
        UUID role = notification.getRoleId();
        var ordinary = new ReminderEntity(role, device, "休息一下", NOW.minusSeconds(15), "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.USER, NOW.minusSeconds(60));
        ordinary.completeOccurrence(ReminderStatus.DELIVERED, null, NOW.minusSeconds(10));
        when(reminderRepository.findRecentCompletedDeliveries(device, role, NOW.minusSeconds(1800), NOW,
                org.springframework.data.domain.PageRequest.of(0, 2))).thenReturn(List.of(ordinary, notification));
        assertThat(service.latestActionable(device, role, NotificationResponseAction.ACKNOWLEDGE)).isNull();
        ordinary.completeOccurrence(ReminderStatus.DELIVERED, null, notification.getLastCompletedAt());
        when(reminderRepository.findRecentCompletedDeliveries(device, role, NOW.minusSeconds(1800), NOW,
                org.springframework.data.domain.PageRequest.of(0, 2))).thenReturn(List.of(notification, ordinary));
        assertThat(service.latestActionable(device, role, NotificationResponseAction.ACKNOWLEDGE)).isNull();
        Instant boundary = NOW.minusSeconds(5);
        when(reminderRepository.findRecentCompletedDeliveries(device, role, boundary, NOW,
                org.springframework.data.domain.PageRequest.of(0, 2))).thenReturn(List.of());
        assertThat(service.latestActionable(device, role, NotificationResponseAction.ACKNOWLEDGE, boundary)).isNull();
    }

    @Test
    void voiceDescriptionRequiresSamePartnerDeliveredContentAndPermittedAction() {
        var notification = delivered(Set.of(NotificationResponseAction.ACKNOWLEDGE));
        when(reminderRepository.findByIdAndDeviceId(notification.getId(), notification.getDeviceId()))
                .thenReturn(java.util.Optional.of(notification));
        assertThat(service.descriptionForVoice(notification.getId(), notification.getDeviceId(), notification.getRoleId(),
                NotificationResponseAction.ACKNOWLEDGE)).isEqualTo("完成");
        assertThatThrownBy(() -> service.descriptionForVoice(notification.getId(), notification.getDeviceId(), UUID.randomUUID(),
                NotificationResponseAction.ACKNOWLEDGE)).isInstanceOf(NotificationApiException.class);
        assertThatThrownBy(() -> service.descriptionForVoice(notification.getId(), notification.getDeviceId(), notification.getRoleId(),
                NotificationResponseAction.COMPLETE)).isInstanceOf(NotificationApiException.class);
        notification.completeOccurrence(ReminderStatus.DELIVERED, null, NOW.minusSeconds(86_401));
        assertThatThrownBy(() -> service.descriptionForVoice(notification.getId(), notification.getDeviceId(), notification.getRoleId(),
                NotificationResponseAction.ACKNOWLEDGE)).isInstanceOf(NotificationApiException.class);
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
