package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProactiveTopicCooldownServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void onlyMutesTheLatestDeliveredTopicAfterTheConversationBoundary() {
        var repository = mock(ProactiveTopicCooldownRepository.class);
        var reminders = mock(com.kj.stackchan.reminder.ReminderRepository.class);
        UUID device = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var delivered = mock(com.kj.stackchan.reminder.ReminderEntity.class);
        when(delivered.getSource()).thenReturn(com.kj.stackchan.reminder.ReminderSource.PROACTIVE);
        when(reminders.findRecentCompletedDeliveries(device, role, NOW.minusSeconds(1800), NOW,
                org.springframework.data.domain.PageRequest.of(0, 2))).thenReturn(java.util.List.of(delivered));
        when(delivered.getLastCompletedAt()).thenReturn(NOW.minusSeconds(10));
        var service = new ProactiveTopicCooldownService(repository, Clock.fixed(NOW, ZoneOffset.UTC), reminders);
        assertThat(service.muteLastDeliveredTopic(device, role, NOW.minusSeconds(10))).isFalse();
        assertThat(service.muteLastDeliveredTopic(device, role, NOW)).isFalse();
        assertThat(service.muteLastDeliveredTopic(device, role, null)).isFalse();
        org.mockito.Mockito.verifyNoInteractions(repository);

        when(delivered.getProactiveTopicKey()).thenReturn("coffee");
        var topic = new ProactiveTopicCooldownEntity(device, role, "coffee", NOW.minusSeconds(20), NOW);
        when(repository.findLocked(device, role, "coffee")).thenReturn(Optional.of(topic));
        assertThat(service.muteLastDeliveredTopic(device, role, NOW.minusSeconds(30))).isTrue();
        assertThat(topic.isUserMuted()).isTrue();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .findFirstByDeviceIdAndRoleIdOrderByLastMentionedAtDescTopicKeyAsc(device, role);
    }

    @Test
    void recordsASevenDayCooldownForANewTopic() {
        ProactiveTopicCooldownRepository repository = mock(ProactiveTopicCooldownRepository.class);
        UUID deviceId = UUID.randomUUID();
        when(repository.findLocked(deviceId, "coffee")).thenReturn(Optional.empty());

        service(repository).recordMention(deviceId, " Coffee ", NOW);

        ArgumentCaptor<ProactiveTopicCooldownEntity> captor =
                ArgumentCaptor.forClass(ProactiveTopicCooldownEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTopicKey()).isEqualTo("coffee");
        assertThat(captor.getValue().getCooldownUntil()).isEqualTo(NOW.plus(ProactiveTopicCooldownService.TOPIC_COOLDOWN));
    }

    @Test
    void userMuteSurvivesCooldownExpiryUntilExplicitResume() {
        ProactiveTopicCooldownRepository repository = mock(ProactiveTopicCooldownRepository.class);
        UUID deviceId = UUID.randomUUID();
        var entity = new ProactiveTopicCooldownEntity(deviceId, "coffee", NOW.minusSeconds(10), NOW.minusSeconds(1));
        entity.mute(NOW.minusSeconds(5));
        when(repository.findById(new ProactiveTopicCooldownId(deviceId, "coffee"))).thenReturn(Optional.of(entity));
        when(repository.findLocked(deviceId, com.kj.stackchan.role.CompanionRoleEntity.DEFAULT_ROLE_ID, "coffee"))
                .thenReturn(Optional.of(entity));

        assertThat(service(repository).isEligible(deviceId, "coffee", NOW)).isFalse();
        service(repository).resume(deviceId, "coffee");
        assertThat(entity.isUserMuted()).isFalse();
        assertThat(entity.getCooldownUntil()).isEqualTo(NOW);
    }

    private ProactiveTopicCooldownService service(ProactiveTopicCooldownRepository repository) {
        return new ProactiveTopicCooldownService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
