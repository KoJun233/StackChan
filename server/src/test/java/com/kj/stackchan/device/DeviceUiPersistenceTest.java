package com.kj.stackchan.device;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.conversation.DeviceVoiceConversationService;
import com.kj.stackchan.interaction.InteractionSettingsService;
import com.kj.stackchan.persona.*;
import com.kj.stackchan.role.*;
import com.kj.stackchan.task.*;
import com.kj.stackchan.voiceaction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketSession;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Testcontainers
class DeviceUiPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired DeviceRepository devices;
    @Autowired DeviceTokenService tokens;
    @Autowired DeviceConnectionRegistry registry;
    @Autowired DeviceVoiceConversationService conversations;
    @Autowired VoiceActionProposalService proposals;
    @Autowired VoiceActionProposalRepository proposalRepository;
    @Autowired com.kj.stackchan.reminder.ReminderService reminders;
    @Autowired PersonalTaskService tasks;
    @Autowired CompanionRoleService roles;
    @Autowired InteractionSettingsService settings;
    @Autowired DeviceUiService ui;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.kj.stackchan.speech.VoiceTurnDiagnosticsService diagnostics;
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;
    @Autowired com.kj.stackchan.interaction.DeviceQuietTodayService quiet;
    @Autowired com.kj.stackchan.workday.WorkdaySettingsService workdaySettings;
    @Autowired com.kj.stackchan.workday.WorkdayRuntimeService workdayRuntime;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void authenticatedCardPinsLongChineseContentAndRequiresRenderedReceiptBeforeExecuting() throws Exception {
        var f = fixture();
        String content = "这是需要用户在屏幕上阅读的固定内容".repeat(50);
        UUID id = offer(f, VoiceActionDraft.reminder(content, Instant.now().plusSeconds(600), "Asia/Shanghai", "NONE", 1));
        var response = http.perform(get(path(id)).header("Authorization", f.authorization))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content").value(content)).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.time_label").value(org.hamcrest.Matchers.containsString("Asia/Shanghai")))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray().length).isLessThanOrEqualTo(8192);
        http.perform(post(path(id)).header("Authorization", f.authorization).contentType("application/json")
                .content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("confirmation_not_shown"));
        assertThat(proposalRepository.findById(id).orElseThrow().getStatus()).isEqualTo(VoiceActionStatus.PENDING);
        shown(f, id);
        for (int retry = 0; retry < 2; retry++) {
            http.perform(post(path(id)).header("Authorization", f.authorization).contentType("application/json")
                    .content("{\"action\":\"CONFIRM\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXECUTED"))
                    .andExpect(jsonPath("$.result_message").value("已执行"));
        }
        assertThat(jdbc.queryForObject("select count(*) from reminders where device_id=?", Integer.class, f.device)).isEqualTo(1);
    }

    @Test
    void tokenScopesCardsAndRefusesArbitraryDeviceRoleSessionOrActionFields() throws Exception {
        var f = fixture(); var other = fixture();
        UUID id = offer(f, VoiceActionDraft.personalTask("审核屏幕上的内容", null, null));
        http.perform(get(path(id)).header("Authorization", other.authorization)).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
        http.perform(get(path(id))).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        for (String body : new String[]{
                "{\"action\":\"CONFIRM\",\"device_id\":\"" + f.device + "\"}",
                "{\"action\":\"CONFIRM\",\"role_id\":\"" + CompanionRoleEntity.DEFAULT_ROLE_ID + "\"}",
                "{\"action\":\"CONFIRM\",\"conversation_id\":\"" + f.conversation + "\"}",
                "{\"action\":\"CONFIRM\",\"action\":\"CANCEL\"}", "{\"action\":\"EXECUTE_LATEST\"}",
                "{\"action\":\"CONFIRM\"} {}"}) {
            http.perform(post(path(id)).header("Authorization", f.authorization).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(tasks.openForAgent(f.device, CompanionRoleEntity.DEFAULT_ROLE_ID)).isEmpty();
    }

    @Test
    void fullTwoThousandChineseCharactersFitTheCardBudgetWithoutEnteringTheWebsocketNotification() throws Exception {
        var f = fixture(); String content = "这是长文内容".repeat(333) + "中文";
        assertThat(content.length()).isEqualTo(2000);
        var suggestion = proposals.propose(f.device, f.conversation, UUID.randomUUID(),
                new VoiceActionDraft(VoiceActionType.CREATE_MEMORY_SUGGESTION, false, content, "长文记忆", null, null,
                        null, null, null, null, null, "USER_PROFILE", null));
        var proposal = proposals.prepareMemoryConfirmation(suggestion.id(), f.device, f.conversation);
        registry.offerConfirmation(f.device, proposal.id());
        var response = http.perform(get(path(proposal.id())).header("Authorization", f.authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value(content)).andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).hasSizeLessThanOrEqualTo(8192);
        var messages = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.TextMessage.class);
        verify(f.session, atLeastOnce()).sendMessage(messages.capture());
        assertThat(messages.getAllValues().getLast().getPayload()).doesNotContain(content);
        assertThat(messages.getAllValues().getLast().getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .isLessThan(1024);
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "notes", "due", "completed", "delete"})
    void editedOrRemovedTaskCannotReuseTheFixedCompletionConsent(String change) throws Exception {
        var f = fixture();
        var task = tasks.create(new PersonalTaskService.TaskCommand(f.device, "整理会议材料", null,
                PersonalTaskPriority.NORMAL, null, "Asia/Shanghai"));
        UUID id = offer(f, VoiceActionDraft.completePersonalTask(task.id(), task.title()));
        shown(f, id);
        switch (change) {
            case "title", "notes", "due" -> tasks.update(task.id(), new PersonalTaskService.TaskCommand(f.device,
                    "title".equals(change) ? "新的会议材料" : task.title(), "notes".equals(change) ? "修改了备注" : null,
                    PersonalTaskPriority.NORMAL, "due".equals(change) ? Instant.now().plusSeconds(600) : null, "Asia/Shanghai"));
            case "completed" -> tasks.complete(task.id());
            case "delete" -> tasks.delete(task.id());
        }
        var result = ui.act(f.device, id, "CONFIRM");
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(proposalRepository.findById(id).orElseThrow().getStatus()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(ui.act(f.device, id, "CONFIRM").status()).isEqualTo("FAILED");
        if (!"delete".equals(change) && !"completed".equals(change))
            assertThat(tasks.get(task.id()).status()).isEqualTo(PersonalTaskStatus.OPEN);
    }

    @Test
    void partnerAndOriginalConversationChangesRetireTheOldScreenScope() {
        var f = fixture();
        UUID id = offer(f, VoiceActionDraft.personalTask("旧伙伴的待办", null, null));
        UUID partner = roles.create(new CompanionRoleService.RoleCommand("新伙伴" + UUID.randomUUID(), PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", "")).id();
        roles.switchActive(f.device, partner);
        assertThatThrownBy(() -> ui.card(f.device, id)).isInstanceOf(DeviceUiException.class);
        roles.switchActive(f.device, CompanionRoleEntity.DEFAULT_ROLE_ID);
        assertThatThrownBy(() -> ui.card(f.device, id)).isInstanceOf(DeviceUiException.class);
        assertThat(proposals.latestPending(f.device, f.conversation)).isNull();
        assertThat(proposalRepository.findById(id).orElseThrow().getStatus()).isEqualTo(VoiceActionStatus.EXPIRED);
        assertThat(proposals.confirm(id, f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.EXPIRED);
        assertThat(tasks.openForAgent(f.device, CompanionRoleEntity.DEFAULT_ROLE_ID)).isEmpty();
        var other = fixture();
        UUID otherId = offer(other, VoiceActionDraft.personalTask("旧会话的待办", null, null));
        // A replacement mapping must not turn old consent into an operation in the new session.
        jdbc.update("delete from device_voice_conversations where device_id=?", other.device);
        conversations.getOrCreateConversationId(other.device);
        assertThatThrownBy(() -> ui.card(other.device, otherId)).isInstanceOf(DeviceUiException.class);
    }

    @Test
    void expiredShownCardReturnsFinalExpiryAndCannotExecuteOrBecomeCancelled() throws Exception {
        var f = fixture(); UUID id = offer(f, VoiceActionDraft.personalTask("过期操作", null, null));
        shown(f, id);
        expire(id);
        http.perform(post(path(id)).header("Authorization", f.authorization).contentType("application/json")
                .content("{\"action\":\"CONFIRM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED"))
                .andExpect(jsonPath("$.expires_in_seconds").value(0));
        assertThat(ui.act(f.device, id, "CANCEL").status()).isEqualTo("EXPIRED");
        assertThat(tasks.openForAgent(f.device, CompanionRoleEntity.DEFAULT_ROLE_ID)).isEmpty();
    }

    @Test
    void reconnectResetsCapabilitiesAndOldReceiptUntilANewOffer() {
        var f = fixture(); UUID id = offer(f, VoiceActionDraft.personalTask("断线的操作", null, null));
        assertThat(registry.acknowledgeConfirmationShown(f.device, id)).isTrue();
        registry.unregister(f.device, f.session);
        var replacement = session(f.device);
        registry.register(f.device, replacement);
        assertThat(registry.supportsDeviceUi(f.device)).isFalse();
        assertThatThrownBy(() -> ui.card(f.device, id)).isInstanceOf(DeviceUiException.class);
        registry.enableDeviceUi(f.device, replacement);
        assertThat(registry.confirmationId(f.device)).isNull();
        assertThat(registry.acknowledgeConfirmationShown(f.device, id)).isFalse();
    }

    @Test
    void concurrentClicksCreateOneTaskAndOneExecutionAudit() throws Exception {
        var f = fixture(); UUID id = offer(f, VoiceActionDraft.personalTask("只能新增一次", null, null));
        shown(f, id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> ui.act(f.device, id, "CONFIRM"));
            var second = executor.submit(() -> ui.act(f.device, id, "CONFIRM"));
            assertThat(first.get(20, TimeUnit.SECONDS).status()).isEqualTo("EXECUTED");
            assertThat(second.get(20, TimeUnit.SECONDS).status()).isEqualTo("EXECUTED");
        }
        assertThat(tasks.openForAgent(f.device, CompanionRoleEntity.DEFAULT_ROLE_ID)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from voice_action_audits where proposal_id=? and event_type='EXECUTED'",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void committedQuietAndSettingsChangesNotifyOnlyNegotiatedConnectionsAndNeverRollback() throws Exception {
        var f = fixture();
        clearInvocations(f.session);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            quiet.quietForToday(f.device);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> verify(f.session, never()).sendMessage(any()));
            status.setRollbackOnly();
        });
        assertThat(quiet.isQuiet(f.device, Instant.now())).isFalse();
        verify(f.session, never()).sendMessage(any());
        quiet.quietForToday(f.device);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        quiet.quietForToday(f.device);
        verify(f.session, never()).sendMessage(any());
        quiet.resume(f.device);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        settings.setVolume(f.device, 73);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        settings.setNightMode(f.device, true);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        settings.setNightMode(f.device, true);
        ui.state(f.device);
        verify(f.session, never()).sendMessage(any());

        registry.unregister(f.device, f.session);
        var legacy = session(f.device);
        registry.register(f.device, legacy);
        quiet.quietForToday(f.device);
        settings.setVolume(f.device, 70);
        verify(legacy, never()).sendMessage(any());
    }

    @Test
    void workdayStateTransitionsNotifyWithoutEmittingOnOrdinaryTimerTicks() throws Exception {
        var f = fixture();
        workdaySettings.save(f.device, new com.kj.stackchan.workday.WorkdaySettingsService.UpdateWorkdaySettingsCommand(
                true, 127, java.time.LocalTime.MIDNIGHT, java.time.LocalTime.of(23, 59, 59),
                15, 5, 1, 10, "测试", null, null, "UTC"));
        clearInvocations(f.session);
        workdayRuntime.start(f.device, true);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        workdayRuntime.tick(f.device);
        workdayRuntime.tick(f.device);
        verify(f.session, never()).sendMessage(any());
        jdbc.update("update device_workday_runtime set focus_seconds=900 where device_id=?", f.device);
        workdayRuntime.tick(f.device);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        workdayRuntime.respondToRest(f.device, com.kj.stackchan.workday.WorkdayRestAction.START_REST);
        verifyStateNotification(f.session);
        clearInvocations(f.session);
        workdayRuntime.stop(f.device);
        verifyStateNotification(f.session);
    }

    private void verifyStateNotification(WebSocketSession session) throws Exception {
        var messages = org.mockito.ArgumentCaptor.forClass(org.springframework.web.socket.TextMessage.class);
        verify(session).sendMessage(messages.capture());
        var payload = mapper.readTree(messages.getValue().getPayload());
        assertThat(payload.size()).isEqualTo(1);
        assertThat(payload.path("type").asText()).isEqualTo("device_ui_state_changed");
    }

    @Test
    void volumeAndNightSettingsMergeUnderDeviceLockAndReturnTheServerState() throws Exception {
        var f = fixture();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> ui.setting(f.device, "volume_percent", 65));
            var second = executor.submit(() -> ui.setting(f.device, "night_mode", true));
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        }
        assertThat(settings.resolve(f.device).volumePercent()).isEqualTo(65);
        assertThat(settings.resolve(f.device).nightMode()).isTrue();
        http.perform(get("/api/v1/device/ui/state").header("Authorization", f.authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.volume_percent").value(65))
                .andExpect(jsonPath("$.night_mode").value(true)).andExpect(header().string("Cache-Control", "no-store"));
        http.perform(post("/api/v1/device/ui/settings").header("Authorization", f.authorization)
                .contentType("application/json").content("{\"quiet_today\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("SAVED"))
                .andExpect(jsonPath("$.state.quiet_today").value(true));
    }

    @Test
    void menuRejectsCalibrationCredentialsAndMixedOrUnknownFields() throws Exception {
        var f = fixture();
        for (String body : new String[]{"{\"servo_enabled\":true}", "{\"credential\":\"ignored\"}",
                "{\"volume_percent\":20,\"night_mode\":true}", "{\"volume_percent\":101}",
                "{\"volume_percent\":30,\"volume_percent\":60}", "{\"role_id\":\"1-1-1-1-1\"}"}) {
            http.perform(post("/api/v1/device/ui/settings").header("Authorization", f.authorization)
                    .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        }
        assertThat(settings.resolve(f.device).volumePercent()).isEqualTo(50);
    }

    @Test
    void shortSpeechRequiresReceiptAndAudioCancellationLeavesCardPending() throws Exception {
        var f = fixture(); UUID id = proposals.propose(f.device, f.conversation, UUID.randomUUID(),
                VoiceActionDraft.personalTask("可见回执", null, null)).id();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var awaited = executor.submit(() -> ui.presentAndAwait(f.device, f.conversation));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (registry.confirmationId(f.device) == null && System.nanoTime() < deadline) Thread.onSpinWait();
            shown(f, id);
            assertThat(awaited.get(2, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(ui.pendingVisible(f.device)).isTrue();
        assertThat(registry.sendStopAudio(f.device)).isTrue();
        assertThat(proposalRepository.findById(id).orElseThrow().getStatus()).isEqualTo(VoiceActionStatus.PENDING);
        assertThat(ui.act(f.device, id, "CANCEL").status()).isEqualTo("CANCELLED");
        assertThat(ui.pendingVisible(f.device)).isFalse();
    }

    @Test
    void roleSwitchPinsAnIdAndRejectsAmbiguousOrRenamedTargets() {
        var f = fixture();
        String name = "固定伙伴" + UUID.randomUUID();
        var target = createRole(name);
        UUID id = offer(f, new VoiceActionDraft(VoiceActionType.SWITCH_ROLE, true, name, null,
                null, null, null, null, null, null, null, null, null));
        assertThat(proposalRepository.findById(id).orElseThrow().getTargetReference()).isEqualTo(target.id());
        createRole(name);
        registry.acknowledgeConfirmationShown(f.device, id);
        assertThat(ui.act(f.device, id, "CONFIRM").status()).isEqualTo("EXECUTED");
        assertThat(roles.getActive(f.device).id()).isEqualTo(target.id());
        assertThat(ui.act(f.device, id, "CONFIRM").status()).isEqualTo("EXECUTED");
        assertThatThrownBy(() -> roles.resolveUniqueName(name)).isInstanceOf(RoleConflictException.class);

        var other = fixture(); String secondName = "会改名伙伴" + UUID.randomUUID();
        var second = createRole(secondName);
        UUID old = offer(other, new VoiceActionDraft(VoiceActionType.SWITCH_ROLE, true, secondName, null,
                null, null, null, null, null, null, null, null, null));
        roles.update(second.id(), new CompanionRoleService.RoleCommand("新名字" + UUID.randomUUID(), PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", ""));
        registry.acknowledgeConfirmationShown(other.device, old);
        assertThat(ui.act(other.device, old, "CONFIRM").status()).isEqualTo("FAILED");
        assertThat(roles.getActive(other.device).id()).isEqualTo(CompanionRoleEntity.DEFAULT_ROLE_ID);
    }

    @Test
    void roleConsentExcludesOnlyItsOwnTurnAndKeepsOtherVoiceAndReminderBusyGates() {
        var f = fixture(); var target = createRole("自身回合" + UUID.randomUUID());
        UUID source = UUID.randomUUID();
        diagnostics.recordServerStage(f.device, source, com.kj.stackchan.speech.VoiceTurnStage.REQUEST_RECEIVED, null);
        var proposal = proposals.propose(f.device, f.conversation, source, roleDraft(target.name()));
        assertThat(proposals.confirm(proposal.id(), f.device, f.conversation).status()).isEqualTo(VoiceActionStatus.EXECUTED);
        assertThat(roles.getActive(f.device).id()).isEqualTo(target.id());

        var other = fixture(); var busyTarget = createRole("其他回合" + UUID.randomUUID());
        UUID source2 = UUID.randomUUID(); UUID unrelated = UUID.randomUUID();
        diagnostics.recordServerStage(other.device, source2, com.kj.stackchan.speech.VoiceTurnStage.REQUEST_RECEIVED, null);
        diagnostics.recordServerStage(other.device, unrelated, com.kj.stackchan.speech.VoiceTurnStage.REQUEST_RECEIVED, null);
        var blocked = proposals.propose(other.device, other.conversation, source2, roleDraft(busyTarget.name()));
        assertThat(proposals.confirm(blocked.id(), other.device, other.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(roles.getActive(other.device).id()).isEqualTo(CompanionRoleEntity.DEFAULT_ROLE_ID);

        var reminderBusy = fixture(); var reminderTarget = createRole("提醒忙碌" + UUID.randomUUID());
        var blockedReminder = proposals.propose(reminderBusy.device, reminderBusy.conversation, UUID.randomUUID(), roleDraft(reminderTarget.name()));
        var reminder = reminders.create(new com.kj.stackchan.reminder.ReminderService.ReminderCommand(reminderBusy.device,
                "忙碌的提醒", Instant.now().plusSeconds(600), "Asia/Shanghai", com.kj.stackchan.reminder.ReminderRecurrence.NONE, 1));
        jdbc.update("update reminders set status='DISPATCHED' where id=?", reminder.id());
        assertThat(proposals.confirm(blockedReminder.id(), reminderBusy.device, reminderBusy.conversation).status()).isEqualTo(VoiceActionStatus.FAILED);
        assertThat(roles.getActive(reminderBusy.device).id()).isEqualTo(CompanionRoleEntity.DEFAULT_ROLE_ID);
    }

    private CompanionRoleService.RoleSnapshot createRole(String name) {
        return roles.create(new CompanionRoleService.RoleCommand(name, PersonaTone.CALM,
                PersonaReplyLength.SHORT, PersonaProactivity.RESERVED, "", "", ""));
    }
    private VoiceActionDraft roleDraft(String name) {
        return new VoiceActionDraft(VoiceActionType.SWITCH_ROLE, true, name, null, null, null,
                null, null, null, null, null, null, null);
    }

    private UUID offer(Fixture f, VoiceActionDraft draft) {
        UUID id = proposals.propose(f.device, f.conversation, UUID.randomUUID(), draft).id();
        registry.offerConfirmation(f.device, id);
        return id;
    }
    private void shown(Fixture f, UUID id) throws Exception {
        http.perform(post(path(id) + "/shown").header("Authorization", f.authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SHOWN"));
    }
    private void expire(UUID id) {
        Instant now = Instant.now();
        jdbc.update("update voice_action_proposals set created_at=?,expires_at=? where id=?",
                Timestamp.from(now.minusSeconds(180)), Timestamp.from(now.minusSeconds(60)), id);
    }
    private Fixture fixture() {
        var device = new DeviceEntity("device-ui-" + UUID.randomUUID(), "test");
        device.rotateCredentials("a".repeat(64), Instant.now());
        device = devices.saveAndFlush(device);
        var session = session(device.getId());
        registry.register(device.getId(), session);
        registry.enableDeviceUi(device.getId(), session);
        return new Fixture(device.getId(), conversations.getOrCreateConversationId(device.getId()),
                "Bearer " + tokens.issue(device).value(), session);
    }
    private WebSocketSession session(UUID deviceId) {
        var session = mock(WebSocketSession.class);
        var attributes = new HashMap<String, Object>();
        attributes.put(DeviceWebSocketHandshakeInterceptor.DEVICE_ID_ATTRIBUTE, deviceId);
        attributes.put(DeviceWebSocketHandshakeInterceptor.CREDENTIAL_VERSION_ATTRIBUTE, 1L);
        attributes.put(DeviceWebSocketHandshakeInterceptor.TOKEN_EXPIRES_AT_ATTRIBUTE, Instant.now().plusSeconds(3600));
        when(session.getAttributes()).thenReturn(attributes); when(session.isOpen()).thenReturn(true);
        return session;
    }
    private static String path(UUID id) { return "/api/v1/device/ui/confirmations/" + id; }
    private record Fixture(UUID device, UUID conversation, String authorization, WebSocketSession session) {}
}
