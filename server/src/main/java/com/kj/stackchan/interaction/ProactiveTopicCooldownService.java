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
    private final com.kj.stackchan.reminder.ReminderRepository reminders;

    public ProactiveTopicCooldownService(ProactiveTopicCooldownRepository repository, Clock clock) {
        this(repository, clock, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProactiveTopicCooldownService(ProactiveTopicCooldownRepository repository, Clock clock,
            com.kj.stackchan.reminder.ReminderRepository reminders) {
        this.repository = repository;
        this.clock = clock;
        this.reminders = reminders;
    }

    @Transactional
    public boolean muteLastDeliveredTopic(UUID deviceId, UUID roleId, Instant topicBoundary) {
        if (reminders == null || deviceId == null || roleId == null) return false;
        Instant now = clock.instant();
        var delivered = reminders.findRecentCompletedDeliveries(deviceId, roleId, now.minus(Duration.ofMinutes(30)),
                now, org.springframework.data.domain.PageRequest.of(0, 2));
        if (delivered.isEmpty()) return false;
        var latest = delivered.getFirst();
        if (latest.getSource() != com.kj.stackchan.reminder.ReminderSource.PROACTIVE) return false;
        if (delivered.size() > 1 && latest.getLastCompletedAt().equals(delivered.get(1).getLastCompletedAt())) return false;
        if (topicBoundary != null && !latest.getLastCompletedAt().isAfter(topicBoundary)) return false;
        if (latest.getProactiveTopicKey() == null || latest.getProactiveTopicKey().isBlank()
                || latest.getProactiveTopicKey().startsWith("workday:")) return false;
        var topic = repository.findLocked(deviceId, roleId, normalize(latest.getProactiveTopicKey())).orElse(null);
        if (topic == null) return false;
        topic.mute(now);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isUserMuted(UUID deviceId, UUID roleId, String topicKey) {
        return repository.findById(new ProactiveTopicCooldownId(deviceId, roleId, normalize(topicKey)))
                .map(ProactiveTopicCooldownEntity::isUserMuted).orElse(false);
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
        return resume(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, topicKey);
    }

    @Transactional
    public TopicCooldownSnapshot resume(UUID deviceId, UUID roleId, String topicKey) {
        String normalized = normalize(topicKey);
        ProactiveTopicCooldownEntity entity = repository.findLocked(deviceId, roleId, normalized)
                .orElseThrow(ProactiveTopicCooldownNotFoundException::new);
        entity.resume(clock.instant());
        return snapshot(entity);
    }

    @Transactional(readOnly = true)
    public List<TopicCooldownSnapshot> list(UUID deviceId) {
        return list(deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID);
    }

    @Transactional(readOnly = true)
    public List<TopicCooldownSnapshot> list(UUID deviceId, UUID roleId) {
        return repository.findAllByDeviceIdAndRoleIdOrderByLastMentionedAtDescTopicKeyAsc(
                        deviceId, roleId).stream()
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
