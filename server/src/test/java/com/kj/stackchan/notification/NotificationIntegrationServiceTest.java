package com.kj.stackchan.notification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.reminder.ReminderEntity;
import com.kj.stackchan.reminder.ReminderRecurrence;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import com.kj.stackchan.role.CompanionRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationIntegrationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T00:00:00Z");

    @Mock private NotificationIntegrationRepository integrationRepository;
    @Mock private NotificationIntegrationTokenRepository tokenRepository;
    @Mock private DeviceRepository deviceRepository;
    @Mock private ReminderRepository reminderRepository;
    @Mock private NotificationRateLimiter rateLimiter;
    @Mock private CompanionRoleRepository roleRepository;

    @Test
    void issuesHighEntropyTokenStoresOnlyHashAndRevokesIt() {
        UUID deviceId = UUID.randomUUID();
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity("Codex", deviceId, true, NOW);
        AtomicReference<NotificationIntegrationTokenEntity> savedToken = new AtomicReference<>();
        when(integrationRepository.findById(integration.getId())).thenReturn(Optional.of(integration));
        when(tokenRepository.save(any(NotificationIntegrationTokenEntity.class))).thenAnswer(invocation -> {
            savedToken.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        NotificationIntegrationService service = service();

        var issued = service.issueToken(integration.getId(), NOW.plusSeconds(3600));
        assertThat(issued.token()).startsWith("scn_").hasSizeGreaterThan(40);
        assertThat(savedToken.get().getTokenHash()).hasSize(64).doesNotContain(issued.token());

        when(tokenRepository.findByTokenHash(NotificationIntegrationService.hash(issued.token())))
                .thenReturn(Optional.of(savedToken.get()));
        var principal = service.authenticate(issued.token());
        assertThat(principal.integrationId()).isEqualTo(integration.getId());
        assertThat(savedToken.get().getLastUsedAt()).isEqualTo(NOW);

        when(tokenRepository.findByIdAndIntegrationId(savedToken.get().getId(), integration.getId()))
                .thenReturn(Optional.of(savedToken.get()));
        service.revokeToken(integration.getId(), savedToken.get().getId());
        assertThat(savedToken.get().getRevokedAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> service.authenticate(issued.token()))
                .isInstanceOfSatisfying(NotificationApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("notification_authentication_failed"));
    }

    @Test
    void rejectsExpiredToken() {
        String rawToken = "scn_expired_token_with_enough_entropy_for_validation";
        NotificationIntegrationTokenEntity token = new NotificationIntegrationTokenEntity(
                UUID.randomUUID(), NotificationIntegrationService.hash(rawToken), NOW.minusSeconds(1), NOW.minusSeconds(3600)
        );
        when(tokenRepository.findByTokenHash(NotificationIntegrationService.hash(rawToken)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().authenticate(rawToken))
                .isInstanceOfSatisfying(NotificationApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("notification_authentication_failed"));
    }

    @Test
    void rejectsTokenWhenIntegrationIsDisabled() {
        String rawToken = "scn_disabled_integration_token_with_enough_entropy";
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity(
                "Codex", UUID.randomUUID(), false, NOW
        );
        NotificationIntegrationTokenEntity token = new NotificationIntegrationTokenEntity(
                integration.getId(), NotificationIntegrationService.hash(rawToken), NOW.plusSeconds(3600), NOW
        );
        when(tokenRepository.findByTokenHash(NotificationIntegrationService.hash(rawToken)))
                .thenReturn(Optional.of(token));
        when(integrationRepository.findById(integration.getId())).thenReturn(Optional.of(integration));

        assertThatThrownBy(() -> service().authenticate(rawToken))
                .isInstanceOfSatisfying(NotificationApiException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("notification_integration_disabled"));
    }

    @Test
    void deletesIntegrationWithTokensAndNotificationHistory() {
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity(
                "Codex", UUID.randomUUID(), true, NOW
        );
        ReminderEntity notification = externalReminder(integration);
        when(integrationRepository.findByIdForUpdate(integration.getId())).thenReturn(Optional.of(integration));
        when(reminderRepository.findAllByNotificationIntegrationIdForUpdate(integration.getId()))
                .thenReturn(List.of(notification));

        service().delete(integration.getId());

        verify(reminderRepository).deleteAll(List.of(notification));
        verify(reminderRepository).flush();
        verify(integrationRepository).delete(integration);
        verify(rateLimiter).forget(integration.getId());
    }

    @Test
    void refusesToDeleteIntegrationWhileNotificationIsDispatched() {
        NotificationIntegrationEntity integration = new NotificationIntegrationEntity(
                "Codex", UUID.randomUUID(), true, NOW
        );
        ReminderEntity notification = externalReminder(integration);
        notification.markDispatched("command-1", new byte[44], NOW);
        when(integrationRepository.findByIdForUpdate(integration.getId())).thenReturn(Optional.of(integration));
        when(reminderRepository.findAllByNotificationIntegrationIdForUpdate(integration.getId()))
                .thenReturn(List.of(notification));

        assertThatThrownBy(() -> service().delete(integration.getId()))
                .isInstanceOfSatisfying(NotificationApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("notification_delivery_in_progress");
                });
        verify(integrationRepository, never()).delete(any());
    }

    private ReminderEntity externalReminder(NotificationIntegrationEntity integration) {
        ReminderEntity reminder = new ReminderEntity(
                integration.getDeviceId(), "完成", NOW, "UTC",
                ReminderRecurrence.NONE, 1, null, ReminderSource.EXTERNAL, NOW
        );
        reminder.assignExternalMetadata(
                integration.getId(), "task-1", NotificationIntegrationService.hash("完成"),
                NOW.plusSeconds(3600), NOW
        );
        return reminder;
    }

    private NotificationIntegrationService service() {
        return new NotificationIntegrationService(
                integrationRepository, tokenRepository, deviceRepository, reminderRepository, rateLimiter,
                Clock.fixed(NOW, ZoneOffset.UTC), roleRepository
        );
    }
}
