package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExternalNotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Mock private NotificationIntegrationRepository integrationRepository;
    @Mock private ReminderRepository reminderRepository;
    @Mock private NotificationRateLimiter rateLimiter;

    private ExternalNotificationService service;
    private NotificationIntegrationEntity integration;
    private NotificationIntegrationPrincipal principal;

    @BeforeEach
    void setUp() {
        service = new ExternalNotificationService(
                integrationRepository, reminderRepository, rateLimiter, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        integration = new NotificationIntegrationEntity("Codex", UUID.randomUUID(), true, NOW);
        principal = new NotificationIntegrationPrincipal(
                integration.getId(), integration.getDeviceId(), integration.getName()
        );
        lenient().when(integrationRepository.findByIdForUpdate(integration.getId())).thenReturn(Optional.of(integration));
        lenient().when(rateLimiter.tryAcquire(integration.getId())).thenReturn(true);
        lenient().when(reminderRepository.save(any(ReminderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsDeterministicExternalReminderWithDefaultExpiry() {
        var result = service.create(principal, "task-1", " 任务已完成。 ", null);

        assertThat(result.replayed()).isFalse();
        assertThat(result.notification().status()).isEqualTo(ReminderStatus.PENDING);
        assertThat(result.notification().expiresAt()).isEqualTo(NOW.plusSeconds(86_400));
        assertThat(result.notification().attemptCount()).isZero();
    }

    @Test
    void createsInteractiveNotificationAndTreatsChangedActionsAsIdempotencyConflict() {
        var result = service.create(principal, "interactive-1", "请处理", null,
                java.util.Set.of(NotificationResponseAction.ACKNOWLEDGE, NotificationResponseAction.COMPLETE));
        assertThat(result.notification().responseActions())
                .containsExactlyInAnyOrder(NotificationResponseAction.ACKNOWLEDGE, NotificationResponseAction.COMPLETE);

        ReminderEntity existing = externalReminder("interactive-2", "请处理");
        when(reminderRepository.findByNotificationIntegrationIdAndIdempotencyKey(
                integration.getId(), "interactive-2")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(principal, "interactive-2", "请处理", null,
                java.util.Set.of(NotificationResponseAction.COMPLETE)))
                .isInstanceOfSatisfying(NotificationApiException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("notification_idempotency_conflict"));
    }

    @Test
    void replaysSameIdempotencyKeyAndRejectsDifferentContent() {
        ReminderEntity existing = externalReminder("task-1", "原正文");
        when(reminderRepository.findByNotificationIntegrationIdAndIdempotencyKey(
                integration.getId(), "task-1"
        )).thenReturn(Optional.of(existing));

        assertThat(service.create(principal, "task-1", "原正文", null).replayed()).isTrue();
        assertThatThrownBy(() -> service.create(principal, "task-1", "不同正文", null))
                .isInstanceOfSatisfying(NotificationApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("notification_idempotency_conflict");
                });
    }

    @Test
    void rejectsCreationWhenIncompleteQueueReachedLimit() {
        when(reminderRepository.countByNotificationIntegrationIdAndStatusIn(
                integration.getId(), EnumSet.of(ReminderStatus.PENDING, ReminderStatus.DISPATCHED)
        )).thenReturn(100L);

        assertThatThrownBy(() -> service.create(principal, "task-2", "正文", null))
                .isInstanceOfSatisfying(NotificationApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("notification_queue_full"));
    }

    @Test
    void queryIsScopedToAuthenticatedIntegration() {
        UUID notificationId = UUID.randomUUID();
        when(reminderRepository.findByIdAndNotificationIntegrationId(notificationId, integration.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(principal, notificationId))
                .isInstanceOfSatisfying(NotificationApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("notification_not_found"));
    }

    @Test
    void administratorDeletesPendingOrCompletedNotification() {
        ReminderEntity notification = externalReminder("task-delete", "待删除");
        when(reminderRepository.findByIdAndSourceForUpdate(notification.getId(), ReminderSource.EXTERNAL))
                .thenReturn(Optional.of(notification));

        service.deleteAdmin(notification.getId());

        verify(reminderRepository).delete(notification);
    }

    @Test
    void administratorCannotDeleteDispatchedNotification() {
        ReminderEntity notification = externalReminder("task-playing", "正在播报");
        notification.markDispatched("command-1", new byte[44], NOW);
        when(reminderRepository.findByIdAndSourceForUpdate(notification.getId(), ReminderSource.EXTERNAL))
                .thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> service.deleteAdmin(notification.getId()))
                .isInstanceOfSatisfying(NotificationApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("notification_delivery_in_progress");
                });
        verify(reminderRepository, never()).delete(any(ReminderEntity.class));
    }

    private ReminderEntity externalReminder(String key, String content) {
        ReminderEntity reminder = new ReminderEntity(
                integration.getDeviceId(), content, NOW, "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.EXTERNAL, NOW
        );
        reminder.assignExternalMetadata(
                integration.getId(), key, NotificationIntegrationService.hash(content), NOW.plusSeconds(3600), NOW
        );
        return reminder;
    }
}
