package com.kj.stackchan.delegation;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.SecretCipher;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;

/** Real isolated PostgreSQL with synthetic private data; no production database or external providers. */
@Testcontainers
class ProjectLoopMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4").asCompatibleSubstituteFor("postgres"));
    private static final Instant CREATED=Instant.parse("2026-10-15T01:00:00Z");
    private static final List<String> LEGACY_TABLES=List.of("admin_users","devices","companion_roles","conversations",
            "device_voice_conversations","long_term_memories","continuation_daily_budget","voice_turns","voice_turn_events",
            "voice_turn_privacy_tombstones","delegations","delegation_runs","bark_notification_attempts",
            "llm_provider_settings","speech_provider_settings","agent_mcp_connections","icloud_calendar_connections","delegation_push_settings");

    @Test void populatedV61IncludingV57V60AndV61UpgradesWithoutChangingAnyLegacyFieldOrChecksum() {
        var schema=schema();
        assertThat(schema.configuration().target("61").load().migrate().migrationsExecuted).isEqualTo(61);
        var scope=scope(schema);
        seedOlderCompanionData(schema,scope);
        var cipher=seedSecrets(schema,scope.device());
        UUID sent=run(schema,scope.task(),"SENT",1), unknown=run(schema,scope.task(),"UNKNOWN",2);
        for(String status:List.of("SENT","UNKNOWN")) schema.jdbc().update("insert into "+schema.prefix()+
                "bark_notification_attempts(operation_key,content_sha256,receipt_id,status,started_at,completed_at) values(?,?,?,?,?,?)",
                (status.equals("SENT")?"a":"b").repeat(64),"c".repeat(64),UUID.randomUUID(),status,time(CREATED),time(CREATED));
        var original=capture(schema,LEGACY_TABLES);
        var checksums=history(schema);
        assertThat(checksums).hasSize(61);
        assertSecrets(schema,cipher);

        var candidate=schema.configuration().target("62").load();
        assertThat(candidate.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(candidate.info().current().getVersion().toString()).isEqualTo("62");
        original.forEach((table,snapshot)->assertThat(rows(schema,table,snapshot.columns())).as("all old columns in %s",table).isEqualTo(snapshot.rows()));
        assertSecrets(schema,cipher);
        assertThat(history(schema).subList(0,61)).isEqualTo(checksums);
        assertThat(schema.jdbc().queryForMap("select id,status,delivery_status,attempt from "+schema.prefix()+"delegation_runs where id=?",sent))
                .containsEntry("id",sent).containsEntry("status","SUCCEEDED").containsEntry("delivery_status","SENT").containsEntry("attempt",1);
        assertThat(schema.jdbc().queryForObject("select delivery_status from "+schema.prefix()+"delegation_runs where id=?",String.class,unknown)).isEqualTo("UNKNOWN");
        assertThat(schema.jdbc().queryForObject("select not project_loop and cancelled_at is null and cancellation_reason is null from "+schema.prefix()+
                "delegations where id=?",Boolean.class,scope.task())).isTrue();
        for(String table:List.of("delegation_result_reviews","delegation_result_feedback","delegation_revision_intents","companion_operation_receipts"))
            assertThat(schema.jdbc().queryForObject("select count(*) from "+schema.prefix()+table,Long.class)).as("legacy migration creates no new %s records",table).isZero();
        assertThat(candidate.validateWithResult().validationSuccessful).isTrue();
        assertThat(candidate.migrate().migrationsExecuted).isZero();
    }

    @Test void anEmptyDatabaseAppliesAll62MigrationsAndKeepsRunDeliveryAndReviewStatesSeparate() {
        var schema=schema(); var candidate=schema.configuration().target("62").load();
        assertThat(candidate.migrate().migrationsExecuted).isEqualTo(62);
        assertThat(history(schema)).hasSize(62);
        var scope=scope(schema); UUID run=run(schema,scope.task(),"UNKNOWN",1);
        schema.jdbc().update("insert into "+schema.prefix()+"delegation_result_reviews(run_id) values(?)",run);
        assertThat(schema.jdbc().queryForMap("select review_state,resource_version,acknowledged_at from "+schema.prefix()+"delegation_result_reviews where run_id=?",run))
                .containsEntry("review_state","UNREVIEWED").containsEntry("resource_version",1L).containsEntry("acknowledged_at",null);
        assertThatThrownBy(()->schema.jdbc().update("update "+schema.prefix()+"delegation_runs set status='ACCEPTED' where id=?",run))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("update "+schema.prefix()+"delegation_runs set delivery_status='READ' where id=?",run))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("update "+schema.prefix()+"delegation_result_reviews set resource_version=0 where run_id=?",run))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("update "+schema.prefix()+"delegation_result_reviews set review_state='COMPLETED' where run_id=?",run))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(candidate.validateWithResult().validationSuccessful).isTrue();
        assertThat(candidate.migrate().migrationsExecuted).isZero();
    }

    @Test void deletingTaskCascadesPrivateFeedbackAndArtifactsButRetainsBodyFreeReceiptAndCannotReplayItsKey() {
        var schema=schema(); schema.configuration().target("62").load().migrate();
        var deleted=scope(schema); var untouched=scope(schema);
        UUID first=run(schema,deleted.task(),"SENT",1), second=run(schema,deleted.task(),"NONE",2);
        UUID otherRun=run(schema,untouched.task(),"UNKNOWN",1);
        seedProjectHistory(schema,deleted,first,second);
        String privateComment="合成私人修订：更在意线缆少；不是现实用户资料";
        String key=UUID.randomUUID().toString();
        var service=operations(schema);
        Map<String,String> canonical=Map.of("comment",privateComment);
        var expected=new OperationReceiptService.Result(Map.of("taskId",deleted.task(),"runId",first,"newRunId",second),8,"REVISION_REQUESTED",2L);
        assertThat(service.perform(deleted.device(),deleted.role(),OperationReceiptService.Action.DELEGATION_FEEDBACK,key,first,canonical,()->expected)).isEqualTo(expected);
        var receiptBefore=schema.jdbc().queryForList("select * from "+schema.prefix()+"companion_operation_receipts");
        assertThat(receiptBefore.toString()).doesNotContain(privateComment,"合成私人修订","comment");
        var otherBefore=schema.jdbc().queryForMap("select * from "+schema.prefix()+"delegation_runs where id=?",otherRun);

        schema.jdbc().update("delete from "+schema.prefix()+"delegations where id=?",deleted.task());
        for(String table:List.of("delegation_runs","delegation_result_feedback","delegation_revision_intents"))
            assertThat(schema.jdbc().queryForObject("select count(*) from "+schema.prefix()+table+" where task_id=?",Long.class,deleted.task())).isZero();
        assertThat(schema.jdbc().queryForObject("select count(*) from "+schema.prefix()+"delegation_result_reviews where run_id in (?,?)",Long.class,first,second)).isZero();
        assertThat(schema.jdbc().queryForMap("select * from "+schema.prefix()+"delegation_runs where id=?",otherRun)).isEqualTo(otherBefore);
        assertThat(schema.jdbc().queryForList("select * from "+schema.prefix()+"companion_operation_receipts")).isEqualTo(receiptBefore);
        var calls=new AtomicInteger();
        assertThat(service.perform(deleted.device(),deleted.role(),OperationReceiptService.Action.DELEGATION_FEEDBACK,key,first,canonical,()-> {
            calls.incrementAndGet(); throw new AssertionError("A deleted private task must never be recreated by replaying its old key");
        })).isEqualTo(expected);
        assertThat(calls).hasValue(0);
        assertThat(service.get(deleted.device(),deleted.role(),OperationReceiptService.Action.DELEGATION_FEEDBACK,key,first).status()).isEqualTo("COMMITTED");
        assertThat(service.get(untouched.device(),untouched.role(),OperationReceiptService.Action.DELEGATION_FEEDBACK,key,first).status()).isEqualTo("NOT_FOUND");
    }

    @Test void deletingDeviceAndRoleRemovesPrivateProjectRowsWithoutDeletingAnonymousDeduplicationMarkers() {
        var schema=schema(); schema.configuration().target("62").load().migrate();
        var scope=scope(schema); UUID first=run(schema,scope.task(),"NONE",1), second=run(schema,scope.task(),"NONE",2);
        seedProjectHistory(schema,scope,first,second);
        var service=operations(schema); String key=UUID.randomUUID().toString();
        service.perform(scope.device(),scope.role(),OperationReceiptService.Action.DELEGATION_CREATE,key,null,Map.of("scope","synthetic"),
                ()->new OperationReceiptService.Result(Map.of("taskId",scope.task()),7,null,null));
        var markers=schema.jdbc().queryForList("select * from "+schema.prefix()+"companion_operation_receipts");
        schema.jdbc().update("delete from "+schema.prefix()+"devices where id=?",scope.device());
        schema.jdbc().update("delete from "+schema.prefix()+"companion_roles where id=?",scope.role());
        for(String table:List.of("delegations","delegation_runs","delegation_result_reviews","delegation_result_feedback","delegation_revision_intents"))
            assertThat(schema.jdbc().queryForObject("select count(*) from "+schema.prefix()+table,Long.class)).isZero();
        assertThat(schema.jdbc().queryForList("select * from "+schema.prefix()+"companion_operation_receipts")).isEqualTo(markers);
    }

    @Test void feedbackConstraintsAndUniqueRevisionSlotPreventInvalidStatesAndParallelDuplicateIntent() {
        var schema=schema(); schema.configuration().target("62").load().migrate();
        var scope=scope(schema); UUID first=run(schema,scope.task(),"NONE",1), second=run(schema,scope.task(),"NONE",2);
        seedProjectHistory(schema,scope,first,second);
        assertThatThrownBy(()->insertFeedback(schema,scope,first,"MODEL_ACCEPTED","synthetic"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->insertFeedback(schema,scope,first,"REVISE_PREFERENCES","字".repeat(2001)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("insert into "+schema.prefix()+"delegation_revision_intents(task_id,task_revision,run_id,operation_key,created_at) values(?,8,?,?,?)",
                scope.task(),first,UUID.randomUUID().toString(),time(CREATED))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("insert into "+schema.prefix()+"companion_operation_receipts(device_id,role_id,action,operation_key,request_digest,status,created_at,updated_at) values(?,?,'read_public_source',?,?,'PENDING',?,?)",
                scope.device(),scope.role(),UUID.randomUUID().toString(),"a".repeat(64),time(CREATED),time(CREATED)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("update "+schema.prefix()+"delegation_result_feedback set expected_revision=0 where task_id=?",scope.task()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->schema.jdbc().update("update "+schema.prefix()+"delegation_revision_intents set task_revision=0 where task_id=?",scope.task()))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Unicode limit counts code points in PostgreSQL rather than UTF-16 units.
        insertFeedback(schema,scope,first,"REVISE_PREFERENCES","🙂".repeat(2000));
    }

    @Test void aRealPgDumpRestoreOfPopulatedV61UpgradesTo62WithHistoryCredentialsAndReceiptsIntact() throws Exception {
        String suffix=UUID.randomUUID().toString().replace("-","");
        String sourceDatabase="r02_source_"+suffix, restoredDatabase="r02_restored_"+suffix;
        var admin=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        admin.execute("create database "+sourceDatabase); admin.execute("create database "+restoredDatabase);
        String sourceUrl=databaseUrl(sourceDatabase), restoredUrl=databaseUrl(restoredDatabase);
        var sourceJdbc=new JdbcTemplate(new DriverManagerDataSource(sourceUrl,POSTGRES.getUsername(),POSTGRES.getPassword()));
        sourceJdbc.execute("create extension if not exists pg_trgm with schema public");
        var source=new Schema("project_loop_restore_fixture",sourceJdbc,sourceUrl);
        assertThat(source.configuration().target("61").load().migrate().migrationsExecuted).isEqualTo(61);
        var scope=scope(source); seedOlderCompanionData(source,scope); var cipher=seedSecrets(source,scope.device());
        UUID sent=run(source,scope.task(),"SENT",1), unknown=run(source,scope.task(),"UNKNOWN",2);
        for(String status:List.of("SENT","UNKNOWN")) source.jdbc().update("insert into "+source.prefix()+
                        "bark_notification_attempts(operation_key,content_sha256,receipt_id,status,started_at,completed_at) values(?,?,?,?,?,?)",
                (status.equals("SENT")?"a":"b").repeat(64),"c".repeat(64),UUID.randomUUID(),status,time(CREATED),time(CREATED));
        var snapshot=capture(source,LEGACY_TABLES); var checksums=history(source);
        assertSecrets(source,cipher);
        String dumpFile="/tmp/r02-v61-"+suffix+".dump";
        try {
            var dump=POSTGRES.execInContainer("pg_dump","--username",POSTGRES.getUsername(),"--dbname",sourceDatabase,"--format=custom","--file",dumpFile);
            assertThat(dump.getExitCode()).as("real pg_dump exit code; synthetic private dump is never printed").isZero();
            assertThat(dump.getStdout()).isEmpty();
            var restore=POSTGRES.execInContainer("pg_restore","--username",POSTGRES.getUsername(),"--dbname",restoredDatabase,
                    "--no-owner","--exit-on-error",dumpFile);
            assertThat(restore.getExitCode()).as("real isolated pg_restore exit code").isZero();
            var restored=new Schema(source.name(),new JdbcTemplate(new DriverManagerDataSource(restoredUrl,POSTGRES.getUsername(),POSTGRES.getPassword())),restoredUrl);
            snapshot.forEach((table,original)->assertThat(rows(restored,table,original.columns())).as("restored synthetic %s",table).isEqualTo(original.rows()));
            assertThat(history(restored)).isEqualTo(checksums); assertSecrets(restored,cipher);
            var candidate=restored.configuration().target("62").load();
            assertThat(candidate.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(candidate.info().current().getVersion().toString()).isEqualTo("62");
            snapshot.forEach((table,original)->assertThat(rows(restored,table,original.columns())).as("restored then upgraded synthetic %s",table).isEqualTo(original.rows()));
            assertThat(history(restored).subList(0,61)).isEqualTo(checksums); assertSecrets(restored,cipher);
            assertThat(restored.jdbc().queryForObject("select delivery_status from "+restored.prefix()+"delegation_runs where id=?",String.class,sent)).isEqualTo("SENT");
            assertThat(restored.jdbc().queryForObject("select delivery_status from "+restored.prefix()+"delegation_runs where id=?",String.class,unknown)).isEqualTo("UNKNOWN");
            assertThat(candidate.validateWithResult().validationSuccessful).isTrue();
            assertThat(candidate.migrate().migrationsExecuted).isZero();
        } finally {
            assertThat(dumpFile).matches("/tmp/r02-v61-[a-f0-9]{32}\\.dump");
            POSTGRES.execInContainer("rm","-f","--",dumpFile);
        }
    }

    private static Schema schema() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()));
        jdbc.execute("create extension if not exists pg_trgm with schema public");
        return new Schema("project_loop_"+UUID.randomUUID().toString().replace("-",""),jdbc);
    }
    private static Scope scope(Schema schema) {
        var jdbc=schema.jdbc(); String p=schema.prefix();
        UUID device=UUID.randomUUID(),role=UUID.randomUUID(),task=UUID.randomUUID();
        jdbc.update("insert into "+p+"devices(id,hardware_id,firmware_version,credential_version,refresh_token_hash,refresh_token_issued_at) values(?,?,'synthetic-r02',7,?,?)",
                device,"synthetic-r02-"+device,"d".repeat(64),time(CREATED));
        jdbc.update("insert into "+p+"companion_roles(id,name,tone,reply_length,proactivity,created_at,updated_at) values(?,'合成R02伙伴','CALM','SHORT','RESERVED',?,?)",
                role,time(CREATED),time(CREATED));
        jdbc.update("insert into "+p+"delegations(id,device_id,role_id,title,instruction,source_urls,schedule_type,days_of_week,local_time,zone_id,max_items,delivery_mode,enabled,revision,next_run_at,created_at,updated_at,understanding,allowed_tool_names,required_tool_names) "
                        +"values(?,?,?,'合成旧委托','每周整理公开资料','[\"https://example.com/fake\"]','WEEKLY','[1,4]','08:30','Asia/Shanghai',5,'BARK',true,7,?,?,?,'旧理解','[\"read_public_source\"]','[\"read_public_source\"]')",
                task,device,role,time(CREATED.plusSeconds(86400)),time(CREATED),time(CREATED));
        return new Scope(device,role,task);
    }
    private static UUID run(Schema schema,UUID task,String delivery,int index) {
        UUID id=UUID.randomUUID();
        schema.jdbc().update("insert into "+schema.prefix()+"delegation_runs(id,task_id,task_title,scheduled_date,task_revision,attempt,status,items,source_warnings,delivery_status,delivery_attempts,created_at,started_at,completed_at,occurrence_key,scheduled_at,output,steps,evidence) "
                        +"values(?,?,'合成历史成果',?,7,1,'SUCCEEDED','[]','[]',?,1,?,?,?, ?,?,'合成原成果：不是真实研究','[{\"toolName\":\"read_public_source\",\"status\":\"SUCCEEDED\",\"summary\":\"合成步骤\"}]','[{\"title\":\"合成旧来源\",\"url\":\"https://example.com/fake\",\"detail\":\"合成旧事实\"}]')",
                id,task,LocalDate.of(2026,10,13).plusDays(index),delivery,time(CREATED),time(CREATED),time(CREATED.plusSeconds(10)),
                "local:synthetic-"+index,time(CREATED));
        return id;
    }
    private static void seedOlderCompanionData(Schema schema,Scope scope) {
        var jdbc=schema.jdbc(); String p=schema.prefix(); UUID conversation=UUID.randomUUID(),turn=UUID.randomUUID();
        jdbc.update("insert into "+p+"admin_users(id,username,password_hash,created_at) values(?,'synthetic-r02-admin','synthetic-password-hash',?)",UUID.randomUUID(),time(CREATED));
        jdbc.update("insert into "+p+"conversations(id,title,role_id,created_at,updated_at) values(?,'合成旧对话',?,?,?)",conversation,scope.role(),time(CREATED),time(CREATED));
        jdbc.update("insert into "+p+"device_voice_conversations(device_id,role_id,conversation_id) values(?,?,?)",scope.device(),scope.role(),conversation);
        jdbc.update("insert into "+p+"long_term_memories(id,scope_type,device_id,role_id,category,title,content,source,source_detail,confirmation_status,enabled,confirmed_at,created_at,updated_at,topic_key,continuation_state,continuation_progress,continuation_conversation_id,continuation_progress_conversation_id) "
                        +"values(?,'DEVICE',?,?,'EVENT','合成旧项目','合成私人记忆','USER_ENTERED','synthetic','CONFIRMED',true,?,?,?,'synthetic-r02','ACTIVE','合成旧进度',?,?)",
                UUID.randomUUID(),scope.device(),scope.role(),time(CREATED),time(CREATED),time(CREATED),conversation,conversation);
        jdbc.update("insert into "+p+"continuation_daily_budget(device_id,local_date,stage) values(?,?,'OFFERED')",scope.device(),LocalDate.of(2026,10,15));
        jdbc.update("insert into "+p+"voice_turns(id,device_id,status,started_at,updated_at,conversation_id) values(?,?,'COMPLETED',?,?,?)",turn,scope.device(),time(CREATED),time(CREATED),conversation);
        jdbc.update("insert into "+p+"voice_turn_events(id,turn_id,stage,source,occurred_at,duration_ms) values(?,?,'ASR_COMPLETED','SERVER',?,123)",UUID.randomUUID(),turn,time(CREATED));
        jdbc.update("insert into "+p+"voice_turn_privacy_tombstones(turn_id,expires_at) values(?,?)",UUID.randomUUID(),time(CREATED.plusSeconds(604800)));
    }
    private static void seedProjectHistory(Schema schema,Scope scope,UUID first,UUID second) {
        String p=schema.prefix(); var jdbc=schema.jdbc();
        jdbc.update("update "+p+"delegations set project_loop=true,revision=8 where id=?",scope.task());
        jdbc.update("insert into "+p+"delegation_result_reviews(run_id,review_state,resource_version) values(?,'REVISION_REQUESTED',2)",first);
        jdbc.update("insert into "+p+"delegation_result_reviews(run_id,supersedes_run_id) values(?,?)",second,first);
        insertFeedback(schema,scope,first,"REVISE_PREFERENCES","合成私人修订：更在意线缆少；不是现实用户资料");
        jdbc.update("insert into "+p+"delegation_revision_intents(task_id,task_revision,run_id,supersedes_run_id,operation_key,created_at) values(?,8,?,?,?,?)",
                scope.task(),second,first,UUID.randomUUID().toString(),time(CREATED));
    }
    private static void insertFeedback(Schema schema,Scope scope,UUID run,String kind,String comment) {
        schema.jdbc().update("insert into "+schema.prefix()+"delegation_result_feedback(id,task_id,run_id,device_id,role_id,kind,comment,expected_revision,idempotency_key,created_at) values(?,?,?,?,?,?,?,7,?,?)",
                UUID.randomUUID(),scope.task(),run,scope.device(),scope.role(),kind,comment,UUID.randomUUID().toString(),time(CREATED));
    }
    private static OperationReceiptService operations(Schema schema) {
        String url=schema.databaseUrl()+(schema.databaseUrl().contains("?")?"&":"?")+"currentSchema="+schema.name();
        var source=new DriverManagerDataSource(url,POSTGRES.getUsername(),POSTGRES.getPassword());
        return new OperationReceiptService(new JdbcTemplate(source),new ObjectMapper(),Clock.fixed(CREATED,ZoneOffset.UTC),new DataSourceTransactionManager(source));
    }
    private static Map<String,Snapshot> capture(Schema schema,List<String> tables) {
        Map<String,Snapshot> result=new LinkedHashMap<>();
        for(String table:tables) {
            var columns=schema.jdbc().queryForList("select column_name from information_schema.columns where table_schema=? and table_name=? order by ordinal_position",String.class,schema.name(),table);
            assertThat(columns).isNotEmpty(); result.put(table,new Snapshot(columns,rows(schema,table,columns)));
        }
        return result;
    }
    private static List<Map<String,Object>> rows(Schema schema,String table,List<String> columns) {
        String selected=String.join(",",columns);
        return schema.jdbc().queryForList("select "+selected+" from "+schema.prefix()+table+" order by "+selected);
    }
    private static List<Map<String,Object>> history(Schema schema) {
        return schema.jdbc().queryForList("select version,script,checksum from "+schema.prefix()+"flyway_schema_history where success and version is not null order by installed_rank");
    }
    private static SecretCipher seedSecrets(Schema schema,UUID device) {
        var properties=new AppProperties(); properties.setSecretsEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        var cipher=new SecretCipher(properties); var jdbc=schema.jdbc(); String p=schema.prefix();
        var llm=cipher.encrypt(secret("llm"));
        jdbc.update("insert into "+p+"llm_provider_settings(id,base_url,model,api_key_ciphertext,api_key_iv,updated_at) values(1,'https://example.com/v1','synthetic-r02',?,?,?)",llm.ciphertext(),llm.initializationVector(),time(CREATED));
        var speech=cipher.encrypt(secret("speech"));
        jdbc.update("insert into "+p+"speech_provider_settings(id,base_url,asr_model,tts_model,tts_voice,api_key_ciphertext,api_key_iv,updated_at) values(1,'https://example.com/v1','synthetic-asr','synthetic-tts','synthetic-voice',?,?,?)",speech.ciphertext(),speech.initializationVector(),time(CREATED));
        var mcp=cipher.encrypt(secret("mcp"));
        jdbc.update("insert into "+p+"agent_mcp_connections(id,connection_name,url,endpoint,auth_type,bearer_token_ciphertext,bearer_token_iv,created_at,updated_at) values(?,'synthetic-r02','https://example.com','/mcp','BEARER',?,?,?,?)",UUID.randomUUID(),mcp.ciphertext(),mcp.initializationVector(),time(CREATED),time(CREATED));
        var calendar=cipher.encrypt(secret("calendar"));
        jdbc.update("insert into "+p+"icloud_calendar_connections(device_id,account_email,password_ciphertext,password_iv,status,created_at,updated_at) values(?,'synthetic-r02@example.invalid',?,?,'CONFIGURED',?,?)",device,calendar.ciphertext(),calendar.initializationVector(),time(CREATED),time(CREATED));
        var bark=cipher.encrypt(secret("bark"));
        jdbc.update("update "+p+"delegation_push_settings set key_ciphertext=?,key_iv=?,enabled=true,revision=9,updated_at=? where id=1",bark.ciphertext(),bark.initializationVector(),time(CREATED));
        return cipher;
    }
    private static void assertSecrets(Schema schema,SecretCipher cipher) {
        for(var fields:List.of(List.of("llm_provider_settings","api_key_ciphertext","api_key_iv","llm"),
                List.of("speech_provider_settings","api_key_ciphertext","api_key_iv","speech"),
                List.of("agent_mcp_connections","bearer_token_ciphertext","bearer_token_iv","mcp"),
                List.of("icloud_calendar_connections","password_ciphertext","password_iv","calendar"),
                List.of("delegation_push_settings","key_ciphertext","key_iv","bark"))) {
            var encrypted=schema.jdbc().queryForMap("select "+fields.get(1)+","+fields.get(2)+" from "+schema.prefix()+fields.getFirst());
            assertThat(digest(cipher.decrypt(new SecretCipher.EncryptedSecret((String)encrypted.get(fields.get(1)),(String)encrypted.get(fields.get(2))))))
                    .as("decrypted synthetic %s digest",fields.get(3)).isEqualTo(digest(secret(fields.get(3))));
        }
    }
    private static String digest(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String secret(String name) { return "SYNTHETIC-R02-"+name+"-NOT-A-REAL-CREDENTIAL"; }
    private static String databaseUrl(String database) {
        String url=POSTGRES.getJdbcUrl(); int query=url.indexOf('?');
        String base=query<0?url:url.substring(0,query), options=query<0?"":url.substring(query);
        return base.substring(0,base.lastIndexOf('/')+1)+database+options;
    }
    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private record Scope(UUID device,UUID role,UUID task) {}
    private record Snapshot(List<String> columns,List<Map<String,Object>> rows) {}
    private record Schema(String name,JdbcTemplate jdbc,String databaseUrl) {
        Schema(String name,JdbcTemplate jdbc) { this(name,jdbc,POSTGRES.getJdbcUrl()); }
        String prefix() { return name+"."; }
        FluentConfiguration configuration() { return Flyway.configure().dataSource(databaseUrl,POSTGRES.getUsername(),POSTGRES.getPassword()).schemas(name).defaultSchema(name); }
    }
}
