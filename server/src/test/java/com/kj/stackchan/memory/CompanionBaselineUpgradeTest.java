package com.kj.stackchan.memory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.SecretCipher;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R00 compatibility evidence from synthetic data in an isolated PostgreSQL container.
 * This is neither a production-backup restore nor permission to migrate a running database.
 * All fixture credentials are synthetic; assertions compare decrypted digests, never plaintext.
 */
@Testcontainers
class CompanionBaselineUpgradeTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    private static final Instant CREATED = Instant.parse("2026-10-05T00:00:00Z");
    private static final List<String> CORE_TABLES = List.of(
            "admin_users", "devices", "companion_roles", "device_active_roles", "device_interaction_settings",
            "conversations", "conversation_messages", "device_voice_conversations", "personal_tasks",
            "long_term_memories", "memory_usage_records", "llm_provider_settings", "speech_provider_settings",
            "agent_mcp_connections", "icloud_calendar_connections");
    private static final List<String> DELEGATION_TABLES = List.of(
            "delegations", "delegation_runs", "delegation_push_settings");

    @Test
    void freshDatabaseAppliesAllSixtyMigrationsAndRestartKeepsOrdinaryMemoriesUnlicensed() {
        var database = database();
        var candidate = database.configuration().load();
        assertThat(candidate.migrate().migrationsExecuted).isEqualTo(60);
        assertThat(candidate.info().current().getVersion().toString()).isEqualTo("60");
        assertThat(history(database)).hasSize(60);
        assertThat(database.jdbc().queryForObject("select count(*) from " + database.schema()
                + ".continuation_daily_budget", Long.class)).isZero();

        var fixture = seedCore(database);
        assertOrdinaryMemoriesUnlicensed(database, fixture);
        var before = snapshot(database, CORE_TABLES);
        var checksums = history(database);

        var restart = database.configuration().load();
        assertThat(restart.validateWithResult().validationSuccessful).isTrue();
        assertThat(restart.migrate().migrationsExecuted).isZero();
        assertUnchanged(database, before);
        assertThat(history(database)).isEqualTo(checksums);
        assertOrdinaryMemoriesUnlicensed(database, fixture);
        assertEncryptedSettings(database, fixture, false);
    }

    @Test
    void lawfulV56ToV57ToV60UpgradeRetainsIdentityFactsContinuationBudgetAndDelegationReceipts() {
        var database = database();
        assertThat(database.configuration().target("56").load().migrate().migrationsExecuted).isEqualTo(56);
        var fixture = seedCore(database);
        var v56Data = snapshot(database, CORE_TABLES);
        var v56Checksums = history(database);

        assertThat(database.configuration().target("57").load().migrate().migrationsExecuted).isEqualTo(1);
        assertUnchanged(database, v56Data);
        assertOldChecksums(database, v56Checksums);
        assertOrdinaryMemoriesUnlicensed(database, fixture);
        assertThat(database.jdbc().queryForObject("select count(*) from " + database.schema()
                + ".continuation_daily_budget", Long.class)).isZero();

        seedContinuation(database, fixture);
        var v57Data = snapshot(database, withTables(CORE_TABLES, "continuation_daily_budget"));
        var v57Checksums = history(database);
        assertThat(database.configuration().target("58").load().migrate().migrationsExecuted).isEqualTo(1);
        seedLegacyDelegations(database, fixture);
        var v58Delegations = snapshot(database, DELEGATION_TABLES);
        var v58Checksums = history(database);

        assertThat(database.configuration().target("60").load().migrate().migrationsExecuted).isEqualTo(2);
        assertUnchanged(database, v57Data);
        assertUnchanged(database, v58Delegations);
        assertOldChecksums(database, v57Checksums);
        assertOldChecksums(database, v58Checksums);
        assertOrdinaryMemoriesUnlicensed(database, fixture);
        assertContinuationStatesAndBudget(database);
        assertLegacyDelegationNormalization(database, fixture);
        assertEncryptedSettings(database, fixture, true);
        assertThat(database.jdbc().queryForObject("select count(*) from " + database.schema()
                + ".bark_notification_attempts", Long.class)).isZero();

        seedBarkReceipts(database);
        var v60Data = snapshot(database, allFixtureTables());
        var v60Checksums = history(database);
        var restart = database.configuration().load();
        assertThat(restart.validateWithResult().validationSuccessful).isTrue();
        assertThat(restart.migrate().migrationsExecuted).isZero();
        assertUnchanged(database, v60Data);
        assertThat(history(database)).isEqualTo(v60Checksums);
        assertTerminalReceipts(database, fixture);
    }

    @Test
    void lawfulHistoricalV57ThroughV60SyntheticSnapshotValidatesAndStartupMakesNoDataChanges() {
        var database = database();
        database.configuration().target("57").load().migrate();
        var fixture = seedCore(database);
        seedContinuation(database, fixture);
        database.configuration().target("58").load().migrate();
        seedLegacyDelegations(database, fixture);
        database.configuration().target("60").load().migrate();
        seedBarkReceipts(database);
        var historicalData = snapshot(database, allFixtureTables());
        var historicalChecksums = history(database);

        // A separately loaded Flyway instance exercises normal startup validation and migration.
        var startup = database.configuration().load();
        assertThat(startup.validateWithResult().validationSuccessful).isTrue();
        assertThat(startup.migrate().migrationsExecuted).isZero();
        assertThat(startup.info().current().getVersion().toString()).isEqualTo("60");
        assertThat(history(database)).isEqualTo(historicalChecksums);
        assertUnchanged(database, historicalData);
        assertOrdinaryMemoriesUnlicensed(database, fixture);
        assertContinuationStatesAndBudget(database);
        assertLegacyDelegationNormalization(database, fixture);
        assertEncryptedSettings(database, fixture, true);
        assertTerminalReceipts(database, fixture);
    }

    @Test
    void syntheticHistoricalDumpRestoresIntoSeparateDatabaseWithMatchingDataAndDecryptableSettings() throws Exception {
        var source = database();
        source.configuration().target("57").load().migrate();
        var fixture = seedCore(source);
        seedContinuation(source, fixture);
        source.configuration().target("58").load().migrate();
        seedLegacyDelegations(source, fixture);
        source.configuration().target("60").load().migrate();
        seedBarkReceipts(source);
        var before = snapshot(source, allFixtureTables());
        var checksums = history(source);

        String restoreName = "companion_r00_restore_" + UUID.randomUUID().toString().replace("-", "");
        String dumpFile = "/tmp/" + restoreName + ".dump";
        // Native PostgreSQL tools touch only this synthetic schema and a new database in this disposable container.
        // No stderr, plaintext credentials, or dump content is emitted as test evidence.
        assertThat(POSTGRES.execInContainer("pg_dump", "--format=custom", "--schema=" + source.schema(),
                "--file=" + dumpFile, "--username=" + POSTGRES.getUsername(), "--dbname=" + POSTGRES.getDatabaseName())
                .getExitCode()).as("synthetic schema dump exit code").isZero();
        assertThat(POSTGRES.execInContainer("createdb", "--username=" + POSTGRES.getUsername(), restoreName)
                .getExitCode()).as("isolated restore database creation exit code").isZero();
        // Schema-only dumps require the same extension prerequisite as the source database.
        assertThat(POSTGRES.execInContainer("psql", "--username=" + POSTGRES.getUsername(), "--dbname=" + restoreName,
                "--set=ON_ERROR_STOP=1", "--command=CREATE EXTENSION pg_trgm WITH SCHEMA public")
                .getExitCode()).as("isolated restore extension preparation exit code").isZero();
        assertThat(POSTGRES.execInContainer("pg_restore", "--exit-on-error", "--username=" + POSTGRES.getUsername(),
                "--dbname=" + restoreName, dumpFile).getExitCode()).as("synthetic schema restore exit code").isZero();

        String restoredUrl = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + restoreName);
        var restoredJdbc = new JdbcTemplate(new DriverManagerDataSource(
                restoredUrl, POSTGRES.getUsername(), POSTGRES.getPassword()));
        var restored = new Database(source.schema(), restoredJdbc, restoredUrl);
        var restoredStartup = restored.configuration().load();
        assertThat(restoredStartup.validateWithResult().validationSuccessful).isTrue();
        assertThat(restoredStartup.migrate().migrationsExecuted).isZero();
        assertUnchanged(restored, before);
        assertThat(history(restored)).isEqualTo(checksums);
        assertOrdinaryMemoriesUnlicensed(restored, fixture);
        assertContinuationStatesAndBudget(restored);
        assertLegacyDelegationNormalization(restored, fixture);
        assertEncryptedSettings(restored, fixture, true);
        assertTerminalReceipts(restored, fixture);
        assertUnchanged(source, before);
        assertThat(history(source)).isEqualTo(checksums);
    }

    @Test
    void mainlineV60WithoutV57FailsClosedAndKeepsAllDataAndChecksums(@TempDir Path legacyMigrations)
            throws IOException {
        copyMainlineMigrationsWithoutV57(legacyMigrations);
        var database = database();
        String legacyLocation = "filesystem:" + legacyMigrations.toAbsolutePath();
        database.configuration().locations(legacyLocation).target("58").load().migrate();
        var fixture = seedCore(database);
        seedLegacyDelegations(database, fixture);
        database.configuration().locations(legacyLocation).target("60").load().migrate();
        seedBarkReceipts(database);
        var mainlineTables = withTables(CORE_TABLES, "delegations", "delegation_runs", "delegation_push_settings",
                "bark_notification_attempts");
        var oldData = snapshot(database, mainlineTables);
        var oldChecksums = history(database);
        assertThat(oldChecksums).hasSize(59);
        assertThat(database.jdbc().queryForObject("select count(*) from " + database.schema()
                + ".flyway_schema_history where version='57'", Long.class)).isZero();

        // Do not enable out-of-order migration or repair a mainline history lacking V57.
        var candidate = database.configuration().load();
        assertThat(candidate.validateWithResult().validationSuccessful).isFalse();
        assertThatThrownBy(candidate::migrate).isInstanceOf(FlywayValidateException.class);
        assertUnchanged(database, oldData);
        assertThat(history(database)).isEqualTo(oldChecksums);
        assertThat(database.jdbc().queryForObject("select count(*) from information_schema.columns "
                + "where table_schema=? and table_name='long_term_memories' and column_name='continuation_state'",
                Long.class, database.schema())).isZero();
        assertEncryptedSettings(database, fixture, true);
        assertTerminalReceipts(database, fixture);
    }

    private static Database database() {
        String schema = "companion_r00_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        // Keep the shared test-container extension visible to every isolated application schema.
        jdbc.execute("create extension if not exists pg_trgm with schema public");
        return new Database(schema, jdbc, POSTGRES.getJdbcUrl());
    }

    private static Fixture seedCore(Database database) {
        var fixture = new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), cipher());
        var jdbc = database.jdbc();
        String prefix = database.schema() + ".";
        jdbc.update("insert into " + prefix + "admin_users(id,username,password_hash,created_at) values(?,?,?,?)",
                fixture.adminId(), "synthetic-r00", new BCryptPasswordEncoder().encode(syntheticSecret("admin")),
                timestamp(CREATED));
        jdbc.update("insert into " + prefix + "devices(id,hardware_id,firmware_version,display_name,safety_state,"
                        + "refresh_token_hash,refresh_token_issued_at,credential_version) values(?,?,?,?,?,?,?,?)",
                fixture.deviceId(), "synthetic-r00-device", "synthetic-r3", "合成设备", "motion_disabled",
                sha256(syntheticSecret("refresh")), timestamp(CREATED), 7L);
        jdbc.update("insert into " + prefix + "companion_roles(id,name,tone,reply_length,proactivity,"
                        + "background_instructions,created_at,updated_at,tts_voice_override) values(?,?,?,?,?,?,?,?,?)",
                fixture.roleId(), "合成伙伴", "CALM", "SHORT", "RESERVED", "合成角色边界",
                timestamp(CREATED), timestamp(CREATED.plusSeconds(1)), "synthetic-voice");
        jdbc.update("insert into " + prefix + "device_active_roles(device_id,role_id,updated_at) values(?,?,?)",
                fixture.deviceId(), fixture.roleId(), timestamp(CREATED));
        jdbc.update("insert into " + prefix + "device_interaction_settings(device_id,zone_id,updated_at) values(?,?,?)",
                fixture.deviceId(), "Asia/Shanghai", timestamp(CREATED));
        jdbc.update("insert into " + prefix + "conversations(id,title,created_at,updated_at,role_id) values(?,?,?,?,?)",
                fixture.conversationId(), "合成待续来源对话", timestamp(CREATED), timestamp(CREATED.plusSeconds(3)), fixture.roleId());
        jdbc.update("insert into " + prefix + "conversation_messages(id,conversation_id,role,content,created_at,completed_at) "
                        + "values(?,?,'USER','合成夹具：书架第一层已完成',?,?)",
                fixture.userMessageId(), fixture.conversationId(), timestamp(CREATED), timestamp(CREATED));
        jdbc.update("insert into " + prefix + "conversation_messages(id,conversation_id,in_reply_to_message_id,role,content,"
                        + "created_at,completed_at) values(?,?,?,'ASSISTANT','合成夹具：收到明确进展',?,?)",
                UUID.randomUUID(), fixture.conversationId(), fixture.userMessageId(), timestamp(CREATED.plusSeconds(1)),
                timestamp(CREATED.plusSeconds(3)));
        jdbc.update("insert into " + prefix + "device_voice_conversations(device_id,role_id,conversation_id) values(?,?,?)",
                fixture.deviceId(), fixture.roleId(), fixture.conversationId());
        jdbc.update("insert into " + prefix + "personal_tasks(id,device_id,role_id,title,notes,priority,status,due_at,zone_id,"
                        + "created_at,updated_at) values(?,?,?,'合成未完成任务','保留原待办','HIGH','OPEN',?,'Asia/Shanghai',?,?)",
                UUID.randomUUID(), fixture.deviceId(), fixture.roleId(), timestamp(CREATED.plusSeconds(86400)),
                timestamp(CREATED), timestamp(CREATED));
        jdbc.update("insert into " + prefix + "personal_tasks(id,device_id,role_id,title,priority,status,zone_id,completed_at,"
                        + "created_at,updated_at) values(?,?,?,'合成已完成任务','NORMAL','COMPLETED','Asia/Shanghai',?,?,?)",
                UUID.randomUUID(), fixture.deviceId(), CompanionRoleEntity.DEFAULT_ROLE_ID, timestamp(CREATED.plusSeconds(60)),
                timestamp(CREATED), timestamp(CREATED.plusSeconds(60)));
        insertOrdinaryMemory(database, fixture.ordinaryGlobalMemoryId(), null, CompanionRoleEntity.DEFAULT_ROLE_ID,
                "GLOBAL", "USER_PROFILE", false);
        insertOrdinaryMemory(database, fixture.ordinaryDeviceMemoryId(), fixture.deviceId(), fixture.roleId(),
                "DEVICE", "EVENT", true);
        jdbc.update("insert into " + prefix + "memory_usage_records(turn_id,memory_id,used_at) values(?,?,?)",
                fixture.userMessageId(), fixture.ordinaryDeviceMemoryId(), timestamp(CREATED.plusSeconds(1)));
        seedEncryptedSettings(database, fixture);
        return fixture;
    }

    private static void insertOrdinaryMemory(Database database, UUID id, UUID deviceId, UUID roleId,
                                             String scope, String category, boolean allowProactiveMention) {
        database.jdbc().update("insert into " + database.schema() + ".long_term_memories(id,scope_type,device_id,role_id,"
                        + "category,title,content,source,source_detail,confirmation_status,enabled,confirmed_at,"
                        + "created_at,updated_at,topic_key,allow_proactive_mention) values(?,?,?,?,?,'合成普通事实',"
                        + "'合成夹具：已确认事实，不代表待续许可','USER_ENTERED','合成用户输入','CONFIRMED',true,?,?,?,?,?)",
                id, scope, deviceId, roleId, category, timestamp(CREATED), timestamp(CREATED), timestamp(CREATED),
                "synthetic-ordinary-" + id, allowProactiveMention);
    }

    private static void seedContinuation(Database database, Fixture fixture) {
        String prefix = database.schema() + ".";
        for (String state : List.of("PENDING", "ACTIVE", "PAUSED", "STOPPED", "DORMANT")) {
            UUID id = UUID.randomUUID();
            boolean confirmed = !state.equals("PENDING");
            database.jdbc().update("insert into " + prefix + "long_term_memories(id,scope_type,device_id,role_id,category,"
                            + "title,content,source,source_detail,confirmation_status,enabled,confirmed_at,created_at,updated_at,"
                            + "topic_key,continuation_state,continuation_progress,continuation_progress_at,continuation_paused_until,"
                            + "continuation_offered_at,continuation_discussed_at,continuation_conversation_id,"
                            + "continuation_progress_conversation_id,source_turn_id) values(?,'DEVICE',?,?,'EVENT',?,?,"
                            + "'ASSISTANT_SUGGESTED','合成夹具来源',?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, fixture.deviceId(), fixture.roleId(), "合成话题-" + state, "合成待续原事实-" + state,
                    confirmed ? "CONFIRMED" : "PENDING", confirmed, confirmed ? timestamp(CREATED) : null,
                    timestamp(CREATED), timestamp(CREATED.plusSeconds(90)), "synthetic-topic-" + state, state,
                    confirmed ? "合成夹具：书架第一层已完成" : "", confirmed ? timestamp(CREATED.plusSeconds(60)) : null,
                    state.equals("PAUSED") ? timestamp(CREATED.plusSeconds(86400)) : null,
                    timestamp(CREATED.plusSeconds(10)), confirmed ? timestamp(CREATED.plusSeconds(60)) : null,
                    fixture.conversationId(), confirmed ? fixture.conversationId() : null, UUID.randomUUID());
        }
        for (String stage : List.of("GENERATED", "OFFERED")) {
            database.jdbc().update("insert into " + prefix + "continuation_daily_budget(device_id,local_date,stage) values(?,?,?)",
                    fixture.deviceId(), LocalDate.of(2026, 10, 5), stage);
        }
        database.jdbc().update("insert into " + prefix + "continuation_daily_budget(device_id,local_date,stage) values(?,?,'GENERATED')",
                fixture.deviceId(), LocalDate.of(2026, 10, 6));
    }

    private static void seedLegacyDelegations(Database database, Fixture fixture) {
        String prefix = database.schema() + ".";
        database.jdbc().update("insert into " + prefix + "delegations(id,device_id,role_id,title,instruction,source_urls,"
                        + "schedule_type,days_of_week,local_time,zone_id,max_items,delivery_mode,enabled,revision,next_run_at,"
                        + "created_at,updated_at) values(?,?,?,'合成旧委托','合成夹具：每周比较公开资料',"
                        + "'[\"https://example.com/feed\"]','WEEKLY','[1,4]',?,'Asia/Shanghai',3,'BARK',true,7,?,?,?)",
                fixture.taskId(), fixture.deviceId(), fixture.roleId(), LocalTime.of(8, 30),
                timestamp(CREATED.plusSeconds(7 * 86400L)), timestamp(CREATED), timestamp(CREATED.plusSeconds(1)));
        String[] deliveries = {"SENT", "UNKNOWN", "NONE", "FAILED"};
        for (int index = 0; index < deliveries.length; index++) {
            LocalDate date = LocalDate.of(2026, 10, 5).plusDays(index);
            database.jdbc().update("insert into " + prefix + "delegation_runs(id,task_id,task_title,scheduled_date,task_revision,"
                            + "attempt,status,items,source_warnings,error_code,delivery_status,delivery_attempts,delivery_next_at,"
                            + "created_at,started_at,completed_at) values(?,?, '合成旧委托',?,7,1,?,"
                            + "'[{\"title\":\"合成旧成果\",\"summary\":\"合成摘要\",\"impact\":\"合成影响\","
                            + "\"sourceName\":\"合成来源\",\"url\":\"https://example.com/old\","
                            + "\"publishedAt\":\"2026-10-05T00:00:00Z\"}]','[\"SYNTHETIC_WARNING\"]',?,?,1,?,?,?,?)",
                    UUID.randomUUID(), fixture.taskId(), date, index < 2 ? "SUCCEEDED" : index == 2 ? "CANCELLED" : "FAILED",
                    index == 3 ? "SOURCES_UNAVAILABLE" : null, deliveries[index],
                    index == 3 ? timestamp(CREATED.plusSeconds(86400)) : null, timestamp(CREATED),
                    timestamp(CREATED.plusSeconds(index)), timestamp(CREATED.plusSeconds(index + 30)));
        }
        var encrypted = fixture.cipher().encrypt(syntheticSecret("bark"));
        database.jdbc().update("update " + prefix + "delegation_push_settings set key_ciphertext=?,key_iv=?,enabled=true,"
                        + "revision=9,updated_at=? where id=1",
                encrypted.ciphertext(), encrypted.initializationVector(), timestamp(CREATED));
    }

    private static void seedBarkReceipts(Database database) {
        for (String status : List.of("SENT", "UNKNOWN")) {
            database.jdbc().update("insert into " + database.schema() + ".bark_notification_attempts(operation_key,"
                            + "content_sha256,receipt_id,status,error_code,started_at,completed_at) values(?,?,?,?,?,?,?)",
                    sha256("synthetic-operation-" + status), sha256("synthetic-content-" + status), UUID.randomUUID(),
                    status, status.equals("UNKNOWN") ? "DELIVERY_UNKNOWN" : null,
                    timestamp(CREATED), timestamp(CREATED.plusSeconds(1)));
        }
    }

    private static void seedEncryptedSettings(Database database, Fixture fixture) {
        String prefix = database.schema() + ".";
        var llm = fixture.cipher().encrypt(syntheticSecret("llm"));
        database.jdbc().update("insert into " + prefix + "llm_provider_settings(id,base_url,model,api_key_ciphertext,api_key_iv,"
                        + "updated_at) values(1,'https://example.com/v1','synthetic-model',?,?,?)",
                llm.ciphertext(), llm.initializationVector(), timestamp(CREATED));
        var speech = fixture.cipher().encrypt(syntheticSecret("speech"));
        database.jdbc().update("insert into " + prefix + "speech_provider_settings(id,base_url,asr_model,tts_model,tts_voice,"
                        + "api_key_ciphertext,api_key_iv,updated_at) values(1,'https://example.com/v1','synthetic-asr',"
                        + "'synthetic-tts','synthetic-voice',?,?,?)",
                speech.ciphertext(), speech.initializationVector(), timestamp(CREATED));
        var mcp = fixture.cipher().encrypt(syntheticSecret("mcp"));
        database.jdbc().update("insert into " + prefix + "agent_mcp_connections(id,connection_name,url,endpoint,auth_type,"
                        + "bearer_token_ciphertext,bearer_token_iv,created_at,updated_at) values(?,'synthetic-r00',"
                        + "'https://example.com','/mcp','BEARER',?,?,?,?)",
                UUID.randomUUID(), mcp.ciphertext(), mcp.initializationVector(), timestamp(CREATED), timestamp(CREATED));
        var calendar = fixture.cipher().encrypt(syntheticSecret("calendar"));
        database.jdbc().update("insert into " + prefix + "icloud_calendar_connections(device_id,account_email,password_ciphertext,"
                        + "password_iv,status,created_at,updated_at) values(?,'synthetic-r00@example.invalid',?,?,'CONFIGURED',?,?)",
                fixture.deviceId(), calendar.ciphertext(), calendar.initializationVector(), timestamp(CREATED), timestamp(CREATED));
    }

    private static void assertEncryptedSettings(Database database, Fixture fixture, boolean hasBarkSettings) {
        assertDecryptedDigest(database, fixture, "llm_provider_settings", "api_key_ciphertext", "api_key_iv", "llm");
        assertDecryptedDigest(database, fixture, "speech_provider_settings", "api_key_ciphertext", "api_key_iv", "speech");
        assertDecryptedDigest(database, fixture, "agent_mcp_connections", "bearer_token_ciphertext", "bearer_token_iv", "mcp");
        assertDecryptedDigest(database, fixture, "icloud_calendar_connections", "password_ciphertext", "password_iv", "calendar");
        if (hasBarkSettings) {
            assertDecryptedDigest(database, fixture, "delegation_push_settings", "key_ciphertext", "key_iv", "bark");
        }
    }

    private static void assertDecryptedDigest(Database database, Fixture fixture, String table, String ciphertextColumn,
                                              String ivColumn, String syntheticName) {
        var row = database.jdbc().queryForMap("select " + ciphertextColumn + "," + ivColumn + " from "
                + database.schema() + "." + table);
        var encrypted = new SecretCipher.EncryptedSecret((String) row.get(ciphertextColumn), (String) row.get(ivColumn));
        assertThat(sha256(fixture.cipher().decrypt(encrypted))).as("decrypted synthetic %s digest", syntheticName)
                .isEqualTo(sha256(syntheticSecret(syntheticName)));
    }

    private static void assertOrdinaryMemoriesUnlicensed(Database database, Fixture fixture) {
        var rows = database.jdbc().queryForList("select continuation_state,continuation_progress,continuation_progress_at,"
                        + "continuation_paused_until,continuation_offered_at,continuation_discussed_at,continuation_conversation_id,"
                        + "continuation_progress_conversation_id from " + database.schema() + ".long_term_memories where id in (?,?)",
                fixture.ordinaryGlobalMemoryId(), fixture.ordinaryDeviceMemoryId());
        assertThat(rows).hasSize(2);
        rows.forEach(row -> {
            assertThat(row.get("continuation_state")).isEqualTo("NONE");
            assertThat(row.get("continuation_progress")).isEqualTo("");
            row.forEach((column, value) -> {
                if (!column.equals("continuation_state") && !column.equals("continuation_progress")) {
                    assertThat(value).as("ordinary memory %s", column).isNull();
                }
            });
        });
    }

    private static void assertContinuationStatesAndBudget(Database database) {
        assertThat(database.jdbc().queryForList("select distinct continuation_state from " + database.schema()
                + ".long_term_memories order by continuation_state", String.class))
                .containsExactly("ACTIVE", "DORMANT", "NONE", "PAUSED", "PENDING", "STOPPED");
        assertThat(database.jdbc().queryForObject("select count(*) from " + database.schema()
                + ".continuation_daily_budget", Long.class)).isEqualTo(3);
        assertThat(database.jdbc().queryForObject("select count(*) from " + database.schema()
                + ".long_term_memories where continuation_progress='合成夹具：书架第一层已完成'", Long.class)).isEqualTo(4);
    }

    private static void assertLegacyDelegationNormalization(Database database, Fixture fixture) {
        assertThat(database.jdbc().queryForObject("select understanding=instruction and requires_tool_evidence "
                + "and allowed_tool_names='[]' and required_tool_names='[]' from " + database.schema()
                + ".delegations where id=?", Boolean.class, fixture.taskId())).isTrue();
        assertThat(database.jdbc().queryForObject("select bool_and(occurrence_key='local:'||scheduled_date::text "
                + "and scheduled_at=started_at and output='' and steps='[]' and evidence='[]') from " + database.schema()
                + ".delegation_runs where task_id=?", Boolean.class, fixture.taskId())).isTrue();
    }

    private static void assertTerminalReceipts(Database database, Fixture fixture) {
        assertThat(database.jdbc().queryForList("select delivery_status from " + database.schema()
                + ".delegation_runs where task_id=? and delivery_status in ('SENT','UNKNOWN') order by delivery_status",
                String.class, fixture.taskId())).containsExactly("SENT", "UNKNOWN");
        assertThat(database.jdbc().queryForList("select status from " + database.schema()
                + ".bark_notification_attempts order by status", String.class)).containsExactly("SENT", "UNKNOWN");
    }

    private static Map<String, TableSnapshot> snapshot(Database database, List<String> tables) {
        var snapshots = new LinkedHashMap<String, TableSnapshot>();
        for (String table : tables) {
            var columns = database.jdbc().queryForList("select column_name from information_schema.columns "
                    + "where table_schema=? and table_name=? order by ordinal_position", String.class, database.schema(), table);
            assertThat(columns).as("synthetic fixture table %s", table).isNotEmpty();
            snapshots.put(table, new TableSnapshot(columns, readRows(database, table, columns)));
        }
        return snapshots;
    }

    private static List<Map<String, Object>> readRows(Database database, String table, List<String> columns) {
        // Stable ordering compares every original field and row, even after later migrations add columns.
        String selected = String.join(",", columns);
        return database.jdbc().queryForList("select " + selected + " from " + database.schema() + "." + table
                + " order by " + selected);
    }

    private static void assertUnchanged(Database database, Map<String, TableSnapshot> snapshots) {
        snapshots.forEach((table, before) -> assertThat(readRows(database, table, before.columns()))
                .as("all preexisting synthetic rows and fields in %s", table).isEqualTo(before.rows()));
    }

    private static List<Map<String, Object>> history(Database database) {
        return database.jdbc().queryForList("select version,script,checksum from " + database.schema()
                + ".flyway_schema_history where success and version is not null order by installed_rank");
    }

    private static void assertOldChecksums(Database database, List<Map<String, Object>> previous) {
        assertThat(history(database).subList(0, previous.size())).isEqualTo(previous);
    }

    private static List<String> withTables(List<String> original, String... added) {
        var all = new ArrayList<>(original);
        all.addAll(List.of(added));
        return all;
    }

    private static List<String> allFixtureTables() {
        return withTables(CORE_TABLES, "continuation_daily_budget", "delegations", "delegation_runs",
                "delegation_push_settings", "bark_notification_attempts");
    }

    private static void copyMainlineMigrationsWithoutV57(Path directory) throws IOException {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*.sql");
        int copied = 0;
        for (var resource : resources) {
            String name = resource.getFilename();
            if (name != null && !name.startsWith("V57__")
                    && Integer.parseInt(name.substring(1, name.indexOf("__"))) <= 60) {
                try (var source = resource.getInputStream()) {
                    Files.copy(source, directory.resolve(name));
                }
                copied++;
            }
        }
        assertThat(copied).isEqualTo(59);
    }

    private static SecretCipher cipher() {
        var properties = new AppProperties();
        properties.setSecretsEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new SecretCipher(properties);
    }

    private static String syntheticSecret(String name) {
        return "SYNTHETIC-R00-" + name + "-NOT-A-REAL-CREDENTIAL";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private record TableSnapshot(List<String> columns, List<Map<String, Object>> rows) { }

    private record Database(String schema, JdbcTemplate jdbc, String jdbcUrl) {
        FluentConfiguration configuration() {
            return Flyway.configure().dataSource(jdbcUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).target("60");
        }
    }

    private record Fixture(UUID adminId, UUID deviceId, UUID roleId, UUID conversationId, UUID userMessageId,
                           UUID ordinaryGlobalMemoryId, UUID ordinaryDeviceMemoryId, UUID taskId, SecretCipher cipher) { }
}
