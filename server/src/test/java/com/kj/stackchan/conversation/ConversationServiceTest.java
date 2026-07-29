package com.kj.stackchan.conversation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Import(ConversationServiceTest.FixedClockConfiguration.class)
class ConversationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private PersonalDataService personalDataService;

    @Autowired
    private ConversationMessageRepository conversationMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AdjustableClock clock;

    @BeforeEach
    void clearConversationData() {
        conversationMessageRepository.deleteAllInBatch();
        conversationRepository.deleteAllInBatch();
        clock.set(FIXED_NOW);
    }

    @Test
    void persistsOneUserMessageAndOneStreamingAssistantMessageOnlyOncePerClientMessage() {
        ConversationSnapshot conversation = conversationService.createConversation();
        UUID clientMessageId = UUID.fromString("e4b0d6b1-8491-4c8d-9da8-c7c5ee7fc4b9");

        GenerationStart firstStart = conversationService.startGeneration(
                conversation.id(), clientMessageId, "今天有点累"
        );
        List<ConversationMessageEntity> messages = conversationMessageRepository
                .findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.id());

        assertThat(firstStart.duplicate()).isFalse();
        assertThat(messages).extracting(ConversationMessageEntity::getRole)
                .containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
        assertThat(messages).extracting(ConversationMessageEntity::getGenerationStatus)
                .containsExactly(GenerationStatus.COMPLETED, GenerationStatus.STREAMING);
        assertThat(messages.getFirst().getContent()).isEqualTo("今天有点累");
        assertThat(messages.get(1).getContent()).isEmpty();

        GenerationStart duplicateStart = conversationService.startGeneration(
                conversation.id(), clientMessageId, "今天有点累"
        );

        assertThat(duplicateStart.duplicate()).isTrue();
        assertThat(duplicateStart.userMessageId()).isEqualTo(firstStart.userMessageId());
        assertThat(duplicateStart.assistantMessageId()).isEqualTo(firstStart.assistantMessageId());
        assertThat(conversationMessageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.id()))
                .hasSize(2);
    }

    @Test
    void loadsTheLatestTwentyCompletedMessagesAndExcludesFailedMessages() {
        ConversationSnapshot conversation = conversationService.createConversation();

        for (int index = 0; index < 15; index++) {
            clock.set(FIXED_NOW.plusSeconds(index));
            GenerationStart start = conversationService.startGeneration(
                    conversation.id(), UUID.randomUUID(), "prompt-" + index
            );
            conversationService.completeGeneration(start.assistantMessageId(), "reply-" + index);
        }

        clock.set(FIXED_NOW.plusSeconds(16));
        ConversationMessageEntity failedMessage = conversationMessageRepository.save(
                ConversationMessageEntity.streamingAssistant(conversation.id(), clock.instant())
        );
        conversationService.failGeneration(failedMessage.getId(), "provider_unavailable");

        List<ConversationMessageSnapshot> history = conversationService.loadHistory(conversation.id());

        assertThat(history).hasSize(20);
        assertThat(history).extracting(ConversationMessageSnapshot::content)
                .containsExactly(
                        "prompt-5", "reply-5", "prompt-6", "reply-6", "prompt-7", "reply-7",
                        "prompt-8", "reply-8", "prompt-9", "reply-9", "prompt-10", "reply-10",
                        "prompt-11", "reply-11", "prompt-12", "reply-12", "prompt-13", "reply-13",
                        "prompt-14", "reply-14"
                );
        assertThat(history).extracting(ConversationMessageSnapshot::generationStatus)
                .containsOnly(GenerationStatus.COMPLETED);
    }

    @Test
    void persistsPartialContentForFailedAndInterruptedAssistantGenerations() {
        ConversationSnapshot conversation = conversationService.createConversation();
        GenerationStart failedStart = conversationService.startGeneration(
                conversation.id(), UUID.randomUUID(), "失败之前的提问"
        );

        conversationService.failGeneration(failedStart.assistantMessageId(), "provider_unavailable", "部分回复");

        ConversationMessageEntity failed = conversationMessageRepository.findById(failedStart.assistantMessageId())
                .orElseThrow();
        assertThat(failed.getGenerationStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("provider_unavailable");
        assertThat(failed.getContent()).isEqualTo("部分回复");

        GenerationStart interruptedStart = conversationService.startGeneration(
                conversation.id(), UUID.randomUUID(), "取消之前的提问"
        );
        conversationService.interruptGeneration(interruptedStart.assistantMessageId(), "被取消的部分");

        ConversationMessageEntity interrupted = conversationMessageRepository.findById(interruptedStart.assistantMessageId())
                .orElseThrow();
        assertThat(interrupted.getGenerationStatus()).isEqualTo(GenerationStatus.INTERRUPTED);
        assertThat(interrupted.getContent()).isEqualTo("被取消的部分");
    }

    @Test
    void lateCancellationDoesNotOverwriteACompletedGeneration() {
        ConversationSnapshot conversation = conversationService.createConversation();
        GenerationStart start = conversationService.startGeneration(
                conversation.id(), UUID.randomUUID(), "prompt"
        );
        conversationService.completeGeneration(start.assistantMessageId(), "done");
        Instant completedAt = conversationMessageRepository.findById(start.assistantMessageId())
                .orElseThrow()
                .getCompletedAt();

        clock.set(FIXED_NOW.plusSeconds(1));
        conversationService.interruptGeneration(start.assistantMessageId(), "late-part");

        ConversationMessageEntity persisted = conversationMessageRepository.findById(start.assistantMessageId())
                .orElseThrow();
        assertThat(persisted.getGenerationStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(persisted.getContent()).isEqualTo("done");
        assertThat(persisted.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void listsConversationsByRecentActivityAndReturnsAllPersistedMessages() {
        ConversationSnapshot firstConversation = conversationService.createConversation();
        GenerationStart generation = conversationService.startGeneration(
                firstConversation.id(), UUID.randomUUID(), "第一条消息"
        );
        conversationService.completeGeneration(generation.assistantMessageId(), "第一条回复");

        clock.set(FIXED_NOW.plusSeconds(1));
        ConversationSnapshot secondConversation = conversationService.createConversation();

        assertThat(conversationService.listConversations()).extracting(ConversationSnapshot::id)
                .containsExactly(secondConversation.id(), firstConversation.id());
        assertThat(conversationService.getMessages(firstConversation.id()))
                .extracting(ConversationMessageSnapshot::content)
                .containsExactly("第一条消息", "第一条回复");
    }

    @Test
    void searchesByDeviceTimeAndMessageContentThenPhysicallyDeletesOnlyTheSelectedMessage() {
        UUID deviceId = UUID.fromString("96f6cd3c-5090-4718-a2ef-9ea7eb85ed17");
        jdbcTemplate.update("""
                insert into devices(id, hardware_id, firmware_version, display_name, safety_state, credential_version)
                values (?, ?, ?, ?, ?, ?)
                """, deviceId, "personal-data-device", "test", "书桌 StackChan", "motion_disabled", 0);
        ConversationSnapshot conversation = conversationService.createConversation();
        jdbcTemplate.update(
                "insert into device_voice_conversations(device_id, conversation_id) values (?, ?)",
                deviceId, conversation.id()
        );
        GenerationStart generation = conversationService.startGeneration(
                conversation.id(), UUID.randomUUID(), "跨年旅行安排"
        );
        conversationService.completeGeneration(generation.assistantMessageId(), "已经记下了");

        PersonalDataService.ConversationPage result = personalDataService.list(
                new PersonalDataService.ConversationFilter(
                        "旅行", deviceId, FIXED_NOW.minusSeconds(1), FIXED_NOW.plusSeconds(1), null
                ),
                0,
                20
        );

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list().getFirst())
                .extracting(
                        PersonalDataService.ConversationSummary::id,
                        PersonalDataService.ConversationSummary::deviceId,
                        PersonalDataService.ConversationSummary::deviceName,
                        PersonalDataService.ConversationSummary::messageCount
                )
                .containsExactly(conversation.id(), deviceId, "书桌 StackChan", 2L);

        personalDataService.deleteMessage(conversation.id(), generation.userMessageId());

        assertThat(personalDataService.messages(conversation.id()))
                .extracting(ConversationMessageSnapshot::content)
                .containsExactly("已经记下了");
        assertThat(conversationMessageRepository.findById(generation.assistantMessageId()).orElseThrow()
                .getInReplyToMessageId()).isNull();
        assertThat(personalDataService.list(
                new PersonalDataService.ConversationFilter("旅行", null, null, null, null), 0, 20
        ).total()).isZero();
    }

    @Test
    void exportsOnlyTheRequestedScopeAndRejectsDeletionWhileStreaming() {
        ConversationSnapshot first = conversationService.createConversation();
        GenerationStart completed = conversationService.startGeneration(first.id(), UUID.randomUUID(), "仅导出这段");
        conversationService.completeGeneration(completed.assistantMessageId(), "导出回复");

        ConversationSnapshot second = conversationService.createConversation();
        GenerationStart streaming = conversationService.startGeneration(second.id(), UUID.randomUUID(), "仍在生成");

        PersonalDataService.ConversationExport export = personalDataService.export(
                new PersonalDataService.ConversationFilter("", null, null, null, null), first.id()
        );

        assertThat(export.schemaVersion()).isEqualTo(1);
        assertThat(export.conversations()).hasSize(1);
        assertThat(export.conversations().getFirst().conversation().id()).isEqualTo(first.id());
        assertThat(export.conversations().getFirst().messages())
                .extracting(ConversationMessageSnapshot::content)
                .containsExactly("仅导出这段", "导出回复");
        assertThatThrownBy(() -> personalDataService.deleteMessage(second.id(), streaming.assistantMessageId()))
                .isInstanceOf(PersonalDataConflictException.class);
        assertThatThrownBy(() -> personalDataService.deleteConversation(second.id()))
                .isInstanceOf(PersonalDataConflictException.class);

        conversationService.interruptGeneration(streaming.assistantMessageId(), "");
        personalDataService.deleteConversation(second.id());
        assertThat(conversationRepository.existsById(second.id())).isFalse();
    }

    @Test
    void returnsTheOriginalAssistantForADuplicateWhenSeveralMessagesShareTheSameTimestamp() {
        ConversationSnapshot conversation = conversationService.createConversation();
        UUID firstClientMessageId = UUID.randomUUID();
        GenerationStart firstStart = conversationService.startGeneration(
                conversation.id(), firstClientMessageId, "first"
        );

        for (int index = 0; index < 32; index++) {
            conversationService.startGeneration(conversation.id(), UUID.randomUUID(), "other-" + index);
        }

        GenerationStart duplicate = conversationService.startGeneration(conversation.id(), firstClientMessageId, "first");

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.userMessageId()).isEqualTo(firstStart.userMessageId());
        assertThat(duplicate.assistantMessageId()).isEqualTo(firstStart.assistantMessageId());
    }

    @Test
    void duplicateGenerationReturnsThePersistedAssistantState() {
        ConversationSnapshot conversation = conversationService.createConversation();
        UUID clientMessageId = UUID.fromString("1dc9af27-2b07-49dc-b78c-878409861f24");
        GenerationStart first = conversationService.startGeneration(conversation.id(), clientMessageId, "hello");
        conversationService.completeGeneration(first.assistantMessageId(), "already done");

        GenerationStart duplicate = conversationService.startGeneration(conversation.id(), clientMessageId, "changed");

        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.assistantStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(duplicate.assistantContent()).isEqualTo("already done");
        assertThat(conversationMessageRepository.findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.id()))
                .extracting(ConversationMessageEntity::getContent)
                .containsExactly("hello", "already done");
    }

    @Test
    void recoversOnlyStreamingAssistantsAndTouchesEachConversation() {
        ConversationSnapshot firstConversation = conversationService.createConversation();
        GenerationStart firstStreaming = conversationService.startGeneration(
                firstConversation.id(), UUID.randomUUID(), "prompt-a"
        );
        jdbcTemplate.update(
                "update conversation_messages set content = ? where id = ?",
                "part-a",
                firstStreaming.assistantMessageId()
        );
        GenerationStart firstConversationSecondStreaming = conversationService.startGeneration(
                firstConversation.id(), UUID.randomUUID(), "prompt-a2"
        );
        jdbcTemplate.update(
                "update conversation_messages set content = ? where id = ?",
                "part-a2",
                firstConversationSecondStreaming.assistantMessageId()
        );
        GenerationStart completedStart = conversationService.startGeneration(
                firstConversation.id(), UUID.randomUUID(), "prompt-completed"
        );
        conversationService.completeGeneration(completedStart.assistantMessageId(), "done");

        ConversationSnapshot secondConversation = conversationService.createConversation();
        GenerationStart secondStreaming = conversationService.startGeneration(
                secondConversation.id(), UUID.randomUUID(), "prompt-b"
        );
        jdbcTemplate.update(
                "update conversation_messages set content = ? where id = ?",
                "part-b",
                secondStreaming.assistantMessageId()
        );
        GenerationStart failedStart = conversationService.startGeneration(
                secondConversation.id(), UUID.randomUUID(), "prompt-failed"
        );
        conversationService.failGeneration(failedStart.assistantMessageId(), "generation_failed", "failed-part");
        GenerationStart interruptedStart = conversationService.startGeneration(
                secondConversation.id(), UUID.randomUUID(), "prompt-interrupted"
        );
        conversationService.interruptGeneration(interruptedStart.assistantMessageId(), "interrupted-part");

        Instant recoveryTime = FIXED_NOW.plusSeconds(60);
        clock.set(recoveryTime);

        int recovered = conversationService.recoverStreamingGenerations();

        assertThat(recovered).isEqualTo(3);
        ConversationMessageEntity recoveredFirst = conversationMessageRepository
                .findById(firstStreaming.assistantMessageId()).orElseThrow();
        ConversationMessageEntity recoveredFirstConversationSecond = conversationMessageRepository
                .findById(firstConversationSecondStreaming.assistantMessageId()).orElseThrow();
        ConversationMessageEntity recoveredSecond = conversationMessageRepository
                .findById(secondStreaming.assistantMessageId()).orElseThrow();
        assertThat(List.of(recoveredFirst, recoveredFirstConversationSecond, recoveredSecond))
                .extracting(ConversationMessageEntity::getGenerationStatus)
                .containsOnly(GenerationStatus.INTERRUPTED);
        assertThat(recoveredFirst.getContent()).isEqualTo("part-a");
        assertThat(recoveredFirstConversationSecond.getContent()).isEqualTo("part-a2");
        assertThat(recoveredSecond.getContent()).isEqualTo("part-b");
        assertThat(List.of(recoveredFirst, recoveredFirstConversationSecond, recoveredSecond))
                .extracting(ConversationMessageEntity::getCompletedAt)
                .containsOnly(recoveryTime);

        assertThat(conversationMessageRepository.findById(completedStart.assistantMessageId()).orElseThrow())
                .extracting(
                        ConversationMessageEntity::getGenerationStatus,
                        ConversationMessageEntity::getContent,
                        ConversationMessageEntity::getCompletedAt
                )
                .containsExactly(GenerationStatus.COMPLETED, "done", FIXED_NOW);
        assertThat(conversationMessageRepository.findById(failedStart.assistantMessageId()).orElseThrow())
                .extracting(
                        ConversationMessageEntity::getGenerationStatus,
                        ConversationMessageEntity::getFailureCode,
                        ConversationMessageEntity::getContent,
                        ConversationMessageEntity::getCompletedAt
                )
                .containsExactly(GenerationStatus.FAILED, "generation_failed", "failed-part", FIXED_NOW);
        assertThat(conversationMessageRepository.findById(interruptedStart.assistantMessageId()).orElseThrow())
                .extracting(
                        ConversationMessageEntity::getGenerationStatus,
                        ConversationMessageEntity::getContent,
                        ConversationMessageEntity::getCompletedAt
                )
                .containsExactly(GenerationStatus.INTERRUPTED, "interrupted-part", FIXED_NOW);
        assertThat(conversationMessageRepository.findById(firstStreaming.userMessageId()).orElseThrow())
                .extracting(
                        ConversationMessageEntity::getRole,
                        ConversationMessageEntity::getGenerationStatus,
                        ConversationMessageEntity::getCompletedAt
                )
                .containsExactly(MessageRole.USER, GenerationStatus.COMPLETED, FIXED_NOW);
        assertThat(conversationRepository.findById(firstConversation.id()).orElseThrow().getUpdatedAt())
                .isEqualTo(recoveryTime);
        assertThat(conversationRepository.findById(secondConversation.id()).orElseThrow().getUpdatedAt())
                .isEqualTo(recoveryTime);
    }

    @Test
    void concurrentRetriesWithTheSameClientMessageReturnTheWinningMessagePair() throws Exception {
        ConversationSnapshot conversation = conversationService.createConversation();
        UUID clientMessageId = UUID.fromString("a19fac7a-e159-40bb-a543-54e7378d877f");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS delay_idempotent_message_insert ON conversation_messages");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS delay_idempotent_message_insert()");
        jdbcTemplate.execute("""
                CREATE FUNCTION delay_idempotent_message_insert()
                RETURNS trigger AS $$
                BEGIN
                  IF NEW.client_message_id = '%s'::uuid THEN
                    PERFORM pg_sleep(1);
                  END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(clientMessageId));
        jdbcTemplate.execute("""
                CREATE TRIGGER delay_idempotent_message_insert
                BEFORE INSERT ON conversation_messages
                FOR EACH ROW EXECUTE FUNCTION delay_idempotent_message_insert()
                """);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<GenerationStart> firstFuture = executor.submit(() -> {
                start.await();
                return conversationService.startGeneration(conversation.id(), clientMessageId, "same request");
            });
            Future<GenerationStart> secondFuture = executor.submit(() -> {
                start.await();
                return conversationService.startGeneration(conversation.id(), clientMessageId, "same request");
            });
            start.countDown();

            GenerationStart first = firstFuture.get(10, TimeUnit.SECONDS);
            GenerationStart second = secondFuture.get(10, TimeUnit.SECONDS);

            assertThat(List.of(first.duplicate(), second.duplicate()))
                    .containsExactlyInAnyOrder(false, true);
            assertThat(second.userMessageId()).isEqualTo(first.userMessageId());
            assertThat(second.assistantMessageId()).isEqualTo(first.assistantMessageId());
            assertThat(conversationMessageRepository
                    .findAllByConversationIdOrderByCreatedAtAscIdAsc(conversation.id()))
                    .hasSize(2);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS delay_idempotent_message_insert ON conversation_messages");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS delay_idempotent_message_insert()");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        AdjustableClock fixedClock() {
            return new AdjustableClock(FIXED_NOW);
        }
    }

    static class AdjustableClock extends Clock {

        private Instant current;

        AdjustableClock(Instant current) {
            this.current = current;
        }

        void set(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
