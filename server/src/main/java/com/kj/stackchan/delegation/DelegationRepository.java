package com.kj.stackchan.delegation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL persistence keeps locking and occurrence uniqueness in the same short transaction. */
@Repository
public class DelegationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public DelegationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public JdbcTemplate jdbc() { return jdbc; }

    TaskEntity task(UUID id, boolean lock) {
        return jdbc.query("select * from delegations where id=?" + (lock ? " for update" : ""),
                this::mapTask, id).stream().findFirst().orElse(null);
    }
    RunEntity run(UUID id) {
        return jdbc.query("select * from delegation_runs where id=?", this::mapRun, id)
                .stream().findFirst().orElse(null);
    }
    RunEntity slot(UUID taskId, String occurrenceKey) {
        return jdbc.query("select * from delegation_runs where task_id=? and occurrence_key=?", this::mapRun,
                taskId, occurrenceKey).stream().findFirst().orElse(null);
    }
    List<RunEntity> history(UUID taskId, int limit, int offset) {
        return jdbc.query("select * from delegation_runs where task_id=? order by created_at desc,id desc limit ? offset ?",
                this::mapRun, taskId, limit, offset);
    }
    PushSettingsEntity settings(boolean lock) {
        return jdbc.query("select * from delegation_push_settings where id=1" + (lock ? " for update" : ""),
                (rs, row) -> new PushSettingsEntity(rs.getString("server_url"), rs.getString("key_ciphertext"),
                        rs.getString("key_iv"), rs.getBoolean("enabled"), rs.getLong("revision"), instant(rs,"updated_at")))
                .getFirst();
    }
    String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ignored) { throw new IllegalStateException("DELEGATION_SERIALIZATION_FAILED"); }
    }
    <T> List<T> decode(String value, TypeReference<List<T>> type) {
        try { return json.readValue(value, type); }
        catch (Exception ignored) { throw new IllegalStateException("DELEGATION_DATA_INVALID"); }
    }
    private TaskEntity mapTask(ResultSet rs, int row) throws SQLException {
        var input = new DelegationService.TaskInput(rs.getObject("device_id", UUID.class),
                rs.getObject("role_id", UUID.class), rs.getString("title"), rs.getString("instruction"),
                decode(rs.getString("source_urls"), new TypeReference<List<String>>() {}),
                DelegationService.ScheduleType.valueOf(rs.getString("schedule_type")),
                decode(rs.getString("days_of_week"), new TypeReference<List<Integer>>() {}),
                rs.getObject("local_time", LocalTime.class), rs.getString("zone_id"), rs.getInt("max_items"),
                DelegationService.DeliveryMode.valueOf(rs.getString("delivery_mode")), rs.getBoolean("enabled"),
                rs.getString("instruction"),instant(rs,"once_at"),(Integer)rs.getObject("interval_minutes"));
        return new TaskEntity(rs.getObject("id",UUID.class), input, rs.getLong("revision"),
                instant(rs,"next_run_at"), instant(rs,"created_at"), instant(rs,"updated_at"),rs.getString("understanding"),
                rs.getBoolean("requires_tool_evidence"),decode(rs.getString("allowed_tool_names"),new TypeReference<List<String>>() {}),
                decode(rs.getString("required_tool_names"),new TypeReference<List<String>>() {}));
    }
    private RunEntity mapRun(ResultSet rs, int row) throws SQLException {
        return new RunEntity(rs.getObject("id",UUID.class), rs.getObject("task_id",UUID.class),
                rs.getString("task_title"), rs.getObject("scheduled_date",LocalDate.class),
                rs.getLong("task_revision"), rs.getInt("attempt"),
                DelegationService.RunStatus.valueOf(rs.getString("status")),
                decode(rs.getString("items"), new TypeReference<List<ResearchBriefGenerator.BriefItem>>() {}),
                decode(rs.getString("source_warnings"), new TypeReference<List<String>>() {}),
                rs.getString("error_code"), DelegationService.DeliveryStatus.valueOf(rs.getString("delivery_status")),
                rs.getInt("delivery_attempts"), instant(rs,"delivery_next_at"), instant(rs,"created_at"),
                instant(rs,"started_at"), instant(rs,"completed_at"),instant(rs,"scheduled_at"),rs.getString("occurrence_key"),
                rs.getString("output"),decode(rs.getString("steps"),new TypeReference<List<DelegationTaskExecutor.ExecutionStep>>() {}),
                decode(rs.getString("evidence"),new TypeReference<List<DelegationTaskExecutor.ExecutionEvidence>>() {}));
    }
    static Timestamp time(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
}

record TaskEntity(UUID id, DelegationService.TaskInput input, long revision, Instant nextRunAt,
                  Instant createdAt, Instant updatedAt,String understanding,boolean requiresToolEvidence,List<String> allowedToolNames,List<String> requiredToolNames) {}
record RunEntity(UUID id, UUID taskId, String taskTitle, LocalDate scheduledDate, long taskRevision, int attempt,
                 DelegationService.RunStatus status, List<ResearchBriefGenerator.BriefItem> items,
                 List<String> sourceWarnings, String errorCode, DelegationService.DeliveryStatus deliveryStatus,
                 int deliveryAttempts, Instant deliveryNextAt, Instant createdAt, Instant startedAt, Instant completedAt,
                 Instant scheduledAt,String occurrenceKey,String output,List<DelegationTaskExecutor.ExecutionStep> steps,
                 List<DelegationTaskExecutor.ExecutionEvidence> evidence) {}
record PushSettingsEntity(String serverUrl, String ciphertext, String iv, boolean enabled, long revision, Instant updatedAt) {}
