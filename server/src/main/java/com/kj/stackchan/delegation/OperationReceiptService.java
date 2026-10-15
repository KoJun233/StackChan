package com.kj.stackchan.delegation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.kj.stackchan.delegation.DelegationRepository.time;

/** One durable owner per scoped intent; business changes and COMMITTED are committed together. */
@Service
public class OperationReceiptService {
    public enum Action { DELEGATION_CREATE, DELEGATION_FEEDBACK, DELEGATION_RUN_REVISION }
    public record Result(Map<String,UUID> resourceIds,long revision,String reviewState,Long resourceVersion) {}
    public record Receipt(String key,Action action,String status,Map<String,UUID> resourceIds,Long revision,
                          String errorCode,Instant createdAt,Instant updatedAt) {}
    private record CanonicalIntent(Action action,UUID resource,Object body) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;
    public OperationReceiptService(JdbcTemplate jdbc,ObjectMapper json,Clock clock,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.json=json; this.clock=clock; this.tx=new TransactionTemplate(manager);
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        jdbc.update("update companion_operation_receipts set status='FAILED',error_code='DEPENDENCY_UNAVAILABLE',updated_at=? where status='PENDING'",
                time(clock.instant()));
    }
    public Result perform(UUID device,UUID role,Action action,String key,UUID resource,Object canonical,Supplier<Result> operation) {
        validateKey(key);
        if(device==null || role==null) throw new DelegationException("INVALID_REQUEST");
        String digest=digest(new CanonicalIntent(action,resource,canonical)); Instant now=clock.instant();
        boolean owner=Boolean.TRUE.equals(tx.execute(s -> jdbc.update("""
                insert into companion_operation_receipts(device_id,role_id,action,operation_key,resource_id,request_digest,status,created_at,updated_at)
                values(?,?,?,?,?,?,'PENDING',?,?) on conflict do nothing
                """,device,role,action.name(),key,resource,digest,time(now),time(now))==1));
        if(!owner) return replay(device,role,action,key,digest);
        try {
            return tx.execute(s -> {
                // Lock before business writes so a query never observes COMMITTED without its resource.
                jdbc.queryForObject("select operation_key from companion_operation_receipts where device_id=? and role_id=? and action=? and operation_key=? for update",
                        String.class,device,role,action.name(),key);
                Result result=operation.get();
                jdbc.update("update companion_operation_receipts set status='COMMITTED',result_metadata=?,resource_id=coalesce(resource_id,?),updated_at=? where device_id=? and role_id=? and action=? and operation_key=?",
                        encode(result),result.resourceIds().get("taskId"),time(clock.instant()),device,role,action.name(),key);
                return result;
            });
        } catch(RuntimeException error) {
            String code=error instanceof DelegationException d?d.code():"DEPENDENCY_UNAVAILABLE";
            tx.executeWithoutResult(s -> jdbc.update("update companion_operation_receipts set status='FAILED',error_code=?,updated_at=? where device_id=? and role_id=? and action=? and operation_key=? and status='PENDING'",
                    code,time(clock.instant()),device,role,action.name(),key));
            throw error;
        }
    }
    private Result replay(UUID device,UUID role,Action action,String key,String digest) {
        return tx.execute(s -> jdbc.query("select * from companion_operation_receipts where device_id=? and role_id=? and action=? and operation_key=? for update",
                (rs,row) -> {
                    if(!digest.equals(rs.getString("request_digest"))) throw new DelegationException("IDEMPOTENCY_CONFLICT");
                    if("FAILED".equals(rs.getString("status"))) throw new DelegationException(rs.getString("error_code"));
                    if(!"COMMITTED".equals(rs.getString("status"))) throw new DelegationException("OPERATION_PENDING");
                    return decode(rs.getString("result_metadata"));
                },device,role,action.name(),key).getFirst());
    }
    public Receipt get(UUID device,UUID role,Action action,String key,UUID resource) {
        validateKey(key);
        if(device==null || role==null || action==null) throw new DelegationException("INVALID_REQUEST");
        return jdbc.query("select * from companion_operation_receipts where device_id=? and role_id=? and action=? and operation_key=? and (?::uuid is null or resource_id=?::uuid)",
                (rs,row) -> {
                    Result result=rs.getString("result_metadata")==null?null:decode(rs.getString("result_metadata"));
                    return new Receipt(key,action,rs.getString("status"),result==null?Map.of():result.resourceIds(),
                            result==null?null:result.revision(),rs.getString("error_code"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
                },device,role,action.name(),key,resource,resource).stream().findFirst()
                .orElse(new Receipt(key,action,"NOT_FOUND",Map.of(),null,null,null,null));
    }
    private static void validateKey(String key) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{16,128}")) throw new DelegationException("INVALID_REQUEST");
    }
    private String digest(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new DelegationException("INVALID_REQUEST"); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch(Exception e) { throw new DelegationException("INVALID_REQUEST"); }
    }
    private Result decode(String value) {
        try { return json.readValue(value,Result.class); } catch(Exception e) { throw new DelegationException("DEPENDENCY_UNAVAILABLE"); }
    }
}
