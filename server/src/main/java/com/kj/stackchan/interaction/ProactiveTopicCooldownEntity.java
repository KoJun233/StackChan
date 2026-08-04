package com.kj.stackchan.interaction;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@IdClass(ProactiveTopicCooldownId.class)
@Table(name = "proactive_topic_cooldowns")
public class ProactiveTopicCooldownEntity {

    @Id
    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Id
    @Column(name = "topic_key", nullable = false, length = 120)
    private String topicKey;

    @Column(name = "last_mentioned_at", nullable = false)
    private Instant lastMentionedAt;

    @Column(name = "cooldown_until", nullable = false)
    private Instant cooldownUntil;

    @Column(name = "user_muted", nullable = false)
    private boolean userMuted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProactiveTopicCooldownEntity() {
    }

    public ProactiveTopicCooldownEntity(UUID deviceId, String topicKey, Instant mentionedAt, Instant cooldownUntil) {
        this.deviceId = deviceId;
        this.topicKey = topicKey;
        this.lastMentionedAt = mentionedAt;
        this.cooldownUntil = cooldownUntil;
        this.updatedAt = mentionedAt;
    }

    public void recordMention(Instant mentionedAt, Instant cooldownUntil) {
        this.lastMentionedAt = mentionedAt;
        this.cooldownUntil = cooldownUntil;
        this.updatedAt = mentionedAt;
    }

    public void mute(Instant now) {
        this.userMuted = true;
        this.updatedAt = now;
    }

    public void resume(Instant now) {
        this.userMuted = false;
        this.cooldownUntil = now;
        this.updatedAt = now;
    }

    public UUID getDeviceId() { return deviceId; }
    public String getTopicKey() { return topicKey; }
    public Instant getLastMentionedAt() { return lastMentionedAt; }
    public Instant getCooldownUntil() { return cooldownUntil; }
    public boolean isUserMuted() { return userMuted; }
    public Instant getUpdatedAt() { return updatedAt; }
}
