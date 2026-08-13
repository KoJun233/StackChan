package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.kj.stackchan.role.CompanionRoleEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProactiveTopicCooldownService {

    static final Duration TOPIC_COOLDOWN = Duration.ofDays(7);

    private final ProactiveTopicCooldownRepository repository;
    private final Clock clock;

    public ProactiveTopicCooldownService(ProactiveTopicCooldownRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isEligible(UUID deviceId, String topicKey, Instant now) {
        return isEligible(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, topicKey, now);
    }
    @Transactional(readOnly = true)
    public boolean isEligible(UUID deviceId, UUID roleId, String topicKey, Instant now) {
        String normalized = normalize(topicKey);
        return repository.findById(new ProactiveTopicCooldownId(deviceId, roleId, normalized))
                .map(cooldown -> !cooldown.isUserMuted() && !cooldown.getCooldownUntil().isAfter(now))
                .orElse(true);
    }

    @Transactional
    public void recordMention(UUID deviceId, String topicKey, Instant now) {
        String normalized = normalize(topicKey);
        ProactiveTopicCooldownEntity entity = repository.findLocked(deviceId, normalized)
                .orElseGet(() -> new ProactiveTopicCooldownEntity(deviceId, normalized, now, now.plus(TOPIC_COOLDOWN)));
        entity.recordMention(now, now.plus(TOPIC_COOLDOWN));
        repository.save(entity);
    }
    @Transactional
    public void recordMention(UUID deviceId, UUID roleId, String topicKey, Instant now) {
        String normalized = normalize(topicKey);
        ProactiveTopicCooldownEntity entity = repository.findLocked(deviceId, roleId, normalized)
                .orElseGet(() -> new ProactiveTopicCooldownEntity(
                        deviceId, roleId, normalized, now, now.plus(TOPIC_COOLDOWN)
                ));
        entity.recordMention(now, now.plus(TOPIC_COOLDOWN));
        repository.save(entity);
    }

    @Transactional
    public boolean muteLastTopic(UUID deviceId) {
        return muteLastTopic(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID);
    }
    @Transactional
    public boolean muteLastTopic(UUID deviceId, UUID roleId) {
        ProactiveTopicCooldownEntity entity = repository
                .findFirstByDeviceIdAndRoleIdOrderByLastMentionedAtDescTopicKeyAsc(deviceId, roleId)
                .orElse(null);
        if (entity == null) return false;
        entity.mute(clock.instant());
        return true;
    }

    @Transactional
    public TopicCooldownSnapshot resume(UUID deviceId, String topicKey) {
        String normalized = normalize(topicKey);
        ProactiveTopicCooldownEntity entity = repository.findLocked(deviceId, normalized)
                .orElseThrow(ProactiveTopicCooldownNotFoundException::new);
        entity.resume(clock.instant());
        return snapshot(entity);
    }

    @Transactional(readOnly = true)
    public List<TopicCooldownSnapshot> list(UUID deviceId) {
        return repository.findAllByDeviceIdAndRoleIdOrderByLastMentionedAtDescTopicKeyAsc(
                        deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID).stream()
                .map(this::snapshot)
                .toList();
    }

    private TopicCooldownSnapshot snapshot(ProactiveTopicCooldownEntity entity) {
        return new TopicCooldownSnapshot(
                entity.getTopicKey(), entity.getLastMentionedAt(), entity.getCooldownUntil(), entity.isUserMuted()
        );
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 120) {
            throw new IllegalArgumentException("Proactive topic key is invalid");
        }
        return normalized;
    }

    public record TopicCooldownSnapshot(
            String topicKey,
            Instant lastMentionedAt,
            Instant cooldownUntil,
            boolean userMuted
    ) {
    }
}
