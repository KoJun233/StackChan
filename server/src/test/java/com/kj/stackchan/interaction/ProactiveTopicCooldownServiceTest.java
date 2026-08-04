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
        when(repository.findLocked(deviceId, "coffee")).thenReturn(Optional.of(entity));

        assertThat(service(repository).isEligible(deviceId, "coffee", NOW)).isFalse();
        service(repository).resume(deviceId, "coffee");
        assertThat(entity.isUserMuted()).isFalse();
        assertThat(entity.getCooldownUntil()).isEqualTo(NOW);
    }

    private ProactiveTopicCooldownService service(ProactiveTopicCooldownRepository repository) {
        return new ProactiveTopicCooldownService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
