package com.kj.stackchan.delegation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.SecretCipher;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the actual previous schema with populated history, never a running application or outbound client. */
@Testcontainers
class DelegationUpgradePersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    private static final String TASK_COLUMNS = "id,device_id,role_id,title,instruction,source_urls,schedule_type,"
            + "days_of_week,local_time,zone_id,max_items,delivery_mode,enabled,revision,next_run_at,created_at,updated_at";
    private static final String RUN_COLUMNS = "id,task_id,task_title,scheduled_date,task_revision,attempt,status,"
            + "items,source_warnings,error_code,delivery_status,delivery_attempts,delivery_next_at,created_at,started_at,completed_at";
    private static final String AUDIT_COLUMNS = "id,turn_id,conversation_id,device_id,channel,skill_id,tool_name,"
            + "source_type,source_id,outcome,duration_ms,result_bytes,truncated,created_at";

    @Test
    void upgradesPopulatedV58WithoutChangingExistingDataAndPreservesOccurrenceIdempotency() {
        var database = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(database);
        String schema = "delegation_v58_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema);
        var previous = configuration.target("58").load();
        previous.migrate();
        assertThat(previous.info().current().getVersion().toString()).isEqualTo("58");

        UUID deviceId = UUID.randomUUID(), taskId = UUID.randomUUID(), conversationId = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-08T00:00:00Z"), scheduled = Instant.parse("2026-10-12T00:30:00Z");
        jdbc.update("insert into " + schema + ".devices(id,hardware_id,firmware_version) values(?,?,?)",
                deviceId, "upgrade-fixture-" + deviceId, "test");
        jdbc.update("insert into " + schema + ".conversations(id,title,created_at,updated_at,role_id) values(?,?,?,?,?)",
                conversationId, "升级前对话", Timestamp.from(created), Timestamp.from(created), CompanionRoleEntity.DEFAULT_ROLE_ID);
        jdbc.update("insert into " + schema + ".delegations(" + TASK_COLUMNS + ") values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                taskId, deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID, "原日报", "每周整理资料并推送，保留原要求",
                "[\"https://example.com/news/rss.xml\",\"https://example.org/feed.xml\"]", "WEEKLY", "[1,4]",
                java.time.LocalTime.of(8, 30), "Asia/Shanghai", 5, "BARK", true, 7L,
                Timestamp.from(scheduled), Timestamp.from(created), Timestamp.from(created.plusSeconds(30)));

        UUID succeeded = insertLegacyRun(jdbc, schema, taskId, LocalDate.of(2026, 10, 8), "SUCCEEDED", "SENT", null);
        insertLegacyRun(jdbc, schema, taskId, LocalDate.of(2026, 10, 9), "FAILED", "NONE", "SOURCES_UNAVAILABLE");
        insertLegacyRun(jdbc, schema, taskId, LocalDate.of(2026, 10, 10), "NO_UPDATES", "NONE", null);
        insertLegacyRun(jdbc, schema, taskId, LocalDate.of(2026, 10, 11), "SUCCEEDED", "SENDING", null);
        insertLegacyAudit(jdbc, schema, deviceId, conversationId, "WEB");
        insertLegacyAudit(jdbc, schema, deviceId, null, "VOICE");
        assertThatThrownBy(() -> insertLegacyAudit(jdbc, schema, deviceId, null, "DELEGATION"))
                .isInstanceOf(DataIntegrityViolationException.class);

        var properties = new AppProperties();
        properties.setSecretsEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var cipher = new SecretCipher(properties);
        var fixtureKey = cipher.encrypt("migration-fixture-bark-key");
        jdbc.update("update " + schema + ".delegation_push_settings set key_ciphertext=?,key_iv=?,enabled=true,revision=9,updated_at=? where id=1",
                fixtureKey.ciphertext(), fixtureKey.initializationVector(), Timestamp.from(created));

        var oldChecksums = jdbc.queryForList("select version,checksum from " + schema
                + ".flyway_schema_history where success and version is not null order by installed_rank");
        var oldTasks = jdbc.queryForList("select " + TASK_COLUMNS + " from " + schema + ".delegations order by id");
        var oldRuns = jdbc.queryForList("select " + RUN_COLUMNS + " from " + schema + ".delegation_runs order by scheduled_date");
        var oldSettings = jdbc.queryForList("select * from " + schema + ".delegation_push_settings");
        var oldAudits = jdbc.queryForList("select " + AUDIT_COLUMNS + " from " + schema + ".agent_tool_invocations order by id");

        var upgraded = configuration.target("59").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgraded.info().current().getVersion().toString()).isEqualTo("59");
        assertThat(jdbc.queryForList("select version,checksum from " + schema
                + ".flyway_schema_history where success and version is not null and version<>'59' order by installed_rank"))
                .isEqualTo(oldChecksums);
        assertThat(jdbc.queryForList("select " + TASK_COLUMNS + " from " + schema + ".delegations order by id")).isEqualTo(oldTasks);
        assertThat(jdbc.queryForList("select " + RUN_COLUMNS + " from " + schema + ".delegation_runs order by scheduled_date")).isEqualTo(oldRuns);
        assertThat(jdbc.queryForList("select * from " + schema + ".delegation_push_settings")).isEqualTo(oldSettings);
        assertThat(jdbc.queryForList("select " + AUDIT_COLUMNS + " from " + schema + ".agent_tool_invocations order by id")).isEqualTo(oldAudits);
        assertThat(jdbc.queryForObject("select title from " + schema + ".conversations where id=?", String.class, conversationId))
                .isEqualTo("升级前对话");
        assertThat(jdbc.queryForObject("select understanding=instruction and requires_tool_evidence and allowed_tool_names='[]' and required_tool_names='[]' from "
                + schema + ".delegations where id=?", Boolean.class, taskId)).isTrue();
        assertThat(jdbc.queryForObject("select bool_and(occurrence_key='local:'||scheduled_date::text and scheduled_at=started_at "
                + "and output='' and steps='[]' and evidence='[]') from " + schema + ".delegation_runs", Boolean.class)).isTrue();
        assertThat(cipher.decrypt(new SecretCipher.EncryptedSecret(
                jdbc.queryForObject("select key_ciphertext from " + schema + ".delegation_push_settings", String.class),
                jdbc.queryForObject("select key_iv from " + schema + ".delegation_push_settings", String.class))))
                .isEqualTo("migration-fixture-bark-key");

        assertThatThrownBy(() -> insertNewRun(jdbc, schema, taskId, "local:2026-10-08"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertNewRun(jdbc, schema, taskId, "interval:2026-10-08T02:00:00Z");
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".delegation_runs where task_id=? and scheduled_date=?",
                Integer.class, taskId, LocalDate.of(2026, 10, 8))).isEqualTo(2);

        UUID auditId = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".agent_tool_invocations(id,turn_id,device_id,channel,tool_name,source_type,outcome,"
                + "duration_ms,result_bytes,truncated,created_at,delegation_run_id,delegation_attempt) values(?,?,?,'DELEGATION',"
                + "'current_date_time','BUILTIN','SUCCESS',3,42,false,?,?,2)",
                auditId, succeeded, deviceId, Timestamp.from(created.plusSeconds(20)), succeeded);
        assertThat(jdbc.queryForMap("select channel,delegation_run_id,delegation_attempt from " + schema
                + ".agent_tool_invocations where id=?", auditId))
                .containsEntry("channel", "DELEGATION").containsEntry("delegation_run_id", succeeded).containsEntry("delegation_attempt", 2);
        assertThatThrownBy(() -> insertLegacyAudit(jdbc, schema, deviceId, null, "UNRECOGNISED"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        var priorHistory=jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where success and version is not null order by installed_rank");
        var priorRunCount=jdbc.queryForObject("select count(*) from "+schema+".delegation_runs where task_id=?",Integer.class,taskId);
        var phoneUpgrade=configuration.target("60").load();
        assertThat(phoneUpgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where success and version is not null and version<>'60' order by installed_rank"))
                .isEqualTo(priorHistory);
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".bark_notification_attempts",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".delegation_runs where task_id=?",Integer.class,taskId)).isEqualTo(priorRunCount);
        assertThat(phoneUpgrade.validateWithResult().validationSuccessful).isTrue();
        jdbc.update("delete from " + schema + ".delegations where id=?", taskId);
        assertThat(jdbc.queryForObject("select count(*) from " + schema + ".delegation_runs where task_id=?", Integer.class, taskId)).isZero();
        assertThat(jdbc.queryForObject("select delegation_run_id from " + schema + ".agent_tool_invocations where id=?", UUID.class, auditId))
                .isEqualTo(succeeded);
    }

    private static UUID insertLegacyRun(JdbcTemplate jdbc, String schema, UUID taskId, LocalDate date,
                                       String status, String deliveryStatus, String errorCode) {
        UUID runId = UUID.randomUUID();
        Instant started = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        boolean hasItems = status.equals("SUCCEEDED");
        String items = hasItems ? "[{\"title\":\"真实旧成果\",\"summary\":\"旧摘要\",\"impact\":\"旧影响\","
                + "\"sourceName\":\"原来源\",\"url\":\"https://example.com/news/old?ref=legacy\","
                + "\"publishedAt\":\"2026-10-08T00:00:00Z\"}]" : "[]";
        jdbc.update("insert into " + schema + ".delegation_runs(" + RUN_COLUMNS + ") values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                runId, taskId, "原日报", date, 7L, status.equals("FAILED") ? 2 : 1, status, items,
                "[\"SOURCE_2_UNAVAILABLE\"]", errorCode, deliveryStatus, hasItems ? 1 : 0, null,
                Timestamp.from(started), Timestamp.from(started.plusSeconds(2)), Timestamp.from(started.plusSeconds(15)));
        return runId;
    }

    private static void insertLegacyAudit(JdbcTemplate jdbc, String schema, UUID deviceId, UUID conversationId, String channel) {
        jdbc.update("insert into " + schema + ".agent_tool_invocations(id,turn_id,conversation_id,device_id,channel,tool_name,"
                + "source_type,outcome,duration_ms,result_bytes,truncated,created_at) values(?,?,?,?,?,'current_date_time',"
                + "'BUILTIN','SUCCESS',2,30,false,?)", UUID.randomUUID(), UUID.randomUUID(), conversationId, deviceId, channel,
                Timestamp.from(Instant.parse("2026-10-08T00:00:00Z")));
    }

    private static void insertNewRun(JdbcTemplate jdbc, String schema, UUID taskId, String occurrenceKey) {
        Instant scheduled = Instant.parse("2026-10-08T02:00:00Z");
        jdbc.update("insert into " + schema + ".delegation_runs(id,task_id,task_title,scheduled_date,task_revision,attempt,status,"
                + "created_at,started_at,occurrence_key,scheduled_at) values(?,?,?, ?,7,1,'BLOCKED',?,?,?,?)",
                UUID.randomUUID(), taskId, "原日报", LocalDate.of(2026, 10, 8), Timestamp.from(scheduled), Timestamp.from(scheduled),
                occurrenceKey, Timestamp.from(scheduled));
    }
}
