package com.kj.stackchan.conversation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kj.stackchan.role.CompanionRoleEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BoundedConversationHistoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired ConversationRepository conversations;
    @Autowired ConversationMessageRepository messages;
    @Autowired JdbcTemplate jdbc;

    @Test
    void voiceTopicBoundaryPersistsIdempotentlyWithoutDeletingMessagesOrCrossingConversations() {
        Instant now = Instant.parse("2026-09-13T01:00:00Z");
        var first = conversations.saveAndFlush(new ConversationEntity("first", CompanionRoleEntity.DEFAULT_ROLE_ID, now));
        var second = conversations.saveAndFlush(new ConversationEntity("second", CompanionRoleEntity.DEFAULT_ROLE_ID, now));
        var user = messages.saveAndFlush(ConversationMessageEntity.user(first.getId(), UUID.randomUUID(), "换个话题", now));
        var service = new ConversationService(conversations, messages, Clock.systemUTC());
        assertThat(service.resetVoiceTopic(first.getId(), user.getId())).isEqualTo(now);
        assertThat(service.resetVoiceTopic(first.getId(), user.getId())).isEqualTo(now);
        conversations.flush();
        assertThat(jdbc.queryForObject("select voice_topic_reset_at from conversations where id = ?",
                java.sql.Timestamp.class, first.getId()).toInstant()).isEqualTo(now);
        assertThat(service.voiceTopicBoundary(second.getId())).isNull();
        assertThat(service.getMessages(first.getId())).hasSize(1);
        assertThatThrownBy(() -> service.resetVoiceTopic(second.getId(), user.getId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesLatestTwentyOrderingAndFiltersBeforeLimiting() {
        Instant now = Instant.parse("2026-09-12T00:00:00Z");
        var conversation = conversations.saveAndFlush(new ConversationEntity(
                "history", CompanionRoleEntity.DEFAULT_ROLE_ID, now));
        var other = conversations.saveAndFlush(new ConversationEntity(
                "other", CompanionRoleEntity.DEFAULT_ROLE_ID, now));
        for (int i = 0; i < 30; i++) {
            // Equal timestamps exercise PostgreSQL UUID tie ordering as well as the row limit.
            var user = messages.saveAndFlush(ConversationMessageEntity.user(
                    conversation.getId(), UUID.randomUUID(), "user-" + i, now.plusSeconds(i / 3)));
            var assistant = ConversationMessageEntity.streamingAssistant(
                    conversation.getId(), user.getId(), now.plusSeconds(i / 3));
            assistant.complete("assistant-" + i, now.plusSeconds(i / 3 + 1));
            messages.saveAndFlush(assistant);
        }
        for (int i = 0; i < 25; i++) {
            var failed = ConversationMessageEntity.streamingAssistant(conversation.getId(), now.plusSeconds(100 + i));
            failed.fail("failed", "excluded", now.plusSeconds(100 + i));
            messages.saveAndFlush(failed);
        }
        messages.saveAndFlush(ConversationMessageEntity.user(other.getId(), UUID.randomUUID(), "other", now.plusSeconds(200)));
        jdbc.update("""
                insert into conversation_messages(id, conversation_id, role, content, generation_status, created_at)
                values (?, ?, 'SYSTEM', 'excluded system', 'COMPLETED', ?)
                """, UUID.randomUUID(), conversation.getId(), java.sql.Timestamp.from(now.plusSeconds(300)));
        var expected = messages.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId()).stream()
                .filter(m -> m.getGenerationStatus() == GenerationStatus.COMPLETED)
                .filter(m -> m.getRole() == MessageRole.USER || m.getRole() == MessageRole.ASSISTANT)
                .toList();
        var result = service().loadHistory(conversation.getId());
        assertThat(result).extracting(ConversationMessageSnapshot::id)
                .containsExactlyElementsOf(expected.subList(expected.size() - 20, expected.size()).stream()
                        .map(ConversationMessageEntity::getId).toList());
        assertThat(result.stream().filter(m -> m.role() == MessageRole.ASSISTANT).toList())
                .allSatisfy(m -> assertThat(m.inReplyToMessageId()).isNotNull());
    }

    @Test
    void distinguishesEmptyHistoryFromMissingConversation() {
        var conversation = conversations.saveAndFlush(new ConversationEntity(
                "empty", CompanionRoleEntity.DEFAULT_ROLE_ID, Instant.now()));
        assertThat(service().loadHistory(conversation.getId())).isEmpty();
        assertThatThrownBy(() -> service().loadHistory(UUID.randomUUID()))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    private ConversationService service() {
        return new ConversationService(conversations, messages, Clock.systemUTC());
    }
}
