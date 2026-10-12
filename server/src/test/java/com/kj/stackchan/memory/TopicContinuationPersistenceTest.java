package com.kj.stackchan.memory;

import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.kj.stackchan.conversation.*;
import com.kj.stackchan.device.*;
import com.kj.stackchan.persona.*;
import com.kj.stackchan.role.*;
import com.kj.stackchan.voiceaction.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class TopicContinuationPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @TestConfiguration
    static class TimeConfiguration {
        @Bean @Primary MutableClock continuationClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-10-05T10:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @Autowired MutableClock clock;
    @Autowired TopicContinuationService topics;
    @Autowired LongTermMemoryService memories;
    @Autowired LongTermMemoryRepository repository;
    @Autowired DeviceRepository devices;
    @Autowired DeviceVoiceConversationService voiceConversations;
    @Autowired ConversationService conversations;
    @Autowired PersonalDataService personalData;
    @Autowired CompanionRoleService roles;
    @Autowired VoiceActionProposalService proposals;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void resetTime() { clock.now = Instant.parse("2026-10-05T10:00:00Z"); }

    @Test void candidateRequiresConfirmationAndOnlyRelevantCrossDaySamePartnerSameDeviceContext() {
        var f = fixture(); var other = fixture();
        UUID id = suggest(f, "木工", "我正在准备木工作品");
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.PENDING);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
        memories.confirm(id);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
        clock.now = clock.now.plus(Duration.ofDays(1));
        assertThat(topics.context(f.role, f.device, "木工")).contains("木工", "未知结果只可询问");
        assertThat(topics.context(other.role, other.device, "木工")).isEmpty();
        assertThat(topics.context(UUID.randomUUID(), f.device, "木工")).isEmpty();
        assertThat(topics.context(f.role, f.device, "晚饭吃什么")).isEmpty();
        assertThat(topics.context(f.role, null, "木工")).isEmpty();
        assertThat(memories.loadProactiveCandidates(f.role, f.device, 8)).isEmpty();
    }

    @Test void generationBudgetIsDeviceWideAtomicAndSurvivesDeletion() throws Exception {
        var f = fixture(); UUID otherRole = newRole();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> suggest(f, "木工", "我正在准备木工作品"));
            var b = executor.submit(() -> suggest(new Fixture(f.device, otherRole, f.conversation), "摄影", "我正在练习摄影"));
            a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from long_term_memories where device_id = ? and continuation_state <> 'NONE'", Integer.class, f.device)).isEqualTo(1);
        jdbc.update("delete from long_term_memories where device_id = ?", f.device);
        suggest(f, "种植", "我正在准备种植番茄");
        assertThat(all(f)).isEmpty();
        clock.now = clock.now.plus(Duration.ofDays(1));
        assertThat(suggest(f, "种植", "我正在准备种植番茄")).isNotNull();
    }

    @Test void offersAreRelevantOnceAndCancellationRejectsWithoutAnotherSuggestion() {
        var f = fixture(); UUID id = suggest(f, "木工", "我正在准备木工作品");
        assertThat(proposals.prepareContinuationConfirmation(f.device, f.conversation, "晚饭")).isNull();
        var offer = proposals.prepareContinuationConfirmation(f.device, f.conversation, "木工进展");
        assertThat(offer.content()).isEqualTo(memories.get(id).content());
        assertThat(proposals.restatement(offer)).contains("以后相关聊天", "保存你明确说的新进展");
        assertThat(memories.get(id).confirmationStatus()).isEqualTo(MemoryConfirmationStatus.PENDING);
        proposals.cancel(offer.id(), f.device, f.conversation);
        assertThat(memories.get(id).confirmationStatus()).isEqualTo(MemoryConfirmationStatus.REJECTED);
        clock.now = clock.now.plus(Duration.ofDays(1));
        assertThat(proposals.prepareContinuationConfirmation(f.device, f.conversation, "木工")).isNull();
        suggest(f, "木工", "我正在准备木工作品");
        assertThat(all(f)).hasSize(1);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
    }

    @Test void oldRoleConsentAndEditedSnapshotCannotActivateTopic() {
        var f = fixture(); UUID id = suggest(f, "木工", "我正在准备木工作品");
        var offer = proposals.prepareContinuationConfirmation(f.device, f.conversation, "木工");
        clock.now = clock.now.plusSeconds(1);
        memories.update(id, command(f.device, "木工", "我准备木工书架"));
        assertThat(proposals.confirm(offer.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(memories.get(id).confirmationStatus()).isEqualTo(MemoryConfirmationStatus.PENDING);
        var other = fixture(); UUID candidate = suggest(other, "摄影", "我正在练习摄影");
        var second = proposals.prepareContinuationConfirmation(other.device, other.conversation, "摄影");
        roles.switchActive(other.device, newRole());
        roles.switchActive(other.device, other.role);
        assertThat(proposals.confirm(second.id(), other.device, other.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(memories.get(candidate).confirmationStatus()).isEqualTo(MemoryConfirmationStatus.PENDING);
    }

    @Test void confirmedCardIsIdempotentAndStartsConversationalPermission() {
        var f = fixture(); UUID id = suggest(f, "木工", "我正在准备木工作品");
        var offer = proposals.prepareContinuationConfirmation(f.device, f.conversation, "木工");
        assertThat(proposals.confirm(offer.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(proposals.confirm(offer.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.ACTIVE);
    }

    @Test void offerBudgetRemainsDeviceWideAcrossDaysPartnersAndExpiredProposals() {
        var f = fixture(); suggest(f, "木工", "我正在准备木工作品");
        clock.now = clock.now.plus(Duration.ofDays(1));
        UUID otherRole = newRole();
        roles.switchActive(f.device, otherRole);
        var second = new Fixture(f.device, otherRole, voiceConversations.getOrCreateConversationId(f.device));
        suggest(second, "摄影", "我正在练习摄影");
        roles.switchActive(f.device, f.role);
        UUID originalConversation = voiceConversations.getOrCreateConversationId(f.device);
        var offer = proposals.prepareContinuationConfirmation(f.device, originalConversation, "木工");
        assertThat(offer).isNotNull();
        clock.now = clock.now.plusSeconds(121);
        assertThat(proposals.latestPending(f.device, originalConversation)).isNull();
        roles.switchActive(f.device, otherRole);
        assertThat(proposals.prepareContinuationConfirmation(f.device, voiceConversations.getOrCreateConversationId(f.device), "摄影")).isNull();
        clock.now = clock.now.plus(Duration.ofDays(1));
        assertThat(proposals.prepareContinuationConfirmation(f.device, voiceConversations.getOrCreateConversationId(f.device), "摄影")).isNotNull();
    }

    @Test void pauseEndsAtLocalMidnightWhileStopNeedsExplicitResume() {
        var f = fixture(); UUID id = confirmed(f);
        topics.recordDiscussion(f.role, f.device, f.conversation, "我的木工已经完成底座", "收到");
        assertThat(topics.handleControl(f.role, f.device, f.conversation, null, "这事先不聊了")).contains("今天先不聊");
        assertThat(memories.get(id).continuation().pausedUntil()).isEqualTo(Instant.parse("2026-10-05T16:00:00Z"));
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
        clock.now = Instant.parse("2026-10-05T16:00:00Z");
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.ACTIVE);
        assertThat(topics.handleControl(f.role, f.device, f.conversation, null, "继续聊木工")).contains("继续聊");
        topics.change(id, TopicContinuationService.Control.STOP);
        clock.now = clock.now.plus(Duration.ofDays(1));
        topics.recordDiscussion(f.role, f.device, f.conversation, "木工已经开始上色", "收到");
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.STOPPED);
        topics.handleControl(f.role, f.device, f.conversation, null, "继续聊木工");
        assertThat(topics.context(f.role, f.device, "木工")).isNotEmpty();
    }

    @Test void daylightSavingPauseUsesCalendarDayRatherThanTwentyFourHours() {
        var f = fixture(); UUID id = confirmed(f);
        // Create settings using the normal service, then change only the test timezone in this isolated DB.
        jdbc.update("insert into device_interaction_settings(device_id,updated_at) values (?,now()) on conflict do nothing", f.device);
        jdbc.update("update device_interaction_settings set zone_id = 'America/New_York' where device_id = ?", f.device);
        clock.now = Instant.parse("2026-03-08T05:30:00Z");
        assertThat(topics.change(id, TopicContinuationService.Control.PAUSE_TODAY).continuation().pausedUntil())
                .isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
    }

    @Test void fourteenDaysWithoutFactsDormantButNamingTopicCanResume() {
        var f = fixture(); UUID id = confirmed(f);
        clock.now = clock.now.plus(Duration.ofDays(14)).minusSeconds(1);
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.ACTIVE);
        topics.recordDiscussion(f.role, f.device, f.conversation, "木工怎么样？", "你想问什么");
        clock.now = clock.now.plusSeconds(1);
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.DORMANT);
        assertThat(topics.context(f.role, f.device, "晚饭")).isEmpty();
        assertThat(topics.context(f.role, f.device, "木工")).isNotEmpty();
        topics.recordDiscussion(f.role, f.device, f.conversation, "我在准备木工上色", "好的");
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.ACTIVE);
    }

    @Test void shortControlsRequireRecentSameConversationAndDoNotInterpretQuotes() {
        var f = fixture(); UUID id = confirmed(f);
        assertThat(topics.handleControl(f.role, f.device, f.conversation, null, "别再提这个")).isNull();
        topics.recordDiscussion(f.role, f.device, f.conversation, "木工已经完成底座", "收到");
        assertThat(topics.handleControl(f.role, f.device, UUID.randomUUID(), null, "先不聊")).isNull();
        assertThat(topics.handleControl(f.role, f.device, f.conversation, clock.now.plusSeconds(1), "先不聊")).isNull();
        assertThat(topics.handleControl(f.role, f.device, f.conversation, null, "他说别再提这个是什么意思")).isNull();
        assertThat(topics.handleControl(f.role, f.device, f.conversation, null, "别再提这个")).contains("不再提");
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.STOPPED);
    }

    @ParameterizedTest @ValueSource(strings = {"木工的进展怎么样？", "木工已经完成了吗", "如果木工完成了我就出去旅行", "我正在准备木工，密码是hunter2", "我准备木工时很焦虑"})
    void questionsSensitiveOrEmotionalStatementsCannotBecomeSavedProgress(String input) {
        var f = fixture(); UUID id = confirmed(f);
        topics.recordDiscussion(f.role, f.device, f.conversation, input, "我猜你已经获奖了");
        assertThat(memories.get(id).continuation().progress()).isEmpty();
    }

    @Test void progressUsesOnlyUserFactsAndDeletingSourceConversationClearsTheCopy() {
        var f = fixture(); UUID id = confirmed(f);
        topics.recordDiscussion(f.role, f.device, f.conversation, "木工已经完成底座", "你已经获奖了");
        assertThat(memories.get(id).continuation().progress()).isEqualTo("木工已经完成底座");
        var other = conversations.createConversation().id();
        topics.recordDiscussion(f.role, f.device, other, "木工现在怎么样？", "不确定");
        personalData.deleteConversation(f.conversation);
        assertThat(memories.get(id).continuation().progress()).isEmpty();
        assertThat(memories.get(id).confirmationStatus()).isEqualTo(MemoryConfirmationStatus.CONFIRMED);
    }

    @ParameterizedTest @ValueSource(strings = {"我正在准备木工，很焦虑", "我正在准备木工，银行卡号是6222021234567890123", "我正在准备木工，和朋友吵架了"})
    void excludedSourcesDoNotCreateCandidateOrSpendDailyQuota(String source) {
        var f = fixture(); assertThat(suggest(f, "木工", source)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from continuation_daily_budget where device_id = ?", Integer.class, f.device)).isZero();
    }

    @Test void ordinaryMemoryCannotAcquireContinuationPermissionThroughControlEndpoint() {
        var f = fixture(); UUID id = memories.create(command(f.device, "木工", "我正在准备木工")).id();
        assertThat(memories.get(id).continuation()).isNull();
        assertThatThrownBy(() -> topics.change(id, TopicContinuationService.Control.RESUME)).isInstanceOf(InvalidMemoryException.class);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
    }

    @Test void explicitShortReferenceUsesRecentConversationAndRespectsResetBoundary() {
        var f = fixture(); UUID id = confirmed(f);
        clock.now = clock.now.plus(Duration.ofDays(1));
        topics.recordDiscussion(f.role, f.device, f.conversation, "木工已经完成底座", "收到");
        assertThat(topics.assembleContext(f.role, f.device, f.conversation, null, "它现在怎么样？").memoryId()).isEqualTo(id);
        topics.recordDiscussion(f.role, f.device, f.conversation, "它已经开始上色了", "收到");
        assertThat(memories.get(id).continuation().progress()).isEqualTo("它已经开始上色了");
        assertThat(topics.assembleContext(f.role, f.device, f.conversation, clock.now.plusSeconds(1), "它现在怎么样？").memoryId()).isNull();
        clock.now = clock.now.plus(Duration.ofMinutes(31));
        assertThat(topics.assembleContext(f.role, f.device, f.conversation, null, "它现在怎么样？").memoryId()).isNull();
    }

    @Test void disabledOrDeletedTopicCannotContinueAndPauseDoesNotResumeStoppedTopic() {
        var f = fixture(); UUID id = confirmed(f);
        clock.now = clock.now.plus(Duration.ofDays(1));
        memories.setEnabled(id, false);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
        assertThatThrownBy(() -> topics.change(id, TopicContinuationService.Control.RESUME)).isInstanceOf(InvalidMemoryException.class);
        memories.setEnabled(id, true);
        topics.change(id, TopicContinuationService.Control.STOP);
        assertThatThrownBy(() -> topics.change(id, TopicContinuationService.Control.PAUSE_TODAY)).isInstanceOf(InvalidMemoryException.class);
        assertThat(memories.get(id).continuation().state()).isEqualTo(ContinuationState.STOPPED);
        memories.delete(id);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
    }

    @Test void retainedRefusalIsNotLostBehindOneHundredMoreRecentTopics() {
        var f = fixture(); UUID id = confirmed(f);
        topics.change(id, TopicContinuationService.Control.STOP);
        jdbc.update("""
                insert into long_term_memories(id,scope_type,device_id,category,title,content,source,source_detail,
                    confirmation_status,enabled,created_at,updated_at,role_id,topic_key,continuation_state)
                select gen_random_uuid(),'DEVICE',?,'EVENT','编织'||n,'编织计划','ASSISTANT_SUGGESTED','测试数据',
                    'REJECTED',false,?, ?,?,'编织'||n,'STOPPED' from generate_series(1,101) n
                """, f.device, Timestamp.from(clock.now.plusSeconds(1)), Timestamp.from(clock.now.plusSeconds(1)), f.role);
        clock.now = clock.now.plus(Duration.ofDays(1));
        suggest(f, "木工", "我正在准备木工书架");
        assertThat(jdbc.queryForObject("select count(*) from long_term_memories where device_id = ?", Integer.class, f.device)).isEqualTo(102);
        assertThat(topics.context(f.role, f.device, "木工")).isEmpty();
        topics.change(id, TopicContinuationService.Control.RESUME);
        assertThat(topics.context(f.role, f.device, "木工")).isNotEmpty();
    }

    @Test void deletedOrUnsuccessfulSourceMessageCannotBeCopiedByLateCompletion() {
        var f = fixture(); UUID id = confirmed(f);
        var turn = conversations.startGeneration(f.conversation, UUID.randomUUID(), "木工已经完成底座");
        topics.recordDiscussion(f.role, f.device, f.conversation, turn.userMessageId(), "木工已经完成底座", "收到");
        assertThat(memories.get(id).continuation().progress()).isEmpty();
        conversations.completeGeneration(turn.assistantMessageId(), "收到");
        topics.recordDiscussion(f.role, f.device, f.conversation, turn.userMessageId(), "木工已经完成底座", "收到");
        assertThat(memories.get(id).continuation().progress()).isEqualTo("木工已经完成底座");
        personalData.deleteMessage(f.conversation, turn.userMessageId());
        topics.recordDiscussion(f.role, f.device, f.conversation, turn.userMessageId(), "木工已经完成底座", "收到");
        assertThat(memories.get(id).continuation().progress()).isEmpty();
    }

    @Test void concurrentSourceDeletionAndProgressCopyLeaveNoCopiedFacts() throws Exception {
        var f = fixture(); UUID id = confirmed(f);
        var turn = conversations.startGeneration(f.conversation, UUID.randomUUID(), "木工已经完成底座");
        conversations.completeGeneration(turn.assistantMessageId(), "收到");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var copy = executor.submit(() -> { start.await(); topics.recordDiscussion(f.role, f.device, f.conversation, turn.userMessageId(), "木工已经完成底座", "收到"); return true; });
            var delete = executor.submit(() -> { start.await(); personalData.deleteMessage(f.conversation, turn.userMessageId()); return true; });
            start.countDown(); copy.get(20, TimeUnit.SECONDS); delete.get(20, TimeUnit.SECONDS);
        }
        assertThat(memories.get(id).continuation().progress()).isEmpty();
    }

    private Fixture fixture() {
        UUID device = devices.saveAndFlush(new DeviceEntity("continuation-" + UUID.randomUUID(), "test")).getId();
        return new Fixture(device, CompanionRoleEntity.DEFAULT_ROLE_ID, voiceConversations.getOrCreateConversationId(device));
    }
    private UUID newRole() { return roles.create(new CompanionRoleService.RoleCommand("伙伴" + UUID.randomUUID(),
            PersonaTone.CALM, PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", "")).id(); }
    private UUID confirmed(Fixture f) { UUID id = suggest(f, "木工", "我正在准备木工作品"); memories.confirm(id); return id; }
    private UUID suggest(Fixture f, String title, String source) {
        topics.suggest(f.role, f.device, source, new LongTermMemoryService.MemorySuggestionCommand(
                command(f.device, title, source), "用户分享了正在进行的事", UUID.randomUUID()));
        return all(f).stream().filter(m -> m.getTitle().equals(title)).map(LongTermMemoryEntity::getId).findFirst().orElse(null);
    }
    private java.util.List<LongTermMemoryEntity> all(Fixture f) { return repository.findContinuationTopics(f.role, f.device, PageRequest.of(0, 100)); }
    private LongTermMemoryService.MemoryCommand command(UUID device, String title, String content) {
        return new LongTermMemoryService.MemoryCommand(MemoryScopeType.DEVICE, device, MemoryCategory.EVENT, title, content, title, 3, false);
    }
    private record Fixture(UUID device, UUID role, UUID conversation) { }
}
