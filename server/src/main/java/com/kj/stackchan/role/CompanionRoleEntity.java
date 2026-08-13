package com.kj.stackchan.role;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companion_roles")
public class CompanionRoleEntity {
    public static final UUID DEFAULT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id private UUID id;
    @Column(nullable = false, length = 80) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PersonaTone tone;
    @Enumerated(EnumType.STRING) @Column(name = "reply_length", nullable = false, length = 24)
    private PersonaReplyLength replyLength;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private PersonaProactivity proactivity;
    @Column(name = "background_instructions", nullable = false, length = 4000) private String backgroundInstructions;
    @Column(name = "topic_boundaries", nullable = false, length = 2000) private String topicBoundaries;
    @Column(nullable = false, length = 2000) private String taboos;
    @Column(name = "is_default", nullable = false) private boolean defaultRole;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected CompanionRoleEntity() {}

    public CompanionRoleEntity(String name, PersonaTone tone, PersonaReplyLength replyLength,
                               PersonaProactivity proactivity, String backgroundInstructions,
                               String topicBoundaries, String taboos, Instant now) {
        this.id = UUID.randomUUID();
        this.defaultRole = false;
        this.createdAt = now;
        update(name, tone, replyLength, proactivity, backgroundInstructions, topicBoundaries, taboos, now);
    }

    public void update(String name, PersonaTone tone, PersonaReplyLength replyLength,
                       PersonaProactivity proactivity, String backgroundInstructions,
                       String topicBoundaries, String taboos, Instant now) {
        this.name = name;
        this.tone = tone;
        this.replyLength = replyLength;
        this.proactivity = proactivity;
        this.backgroundInstructions = backgroundInstructions;
        this.topicBoundaries = topicBoundaries;
        this.taboos = taboos;
        this.updatedAt = now;
    }

    public void archive(Instant now) { this.archivedAt = now; this.updatedAt = now; }
    public void restore(Instant now) { this.archivedAt = null; this.updatedAt = now; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public PersonaTone getTone() { return tone; }
    public PersonaReplyLength getReplyLength() { return replyLength; }
    public PersonaProactivity getProactivity() { return proactivity; }
    public String getBackgroundInstructions() { return backgroundInstructions; }
    public String getTopicBoundaries() { return topicBoundaries; }
    public String getTaboos() { return taboos; }
    public boolean isDefaultRole() { return defaultRole; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
