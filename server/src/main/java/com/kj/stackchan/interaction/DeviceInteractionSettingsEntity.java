package com.kj.stackchan.interaction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_interaction_settings")
public class DeviceInteractionSettingsEntity {

    @Id
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "volume_percent", nullable = false)
    private int volumePercent;

    @Column(name = "night_mode", nullable = false)
    private boolean nightMode;

    @Column(name = "continuous_conversation_enabled", nullable = false)
    private boolean continuousConversationEnabled;

    @Column(name = "follow_up_window_seconds", nullable = false)
    private int followUpWindowSeconds;

    @Column(name = "dnd_enabled", nullable = false)
    private boolean dndEnabled;

    @Column(name = "dnd_start", nullable = false)
    private LocalTime dndStart;

    @Column(name = "dnd_end", nullable = false)
    private LocalTime dndEnd;

    @Column(name = "temporary_dnd_until")
    private Instant temporaryDndUntil;

    @Column(name = "zone_id", nullable = false, length = 80)
    private String zoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "missed_reminder_policy", nullable = false, length = 16)
    private MissedReminderPolicy missedReminderPolicy;

    @Column(name = "missed_snooze_minutes", nullable = false)
    private int missedSnoozeMinutes;

    @Column(name = "proactive_enabled", nullable = false)
    private boolean proactiveEnabled;

    @Column(name = "proactive_start", nullable = false)
    private LocalTime proactiveStart;

    @Column(name = "proactive_end", nullable = false)
    private LocalTime proactiveEnd;

    @Column(name = "proactive_min_interval_minutes", nullable = false)
    private int proactiveMinIntervalMinutes;

    @Column(name = "proactive_daily_limit", nullable = false)
    private int proactiveDailyLimit;

    @Column(name = "proactive_content", nullable = false, length = 500)
    private String proactiveContent;

    @Column(name = "proactive_last_at")
    private Instant proactiveLastAt;

    @Column(name = "proactive_counter_date")
    private LocalDate proactiveCounterDate;

    @Column(name = "proactive_counter", nullable = false)
    private int proactiveCounter;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceInteractionSettingsEntity() {
    }

    public DeviceInteractionSettingsEntity(UUID deviceId, Instant now) {
        this.deviceId = deviceId;
        this.volumePercent = 50;
        this.nightMode = false;
        this.continuousConversationEnabled = false;
        this.followUpWindowSeconds = 8;
        this.dndEnabled = false;
        this.dndStart = LocalTime.of(22, 0);
        this.dndEnd = LocalTime.of(7, 0);
        this.temporaryDndUntil = null;
        this.zoneId = "Asia/Shanghai";
        this.missedReminderPolicy = MissedReminderPolicy.PLAY_NOW;
        this.missedSnoozeMinutes = 10;
        this.proactiveEnabled = false;
        this.proactiveStart = LocalTime.of(9, 0);
        this.proactiveEnd = LocalTime.of(21, 0);
        this.proactiveMinIntervalMinutes = 240;
        this.proactiveDailyLimit = 2;
        this.proactiveContent = "你好呀，记得休息一下，也可以和我聊聊天。";
        this.proactiveCounter = 0;
        this.updatedAt = now;
    }

    public void update(
            int volumePercent,
            boolean nightMode,
            boolean continuousConversationEnabled,
            int followUpWindowSeconds,
            boolean dndEnabled,
            LocalTime dndStart,
            LocalTime dndEnd,
            String zoneId,
            MissedReminderPolicy missedReminderPolicy,
            int missedSnoozeMinutes,
            boolean proactiveEnabled,
            LocalTime proactiveStart,
            LocalTime proactiveEnd,
            int proactiveMinIntervalMinutes,
            int proactiveDailyLimit,
            String proactiveContent,
            Instant now
    ) {
        this.volumePercent = volumePercent;
        this.nightMode = nightMode;
        this.continuousConversationEnabled = continuousConversationEnabled;
        this.followUpWindowSeconds = followUpWindowSeconds;
        this.dndEnabled = dndEnabled;
        this.dndStart = dndStart;
        this.dndEnd = dndEnd;
        this.zoneId = zoneId;
        this.missedReminderPolicy = missedReminderPolicy;
        this.missedSnoozeMinutes = missedSnoozeMinutes;
        this.proactiveEnabled = proactiveEnabled;
        this.proactiveStart = proactiveStart;
        this.proactiveEnd = proactiveEnd;
        this.proactiveMinIntervalMinutes = proactiveMinIntervalMinutes;
        this.proactiveDailyLimit = proactiveDailyLimit;
        this.proactiveContent = proactiveContent;
        this.updatedAt = now;
    }

    public void recordProactive(LocalDate localDate, Instant now) {
        if (!localDate.equals(proactiveCounterDate)) {
            proactiveCounterDate = localDate;
            proactiveCounter = 0;
        }
        proactiveCounter++;
        proactiveLastAt = now;
        updatedAt = now;
    }

    public void setVolume(int volumePercent, Instant now) {
        this.volumePercent = volumePercent;
        this.updatedAt = now;
    }

    public void setTemporaryDndUntil(Instant until, Instant now) {
        this.temporaryDndUntil = until;
        this.updatedAt = now;
    }

    public UUID getDeviceId() { return deviceId; }
    public int getVolumePercent() { return volumePercent; }
    public boolean isNightMode() { return nightMode; }
    public boolean isContinuousConversationEnabled() { return continuousConversationEnabled; }
    public int getFollowUpWindowSeconds() { return followUpWindowSeconds; }
    public boolean isDndEnabled() { return dndEnabled; }
    public LocalTime getDndStart() { return dndStart; }
    public LocalTime getDndEnd() { return dndEnd; }
    public Instant getTemporaryDndUntil() { return temporaryDndUntil; }
    public String getZoneId() { return zoneId; }
    public MissedReminderPolicy getMissedReminderPolicy() { return missedReminderPolicy; }
    public int getMissedSnoozeMinutes() { return missedSnoozeMinutes; }
    public boolean isProactiveEnabled() { return proactiveEnabled; }
    public LocalTime getProactiveStart() { return proactiveStart; }
    public LocalTime getProactiveEnd() { return proactiveEnd; }
    public int getProactiveMinIntervalMinutes() { return proactiveMinIntervalMinutes; }
    public int getProactiveDailyLimit() { return proactiveDailyLimit; }
    public String getProactiveContent() { return proactiveContent; }
    public Instant getProactiveLastAt() { return proactiveLastAt; }
    public LocalDate getProactiveCounterDate() { return proactiveCounterDate; }
    public int getProactiveCounter() { return proactiveCounter; }
    public Instant getUpdatedAt() { return updatedAt; }
}
