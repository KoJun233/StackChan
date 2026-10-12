package com.kj.stackchan.speech;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.kj.stackchan.conversation.PersonalDataConflictException;
import com.kj.stackchan.conversation.PersonalDataNotFoundException;
import com.kj.stackchan.conversation.PersonalDataService;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.SecretCipher;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R01 migration and privacy evidence using synthetic conversations in an isolated PostgreSQL container. */
@SpringBootTest
@Testcontainers
class VoiceQualityPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    private static final Instant CREATED = Instant.parse("2026-10-13T06:00:00Z");
    private static final List<String> UPGRADE_TABLES = List.of("devices", "companion_roles", "conversations",
            "device_voice_conversations", "voice_turns", "voice_turn_events", "bark_notification_attempts",
            "llm_provider_settings", "speech_provider_settings", "agent_mcp_connections",
            "icloud_calendar_connections", "delegation_push_settings");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired VoiceTurnDiagnosticsService diagnostics;
    @Autowired PersonalDataService personalData;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void populatedV60IncludingV57UpgradesWithoutReattributingLegacyTurnsOrChangingReceipts() {
        var isolated = isolatedSchema();
        assertThat(isolated.configuration().target("60").load().migrate().migrationsExecuted).isEqualTo(60);
        var fixture = scope(isolated.jdbc(), isolated.prefix());
        var cipher = seedEncryptedSettings(isolated.jdbc(), isolated.prefix(), fixture.device());
        assertEncryptedSettings(isolated.jdbc(), isolated.prefix(), cipher);
        insertLegacyTurn(isolated.jdbc(), isolated.prefix(), fixture.device(), "COMPLETED", null);
        insertLegacyTurn(isolated.jdbc(), isolated.prefix(), fixture.otherDevice(), "CANCELLED", null);
        insertLegacyTurn(isolated.jdbc(), isolated.prefix(), fixture.device(), "FAILED", "ASR_UNAVAILABLE");
        for (String status : List.of("SENT", "UNKNOWN")) {
            isolated.jdbc().update("insert into " + isolated.prefix() + "bark_notification_attempts(operation_key,"
                            + "content_sha256,receipt_id,status,error_code,started_at,completed_at) values(?,?,?,?,?,?,?)",
                    (status.equals("SENT") ? "a" : "b").repeat(64), "c".repeat(64), UUID.randomUUID(), status,
                    status.equals("UNKNOWN") ? "DELIVERY_UNKNOWN" : null, time(CREATED), time(CREATED.plusSeconds(1)));
        }
        var original = capture(isolated.jdbc(), isolated.prefix(), UPGRADE_TABLES);
        var checksums = history(isolated.jdbc(), isolated.prefix());
        assertThat(checksums).hasSize(60);
        assertThat(isolated.jdbc().queryForObject("select count(*) from " + isolated.prefix()
                + "flyway_schema_history where version='57' and success", Long.class)).isEqualTo(1);

        var candidate = isolated.configuration().target("61").load();
        assertThat(candidate.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(candidate.info().current().getVersion().toString()).isEqualTo("61");
        assertPreserved(isolated.jdbc(), isolated.prefix(), original);
        assertEncryptedSettings(isolated.jdbc(), isolated.prefix(), cipher);
        assertThat(history(isolated.jdbc(), isolated.prefix()).subList(0, 60)).isEqualTo(checksums);
        assertThat(isolated.jdbc().queryForObject("select count(*) from " + isolated.prefix()
                + "voice_turns where conversation_id is not null", Long.class)).isZero();
        assertThat(isolated.jdbc().queryForObject("select count(*) from " + isolated.prefix()
                + "voice_turn_privacy_tombstones", Long.class)).isZero();
        assertThat(isolated.jdbc().queryForList("select status from " + isolated.prefix()
                + "bark_notification_attempts order by status", String.class)).containsExactly("SENT", "UNKNOWN");
        assertThat(candidate.validateWithResult().validationSuccessful).isTrue();
        assertThat(candidate.migrate().migrationsExecuted).isZero();
        assertPreserved(isolated.jdbc(), isolated.prefix(), original);
        assertEncryptedSettings(isolated.jdbc(), isolated.prefix(), cipher);
    }

    @Test
    void freshSchemaAppliesAllSixtyOneMigrationsWithConversationCascadeAndIndex() {
        var isolated = isolatedSchema();
        var candidate = isolated.configuration().target("61").load();
        assertThat(candidate.migrate().migrationsExecuted).isEqualTo(61);
        assertThat(history(isolated.jdbc(), isolated.prefix())).hasSize(61);
        assertThat(isolated.jdbc().queryForObject("select is_nullable from information_schema.columns "
                        + "where table_schema=? and table_name='voice_turns' and column_name='conversation_id'",
                String.class, isolated.schema())).isEqualTo("YES");
        assertThat(isolated.jdbc().queryForObject("select count(*) from pg_indexes where schemaname=? "
                        + "and tablename='voice_turns' and indexname='idx_voice_turns_conversation'",
                Long.class, isolated.schema())).isEqualTo(1);
        var fixture = scope(isolated.jdbc(), isolated.prefix());
        UUID bound = insertLegacyTurn(isolated.jdbc(), isolated.prefix(), fixture.device(), "COMPLETED", null);
        UUID untouched = insertLegacyTurn(isolated.jdbc(), isolated.prefix(), fixture.otherDevice(), "COMPLETED", null);
        isolated.jdbc().update("update " + isolated.prefix() + "voice_turns set conversation_id=? where id=?",
                fixture.conversation(), bound);
        isolated.jdbc().update("delete from " + isolated.prefix() + "conversations where id=?", fixture.conversation());
        assertTurnAbsent(isolated.jdbc(), isolated.prefix(), bound);
        assertTurnPresent(isolated.jdbc(), isolated.prefix(), untouched);
        assertThat(candidate.validateWithResult().validationSuccessful).isTrue();
        assertThat(candidate.migrate().migrationsExecuted).isZero();
    }

    @Test
    void deletingConversationRemovesOnlyItsBoundTurnsAndEventsWhileOtherScopesAndLegacySurvive() {
        var fixture = scope(jdbc, "");
        UUID first = boundTurn(fixture.device(), fixture.conversation());
        UUID second = boundTurn(fixture.device(), fixture.conversation());
        UUID otherRole = boundTurn(fixture.device(), fixture.otherRoleConversation());
        UUID otherDevice = boundTurn(fixture.otherDevice(), fixture.otherDeviceConversation());
        UUID legacy = insertLegacyTurn(jdbc, "", fixture.device(), "COMPLETED", null);
        var preserved = selectedTurnsAndEvents(List.of(otherRole, otherDevice, legacy));

        personalData.deleteConversation(fixture.conversation());

        assertTurnAbsent(jdbc, "", first);
        assertTurnAbsent(jdbc, "", second);
        assertThat(jdbc.queryForObject("select count(*) from conversations where id=?", Long.class, fixture.conversation())).isZero();
        assertThat(selectedTurnsAndEvents(List.of(otherRole, otherDevice, legacy))).isEqualTo(preserved);
        assertThat(jdbc.queryForObject("select count(*) from conversations where id in (?,?)", Long.class,
                fixture.otherRoleConversation(), fixture.otherDeviceConversation())).isEqualTo(2);
    }

    @Test
    void deletingOneSourceMessageClearsAllConversationTimingButRetainsRemainingMessagesAndOtherScopes() {
        var fixture = scope(jdbc, "");
        UUID message = insertMessage(jdbc, "", fixture.conversation(), "COMPLETED");
        UUID remaining = insertMessage(jdbc, "", fixture.conversation(), "COMPLETED");
        UUID first = boundTurn(fixture.device(), fixture.conversation());
        UUID second = boundTurn(fixture.device(), fixture.conversation());
        UUID otherRole = boundTurn(fixture.device(), fixture.otherRoleConversation());
        UUID otherDevice = boundTurn(fixture.otherDevice(), fixture.otherDeviceConversation());
        UUID legacy = insertLegacyTurn(jdbc, "", fixture.device(), "COMPLETED", null);
        var preserved = selectedTurnsAndEvents(List.of(otherRole, otherDevice, legacy));

        personalData.deleteMessage(fixture.conversation(), message);

        assertTurnAbsent(jdbc, "", first);
        assertTurnAbsent(jdbc, "", second);
        assertThat(jdbc.queryForObject("select count(*) from conversations where id=?", Long.class, fixture.conversation())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from conversation_messages where id=?", Long.class, message)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from conversation_messages where id=?", Long.class, remaining)).isEqualTo(1);
        assertThat(selectedTurnsAndEvents(List.of(otherRole, otherDevice, legacy))).isEqualTo(preserved);
    }

    @Test
    void duplicateBindingsAndDeviceStagesAreIdempotentWhileCrossDeviceAndRebindAttemptsCannotMutateData() {
        var fixture = scope(jdbc, "");
        UUID turn = boundTurn(fixture.device(), fixture.conversation());
        var original = selectedTurnsAndEvents(List.of(turn));

        diagnostics.bindConversation(fixture.device(), turn, fixture.conversation());
        diagnostics.recordDeviceStage(fixture.device(), turn, VoiceTurnStage.SPEECH_CAPTURED, 9999, null,
                "DEVICE_MONOTONIC", 999, true);
        assertThat(selectedTurnsAndEvents(List.of(turn))).isEqualTo(original);
        assertThatThrownBy(() -> diagnostics.bindConversation(fixture.otherDevice(), turn, fixture.otherDeviceConversation()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> diagnostics.bindConversation(fixture.device(), turn, fixture.otherRoleConversation()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> diagnostics.recordDeviceStage(fixture.otherDevice(), turn,
                VoiceTurnStage.PLAYBACK_STARTED, 1600, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(selectedTurnsAndEvents(List.of(turn))).isEqualTo(original);
    }

    @Test
    void newTurnCannotBindToAnotherDevicesConversationOrUnboundWebConversation() {
        var fixture = scope(jdbc, "");
        UUID otherDeviceTurn = UUID.randomUUID();
        UUID webConversation = insertConversation(jdbc, "", fixture.role());
        UUID webTurn = UUID.randomUUID();
        assertThatThrownBy(() -> diagnostics.bindConversation(fixture.device(), otherDeviceTurn, fixture.otherDeviceConversation()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> diagnostics.bindConversation(fixture.device(), webTurn, webConversation))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from voice_turns where id in (?,?)", Long.class,
                otherDeviceTurn, webTurn)).isZero();
    }

    @Test
    void failedStreamingAndWrongConversationMessageDeletionKeepsAllTimingIntact() {
        var fixture = scope(jdbc, "");
        UUID streaming = insertMessage(jdbc, "", fixture.conversation(), "STREAMING");
        UUID foreignMessage = insertMessage(jdbc, "", fixture.otherRoleConversation(), "COMPLETED");
        UUID turn = boundTurn(fixture.device(), fixture.conversation());
        UUID foreignTurn = boundTurn(fixture.device(), fixture.otherRoleConversation());
        var before = selectedTurnsAndEvents(List.of(turn, foreignTurn));

        assertThatThrownBy(() -> personalData.deleteMessage(fixture.conversation(), streaming))
                .isInstanceOf(PersonalDataConflictException.class);
        assertThatThrownBy(() -> personalData.deleteConversation(fixture.conversation()))
                .isInstanceOf(PersonalDataConflictException.class);
        assertThatThrownBy(() -> personalData.deleteMessage(fixture.conversation(), foreignMessage))
                .isInstanceOf(PersonalDataNotFoundException.class);
        assertThat(selectedTurnsAndEvents(List.of(turn, foreignTurn))).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from conversation_messages where id in (?,?)", Long.class,
                streaming, foreignMessage)).isEqualTo(2);
    }

    @Test
    void erasedTurnsRejectRebindAndIgnoreLateDeviceAndServerStagesWithoutExtendingExpiry() {
        var fixture = scope(jdbc, "");
        UUID message = insertMessage(jdbc, "", fixture.conversation(), "COMPLETED");
        UUID remaining = insertMessage(jdbc, "", fixture.conversation(), "COMPLETED");
        UUID turn = boundTurn(fixture.device(), fixture.conversation());
        Instant beforeDelete = clock.instant();
        personalData.deleteMessage(fixture.conversation(), message);
        Instant afterDelete = clock.instant();
        Instant expiry = tombstoneExpiry(turn);
        assertThat(expiry).isBetween(beforeDelete.plus(VoiceTurnDiagnosticsService.RETENTION),
                afterDelete.plus(VoiceTurnDiagnosticsService.RETENTION));

        diagnostics.recordDeviceStage(fixture.device(), turn, VoiceTurnStage.PLAYBACK_STARTED, 1600, null);
        diagnostics.recordServerStage(fixture.device(), turn, VoiceTurnStage.TTS_COMPLETED, null, 420, "synthetic_tts");
        assertThatThrownBy(() -> diagnostics.bindConversation(fixture.device(), turn, fixture.conversation()))
                .isInstanceOf(IllegalArgumentException.class);
        assertTurnAbsent(jdbc, "", turn);
        assertThat(tombstoneExpiry(turn)).isEqualTo(expiry);

        personalData.deleteMessage(fixture.conversation(), remaining);
        assertThat(tombstoneExpiry(turn)).isEqualTo(expiry);
        assertThat(jdbc.queryForObject("select count(*) from voice_turn_privacy_tombstones where turn_id=?",
                Long.class, turn)).isEqualTo(1);
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_schema='public' "
                + "and table_name='voice_turn_privacy_tombstones' order by ordinal_position", String.class))
                .containsExactly("turn_id", "expires_at");
    }

    @Test
    void expiryCleanupRetainsLivePrivacyMarkersAndRecentUnboundLegacyWhileRemovingExpiredData() {
        var fixture = scope(jdbc, "");
        UUID message = insertMessage(jdbc, "", fixture.conversation(), "COMPLETED");
        UUID erased = boundTurn(fixture.device(), fixture.conversation());
        personalData.deleteMessage(fixture.conversation(), message);
        Instant expiry = tombstoneExpiry(erased);
        UUID expiredMarker = UUID.randomUUID();
        jdbc.update("insert into voice_turn_privacy_tombstones(turn_id,expires_at) values(?,?)",
                expiredMarker, time(clock.instant().minusSeconds(1)));
        UUID recentLegacy = insertLegacyTurn(jdbc, "", fixture.device(), "COMPLETED", null);
        UUID oldLegacy = insertLegacyTurn(jdbc, "", fixture.device(), "COMPLETED", null);
        Instant old = clock.instant().minus(VoiceTurnDiagnosticsService.RETENTION).minusSeconds(1);
        jdbc.update("update voice_turns set started_at=?,updated_at=? where id=?", time(old), time(old), oldLegacy);
        var retained = selectedTurnsAndEvents(List.of(recentLegacy));

        diagnostics.deleteExpired();

        assertTurnAbsent(jdbc, "", oldLegacy);
        assertThat(jdbc.queryForObject("select count(*) from voice_turn_privacy_tombstones where turn_id=?",
                Long.class, expiredMarker)).isZero();
        assertThat(tombstoneExpiry(erased)).isEqualTo(expiry);
        assertThat(selectedTurnsAndEvents(List.of(recentLegacy))).isEqualTo(retained);
        assertTurnAbsent(jdbc, "", erased);
    }

    @Test
    void pendingPrivacyDeletionWaitsForInFlightStageAndLateEventsCannotRecreateItsTurn() throws Exception {
        var fixture = scope(jdbc, "");
        UUID message = insertMessage(jdbc, "", fixture.conversation(), "COMPLETED");
        UUID turn = boundTurn(fixture.device(), fixture.conversation());
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var writerPid = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var writer = executor.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                writerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                jdbc.queryForObject("select cast(pg_advisory_xact_lock(hashtextextended(cast(? as text),0)) as text)",
                        String.class, turn.toString());
                diagnostics.recordServerStage(fixture.device(), turn, VoiceTurnStage.LLM_COMPLETED, null, 88, "synthetic_llm");
                held.countDown();
                try {
                    if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test transaction barrier timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CompletionException(exception);
                }
                return null;
            }));
            try {
                assertThat(held.await(20, TimeUnit.SECONDS)).isTrue();
                var deletion = executor.submit(() -> personalData.deleteMessage(fixture.conversation(), message));
                awaitAdvisoryWaitOn(writerPid.get());
                assertThat(deletion.isDone()).isFalse();
                release.countDown();
                writer.get(20, TimeUnit.SECONDS);
                deletion.get(20, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertTurnAbsent(jdbc, "", turn);
        assertThat(tombstoneExpiry(turn)).isAfter(clock.instant());
        diagnostics.recordServerStage(fixture.device(), turn, VoiceTurnStage.TTS_COMPLETED, null, 99, "synthetic_late_tts");
        diagnostics.recordDeviceStage(fixture.device(), turn, VoiceTurnStage.PLAYBACK_STARTED, 2000, null);
        assertTurnAbsent(jdbc, "", turn);
        assertThatThrownBy(() -> diagnostics.bindConversation(fixture.device(), turn, fixture.conversation()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Instant tombstoneExpiry(UUID turn) {
        return jdbc.queryForObject("select expires_at from voice_turn_privacy_tombstones where turn_id=?", Timestamp.class, turn).toInstant();
    }

    private void awaitAdvisoryWaitOn(int blockingPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            Boolean waiting = jdbc.queryForObject("select exists(select 1 from pg_stat_activity where state='active' "
                    + "and wait_event_type='Lock' and query like '%pg_advisory_xact_lock%' "
                    + "and ?=any(pg_blocking_pids(pid)))", Boolean.class, blockingPid);
            if (Boolean.TRUE.equals(waiting)) return;
            Thread.sleep(20);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("Privacy deletion did not wait on the held diagnostic turn lock");
    }

    private UUID boundTurn(UUID device, UUID conversation) {
        UUID turn = UUID.randomUUID();
        diagnostics.bindConversation(device, turn, conversation);
        diagnostics.recordDeviceStage(device, turn, VoiceTurnStage.SPEECH_CAPTURED, 9000, null,
                "DEVICE_MONOTONIC", 37, false);
        diagnostics.recordServerStage(device, turn, VoiceTurnStage.ASR_COMPLETED, null, 123, "synthetic_asr");
        return turn;
    }

    private Map<String, List<Map<String, Object>>> selectedTurnsAndEvents(List<UUID> ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        return Map.of("turns", jdbc.queryForList("select * from voice_turns where id in (" + placeholders + ") order by id", ids.toArray()),
                "events", jdbc.queryForList("select * from voice_turn_events where turn_id in (" + placeholders + ") order by id", ids.toArray()));
    }

    private static Schema isolatedSchema() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.execute("create extension if not exists pg_trgm with schema public");
        return new Schema("voice_quality_" + UUID.randomUUID().toString().replace("-", ""), jdbc);
    }

    private static Scope scope(JdbcTemplate jdbc, String prefix) {
        UUID device = insertDevice(jdbc, prefix);
        UUID otherDevice = insertDevice(jdbc, prefix);
        UUID role = insertRole(jdbc, prefix);
        UUID otherRole = insertRole(jdbc, prefix);
        UUID conversation = insertConversation(jdbc, prefix, role);
        UUID otherRoleConversation = insertConversation(jdbc, prefix, otherRole);
        UUID otherDeviceConversation = insertConversation(jdbc, prefix, role);
        jdbc.update("insert into " + prefix + "device_voice_conversations(device_id,role_id,conversation_id) values(?,?,?),(?,?,?),(?,?,?)",
                device, role, conversation, device, otherRole, otherRoleConversation, otherDevice, role, otherDeviceConversation);
        return new Scope(device, otherDevice, role, conversation, otherRoleConversation, otherDeviceConversation);
    }

    private static UUID insertDevice(JdbcTemplate jdbc, String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + prefix + "devices(id,hardware_id,firmware_version,display_name,credential_version) "
                + "values(?,?,'synthetic-r01','合成语音设备',7)", id, "synthetic-r01-" + id);
        return id;
    }

    private static UUID insertRole(JdbcTemplate jdbc, String prefix) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + prefix + "companion_roles(id,name,tone,reply_length,proactivity,created_at,updated_at) "
                + "values(?,'合成伙伴','CALM','SHORT','RESERVED',?,?)", id, time(CREATED), time(CREATED));
        return id;
    }

    private static UUID insertConversation(JdbcTemplate jdbc, String prefix, UUID role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + prefix + "conversations(id,title,role_id,created_at,updated_at) values(?,'合成语音对话',?,?,?)",
                id, role, time(CREATED), time(CREATED));
        return id;
    }

    private static UUID insertMessage(JdbcTemplate jdbc, String prefix, UUID conversation, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + prefix + "conversation_messages(id,conversation_id,role,content,generation_status,created_at,completed_at) "
                        + "values(?,?,?,'合成夹具：来源消息',?,?,?)", id, conversation,
                status.equals("STREAMING") ? "ASSISTANT" : "USER", status,
                time(CREATED), status.equals("STREAMING") ? null : time(CREATED.plusSeconds(1)));
        return id;
    }

    private static UUID insertLegacyTurn(JdbcTemplate jdbc, String prefix, UUID device, String status, String failureCode) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + prefix + "voice_turns(id,device_id,status,failure_code,started_at,updated_at) values(?,?,?,?,?,?)",
                id, device, status, failureCode, time(CREATED), time(CREATED.plusSeconds(20)));
        jdbc.update("insert into " + prefix + "voice_turn_events(id,turn_id,stage,source,occurred_at,elapsed_ms,duration_ms,diagnostic_code) "
                        + "values(?,?,'SPEECH_CAPTURED','DEVICE',?,9000,37,'capture_endpoint')",
                UUID.randomUUID(), id, time(CREATED.plusSeconds(9)));
        jdbc.update("insert into " + prefix + "voice_turn_events(id,turn_id,stage,source,occurred_at,duration_ms,diagnostic_code,failure_code) "
                        + "values(?,?,?,'SERVER',?,123,'synthetic_phase',?)", UUID.randomUUID(), id,
                status.equals("FAILED") ? "FAILED" : "ASR_COMPLETED", time(CREATED.plusSeconds(10)), failureCode);
        return id;
    }

    private static void assertTurnAbsent(JdbcTemplate jdbc, String prefix, UUID id) {
        assertThat(jdbc.queryForObject("select count(*) from " + prefix + "voice_turns where id=?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from " + prefix + "voice_turn_events where turn_id=?", Long.class, id)).isZero();
    }

    private static void assertTurnPresent(JdbcTemplate jdbc, String prefix, UUID id) {
        assertThat(jdbc.queryForObject("select count(*) from " + prefix + "voice_turns where id=?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from " + prefix + "voice_turn_events where turn_id=?", Long.class, id)).isEqualTo(2);
    }

    private static Map<String, Snapshot> capture(JdbcTemplate jdbc, String prefix, List<String> tables) {
        String schema = prefix.substring(0, prefix.length() - 1);
        var result = new LinkedHashMap<String, Snapshot>();
        for (String table : tables) {
            var columns = jdbc.queryForList("select column_name from information_schema.columns "
                    + "where table_schema=? and table_name=? order by ordinal_position", String.class, schema, table);
            assertThat(columns).isNotEmpty();
            result.put(table, new Snapshot(columns, rows(jdbc, prefix, table, columns)));
        }
        return result;
    }

    private static SecretCipher seedEncryptedSettings(JdbcTemplate jdbc, String prefix, UUID device) {
        var properties = new AppProperties();
        properties.setSecretsEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var cipher = new SecretCipher(properties);
        var llm = cipher.encrypt(syntheticSecret("llm"));
        jdbc.update("insert into " + prefix + "llm_provider_settings(id,base_url,model,api_key_ciphertext,api_key_iv,"
                        + "updated_at) values(1,'https://example.com/v1','synthetic-model',?,?,?)",
                llm.ciphertext(), llm.initializationVector(), time(CREATED));
        var speech = cipher.encrypt(syntheticSecret("speech"));
        jdbc.update("insert into " + prefix + "speech_provider_settings(id,base_url,asr_model,tts_model,tts_voice,"
                        + "api_key_ciphertext,api_key_iv,updated_at) values(1,'https://example.com/v1','synthetic-asr',"
                        + "'synthetic-tts','synthetic-voice',?,?,?)",
                speech.ciphertext(), speech.initializationVector(), time(CREATED));
        var mcp = cipher.encrypt(syntheticSecret("mcp"));
        jdbc.update("insert into " + prefix + "agent_mcp_connections(id,connection_name,url,endpoint,auth_type,"
                        + "bearer_token_ciphertext,bearer_token_iv,created_at,updated_at) values(?,'synthetic-r01',"
                        + "'https://example.com','/mcp','BEARER',?,?,?,?)",
                UUID.randomUUID(), mcp.ciphertext(), mcp.initializationVector(), time(CREATED), time(CREATED));
        var calendar = cipher.encrypt(syntheticSecret("calendar"));
        jdbc.update("insert into " + prefix + "icloud_calendar_connections(device_id,account_email,password_ciphertext,"
                        + "password_iv,status,created_at,updated_at) values(?,'synthetic-r01@example.invalid',?,?,'CONFIGURED',?,?)",
                device, calendar.ciphertext(), calendar.initializationVector(), time(CREATED), time(CREATED));
        var bark = cipher.encrypt(syntheticSecret("bark"));
        jdbc.update("update " + prefix + "delegation_push_settings set key_ciphertext=?,key_iv=?,enabled=true,"
                        + "revision=9,updated_at=? where id=1",
                bark.ciphertext(), bark.initializationVector(), time(CREATED));
        return cipher;
    }

    private static void assertEncryptedSettings(JdbcTemplate jdbc, String prefix, SecretCipher cipher) {
        assertDecryptedDigest(jdbc, prefix, cipher, "llm_provider_settings", "api_key_ciphertext", "api_key_iv", "llm");
        assertDecryptedDigest(jdbc, prefix, cipher, "speech_provider_settings", "api_key_ciphertext", "api_key_iv", "speech");
        assertDecryptedDigest(jdbc, prefix, cipher, "agent_mcp_connections", "bearer_token_ciphertext", "bearer_token_iv", "mcp");
        assertDecryptedDigest(jdbc, prefix, cipher, "icloud_calendar_connections", "password_ciphertext", "password_iv", "calendar");
        assertDecryptedDigest(jdbc, prefix, cipher, "delegation_push_settings", "key_ciphertext", "key_iv", "bark");
    }

    private static void assertDecryptedDigest(JdbcTemplate jdbc, String prefix, SecretCipher cipher, String table,
                                              String ciphertextColumn, String ivColumn, String name) {
        var row = jdbc.queryForMap("select " + ciphertextColumn + "," + ivColumn + " from " + prefix + table);
        var encrypted = new SecretCipher.EncryptedSecret((String) row.get(ciphertextColumn), (String) row.get(ivColumn));
        assertThat(sha256(cipher.decrypt(encrypted))).as("decrypted synthetic %s digest after V61", name)
                .isEqualTo(sha256(syntheticSecret(name)));
    }

    private static String syntheticSecret(String name) {
        return "SYNTHETIC-R01-" + name + "-NOT-A-REAL-CREDENTIAL";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<Map<String, Object>> rows(JdbcTemplate jdbc, String prefix, String table, List<String> columns) {
        String selected = String.join(",", columns);
        return jdbc.queryForList("select " + selected + " from " + prefix + table + " order by " + selected);
    }

    private static void assertPreserved(JdbcTemplate jdbc, String prefix, Map<String, Snapshot> snapshots) {
        snapshots.forEach((table, original) -> assertThat(rows(jdbc, prefix, table, original.columns()))
                .as("all preexisting synthetic fields and records in %s", table).isEqualTo(original.rows()));
    }

    private static List<Map<String, Object>> history(JdbcTemplate jdbc, String prefix) {
        return jdbc.queryForList("select version,script,checksum from " + prefix
                + "flyway_schema_history where success and version is not null order by installed_rank");
    }

    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }

    private record Scope(UUID device, UUID otherDevice, UUID role, UUID conversation, UUID otherRoleConversation,
                         UUID otherDeviceConversation) { }
    private record Snapshot(List<String> columns, List<Map<String, Object>> rows) { }
    private record Schema(String schema, JdbcTemplate jdbc) {
        String prefix() { return schema + "."; }
        FluentConfiguration configuration() {
            return Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema);
        }
    }
}
