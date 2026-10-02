package com.kj.stackchan.voiceaction;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.memory.*;
import com.kj.stackchan.reminder.*;
import com.kj.stackchan.persona.*;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleRepository;
import com.kj.stackchan.role.CompanionRoleService;
import com.kj.stackchan.speech.RecentProactiveContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class CompanionConsentPersistenceTest {
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
    @Autowired DeviceRepository devices;
    @Autowired DeviceVoiceConversationService conversations;
    @Autowired VoiceActionCoordinator coordinator;
    @Autowired VoiceActionProposalService proposals;
    @Autowired VoiceActionProposalRepository proposalRepository;
    @Autowired LongTermMemoryService memories;
    @Autowired ReminderRepository reminders;
    @Autowired CompanionRoleRepository roles;
    @Autowired CompanionRoleService roleService;
    @Autowired RecentProactiveContextService context;
    @Autowired JdbcTemplate jdbc;

    @Test
    void voiceMemoryConfirmationPinsTheExactCandidateAndExecutesOnlyOnce() {
        var f = fixture();
        assertThat(coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "请记住我喜欢手冲咖啡").reply())
                .contains("我喜欢手冲咖啡", "确认记住");
        var proposal = proposals.latestPending(f.device, f.conversation);
        var candidate = proposalRepository.findById(proposal.id()).orElseThrow().getTargetReference();
        assertThat(memories.get(candidate).confirmationStatus()).isEqualTo(MemoryConfirmationStatus.PENDING);
        assertThat(proposals.confirm(proposal.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        var confirmedAt = memories.get(candidate).confirmedAt();
        assertThat(proposals.confirm(proposal.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(memories.get(candidate).confirmedAt()).isEqualTo(confirmedAt);
        assertThat(memories.get(candidate).roleId()).isEqualTo(CompanionRoleEntity.DEFAULT_ROLE_ID);
        assertThat(memories.get(candidate).deviceId()).isEqualTo(f.device);
        assertThat(jdbc.queryForObject("select count(*) from voice_action_audits where proposal_id=? and event_type='EXECUTED'",
                Integer.class, proposal.id())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "reject", "confirm", "delete"})
    void controlPanelChangesInvalidateOldVoiceConsent(String operation) {
        var f = fixture();
        coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "记住我喜欢散步");
        var proposal = proposals.latestPending(f.device, f.conversation);
        UUID candidate = proposalRepository.findById(proposal.id()).orElseThrow().getTargetReference();
        switch (operation) {
            case "edit" -> memories.update(candidate, new LongTermMemoryService.MemoryCommand(
                    MemoryScopeType.DEVICE, f.device, MemoryCategory.USER_PROFILE, "更新偏好", "我喜欢游泳"));
            case "reject" -> memories.reject(candidate);
            case "confirm" -> memories.confirm(candidate);
            case "delete" -> memories.delete(candidate);
        }
        assertThat(proposals.confirm(proposal.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(proposalRepository.findById(proposal.id()).orElseThrow().getStatus()).isEqualTo(VoiceActionStatus.FAILED);
    }

    @Test
    void devicePartnerAndExpiredProposalCannotReuseConsent() {
        var f = fixture();
        coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "记住我喜欢安静");
        var proposal = proposals.latestPending(f.device, f.conversation);
        var other = fixture();
        assertThatThrownBy(() -> proposals.confirm(proposal.id(), other.device, f.conversation))
                .isInstanceOf(VoiceActionException.class);
        UUID partner = roles.saveAndFlush(new CompanionRoleEntity("同意范围测试", PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", "", Instant.now())).getId();
        roleService.switchActive(f.device, partner);
        assertThat(proposals.confirm(proposal.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);

        coordinator.handle(other.device, other.conversation, UUID.randomUUID(), "记住我喜欢读书");
        var old = proposals.latestPending(other.device, other.conversation);
        Instant now = Instant.now();
        jdbc.update("update voice_action_proposals set created_at=?, expires_at=? where id=?",
                Timestamp.from(now.minusSeconds(180)), Timestamp.from(now.minusSeconds(60)), old.id());
        assertThat(proposals.latestPending(other.device, other.conversation)).isNull();
        assertThat(proposalRepository.findById(old.id()).orElseThrow().getStatus()).isEqualTo(VoiceActionStatus.EXPIRED);
        assertThat(coordinator.handle(other.device, other.conversation, UUID.randomUUID(), "记住我喜欢绿茶").reply())
                .contains("确认记住");
    }

    @Test
    void changedReplacementIsNotSilentlySupersededByOldConsent() {
        var f = fixture();
        var previous = memories.create(new LongTermMemoryService.MemoryCommand(MemoryScopeType.DEVICE,
                f.device, MemoryCategory.USER_PROFILE, "语音记忆建议", "我喜欢咖啡"));
        var suggestion = proposals.propose(f.device, f.conversation, UUID.randomUUID(),
                new VoiceActionDraft(VoiceActionType.CREATE_MEMORY_SUGGESTION, false, "我现在喜欢绿茶", "语音记忆建议",
                        null, null, null, null, null, null, null, MemoryCategory.USER_PROFILE.name(), null));
        proposals.prepareMemoryConfirmation(suggestion.id(), f.device, f.conversation);
        var pending = proposals.latestPending(f.device, f.conversation);
        memories.update(previous.id(), new LongTermMemoryService.MemoryCommand(MemoryScopeType.DEVICE,
                f.device, MemoryCategory.USER_PROFILE, "语音记忆建议", "我喜欢红茶"));
        assertThat(proposals.confirm(pending.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(memories.get(previous.id()).enabled()).isTrue();
        assertThat(memories.get(previous.id()).content()).isEqualTo("我喜欢红茶");
    }

    @Test
    void rejectedSensitiveSuggestionPersistsFailureWithoutCreatingMemory() {
        var f = fixture();
        assertThat(coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "记住我的密码").reply())
                .contains("没有执行");
        assertThat(proposals.latestPending(f.device, f.conversation)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from long_term_memories where device_id=?",
                Integer.class, f.device)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from voice_action_proposals where device_id=? and status='FAILED'",
                Integer.class, f.device)).isEqualTo(1);
    }

    @Test
    void concurrentConfirmationsCreateOneFollowUpAndOneExecutionAudit() throws Exception {
        var f = fixture();
        coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "十分钟后问问我面试结果");
        var pending = proposals.latestPending(f.device, f.conversation);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> proposals.confirm(pending.id(), f.device, f.conversation));
            var second = executor.submit(() -> proposals.confirm(pending.id(), f.device, f.conversation));
            assertThat(first.get(20, TimeUnit.SECONDS).status()).isEqualTo(VoiceActionStatus.EXECUTED);
            assertThat(second.get(20, TimeUnit.SECONDS).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        }
        assertThat(jdbc.queryForObject("select count(*) from reminders where device_id=? and source='FOLLOW_UP'",
                Integer.class, f.device)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from voice_action_audits where proposal_id=? and event_type='EXECUTED'",
                Integer.class, pending.id())).isEqualTo(1);
    }

    @Test
    void followUpNeedsCurrentExplicitConsentAndContextRequiresActualScopedPlayback() {
        var f = fixture();
        assertThat(coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "明天问我一下").reply())
                .contains("没有安排");
        assertThat(proposals.latestPending(f.device, f.conversation)).isNull();
        coordinator.handle(f.device, f.conversation, UUID.randomUUID(), "十分钟后问问我面试结果");
        var pending = proposals.latestPending(f.device, f.conversation);
        assertThat(pending.actionType()).isEqualTo(VoiceActionType.CREATE_FOLLOW_UP);
        assertThatThrownBy(() -> jdbc.update("update voice_action_proposals set recurrence_type=null where id=?", pending.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(reminders.findAll().stream().filter(r -> r.getDeviceId().equals(f.device))).isEmpty();
        var executed = proposals.confirm(pending.id(), f.device, f.conversation);
        assertThat(executed.status()).isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(proposals.confirm(pending.id(), f.device, f.conversation).resultReference()).isEqualTo(executed.resultReference());
        var reminder = reminders.findById(executed.resultReference()).orElseThrow();
        assertThat(reminder.getSource()).isEqualTo(ReminderSource.FOLLOW_UP);
        assertThat(reminder.getExpiresAt()).isEqualTo(reminder.getScheduledAt().plusSeconds(14400));
        assertThatThrownBy(() -> jdbc.update("update reminders set proactive_generation_status=null where id=?", reminder.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(context.context(f.device, reminder.getRoleId())).isEmpty();
        Instant played = Instant.now().minusSeconds(1);
        jdbc.update("update reminders set status='DELIVERED',last_outcome='DELIVERED',last_completed_at=? where id=?",
                Timestamp.from(played), reminder.getId());
        assertThat(context.context(f.device, reminder.getRoleId())).contains("面试结果");
        assertThat(context.context(fixture().device, reminder.getRoleId())).isEmpty();
        assertThat(context.context(f.device, UUID.randomUUID())).isEmpty();
        assertThat(context.context(f.device, reminder.getRoleId(), played.plusSeconds(1))).isEmpty();
        assertThatThrownBy(() -> jdbc.update("update reminders set recurrence_type='DAILY' where id=?", reminder.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Fixture fixture() {
        UUID device = devices.saveAndFlush(new DeviceEntity("consent-" + UUID.randomUUID(), "test")).getId();
        return new Fixture(device, conversations.getOrCreateConversationId(device));
    }
    private record Fixture(UUID device, UUID conversation) {}
}
