package com.kj.stackchan.persona;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companion_persona_settings")
public class PersonaSettingsEntity {

    public static final short CURRENT_SETTINGS_ID = 1;

    @Id
    private Short id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PersonaTone tone;

    @Enumerated(EnumType.STRING)
    @Column(name = "reply_length", nullable = false, length = 24)
    private PersonaReplyLength replyLength;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PersonaProactivity proactivity;

    @Column(name = "topic_boundaries", nullable = false, length = 2000)
    private String topicBoundaries;

    @Column(nullable = false, length = 2000)
    private String taboos;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonaSettingsEntity() {
    }

    public PersonaSettingsEntity(
            String displayName,
            PersonaTone tone,
            PersonaReplyLength replyLength,
            PersonaProactivity proactivity,
            String topicBoundaries,
            String taboos,
            Instant updatedAt
    ) {
        this.id = CURRENT_SETTINGS_ID;
        update(displayName, tone, replyLength, proactivity, topicBoundaries, taboos, updatedAt);
    }

    public void update(
            String displayName,
            PersonaTone tone,
            PersonaReplyLength replyLength,
            PersonaProactivity proactivity,
            String topicBoundaries,
            String taboos,
            Instant updatedAt
    ) {
        this.displayName = displayName;
        this.tone = tone;
        this.replyLength = replyLength;
        this.proactivity = proactivity;
        this.topicBoundaries = topicBoundaries;
        this.taboos = taboos;
        this.updatedAt = updatedAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public PersonaTone getTone() {
        return tone;
    }

    public PersonaReplyLength getReplyLength() {
        return replyLength;
    }

    public PersonaProactivity getProactivity() {
        return proactivity;
    }

    public String getTopicBoundaries() {
        return topicBoundaries;
    }

    public String getTaboos() {
        return taboos;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
