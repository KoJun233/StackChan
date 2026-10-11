package com.kj.stackchan.delegation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

import com.kj.stackchan.agent.AgentCapabilityType;
import com.kj.stackchan.agent.AgentChannel;
import com.kj.stackchan.agent.AgentInvocationContext;
import com.kj.stackchan.agent.AgentSettingsService;
import com.kj.stackchan.agent.BarkNotificationTool;
import com.kj.stackchan.llm.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.kj.stackchan.delegation.DelegationRepository.time;

/** A recipient fixed by the administrator, with one durable outbound attempt per server-owned turn/run. */
@Service
public class BarkNotificationService {
    private final DelegationRepository repo;
    private final BarkPushClient client;
    private final SecretCipher cipher;
    private final AgentSettingsService settings;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TransactionTemplate durable;

    public BarkNotificationService(DelegationRepository repo, BarkPushClient client, SecretCipher cipher,
                                   AgentSettingsService settings, Clock clock, PlatformTransactionManager manager) {
        this.repo=repo; this.client=client; this.cipher=cipher; this.settings=settings; this.clock=clock;
        tx=new TransactionTemplate(manager); durable=new TransactionTemplate(manager);
        durable.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Receipt send(AgentInvocationContext context, String title, String body) {
        if(context==null || context.turnId()==null || context.deviceId()==null || context.roleId()==null
                || context.channel()==null || context.channel()==AgentChannel.DELEGATION
                && (context.delegationRunId()==null || context.delegationAttempt()==null))
            throw new IllegalArgumentException("PUSH_SCOPE_REQUIRED");
        if(title==null || title.isBlank() || title.codePointCount(0,title.length())>80
                || body==null || body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length>BarkPushClient.MAX_BODY_BYTES)
            return new Receipt(null,"FAILED","PUSH_CONTENT_INVALID",false);
        String operation=operationKey(context.deviceId(),context.roleId(),context.delegationRunId(),context.turnId());
        String content=hash(title.length()+":"+title+body);
        UUID receiptId=UUID.randomUUID();
        boolean claimed=Boolean.TRUE.equals(durable.execute(status-> repo.jdbc().update("""
                insert into bark_notification_attempts(operation_key,content_sha256,receipt_id,status,started_at)
                values(?,?,?,'SENDING',?) on conflict(operation_key) do nothing
                """,operation,content,receiptId,time(clock.instant()))==1));
        if(!claimed) {
            return repo.jdbc().query("select * from bark_notification_attempts where operation_key=?",(rs,row)-> {
                boolean same=content.equals(rs.getString("content_sha256"));
                String state=rs.getString("status");
                return new Receipt(rs.getObject("receipt_id",UUID.class),same?known(state):"FAILED",
                        !same?"PUSH_ALREADY_ATTEMPTED":"SENDING".equals(state)?"PUSH_DELIVERY_UNKNOWN":rs.getString("error_code"),true);
            },operation).getFirst();
        }
        Receipt result;
        try {
            result=tx.execute(status-> {
                // Same lock order as scheduled delivery. Task calls already hold the device/role locks.
                boolean device= !repo.jdbc().query("select id from devices where id=? for update",(rs,row)->true,context.deviceId()).isEmpty();
                boolean role= !repo.jdbc().query("select id from companion_roles where id=? and archived_at is null for update",
                        (rs,row)->true,context.roleId()).isEmpty();
                if(!device || !role || !settings.runtimeSettings().enabled()
                        || !settings.isEnabled(AgentCapabilityType.BUILTIN_TOOL,BarkNotificationTool.ID))
                    return new Receipt(receiptId,"FAILED","PUSH_NOT_AUTHORIZED",false);
                if(context.channel()==AgentChannel.DELEGATION) {
                    var run=repo.run(context.delegationRunId());
                    var task=run==null?null:repo.task(run.taskId(),false);
                    if(task==null || !task.input().enabled() || task.revision()!=run.taskRevision()
                            || run.status()!=DelegationService.RunStatus.RUNNING || run.attempt()!=context.delegationAttempt()
                            || !task.input().deviceId().equals(context.deviceId()) || !task.input().roleId().equals(context.roleId())
                            || task.input().deliveryMode()!=DelegationService.DeliveryMode.BARK
                            || !task.allowedToolNames().contains(BarkNotificationTool.ID))
                        return new Receipt(receiptId,"FAILED","PUSH_NOT_AUTHORIZED",false);
                }
                var push=repo.settings(true);
                if(!push.enabled() || push.ciphertext()==null)
                    return new Receipt(receiptId,"FAILED","PUSH_NOT_CONFIGURED",false);
                var outcome=client.send(push.serverUrl(),cipher.decrypt(new SecretCipher.EncryptedSecret(push.ciphertext(),push.iv())),title,body);
                return new Receipt(receiptId,outcome.name(),outcome==BarkPushClient.DeliveryOutcome.SENT?null:
                        outcome==BarkPushClient.DeliveryOutcome.FAILED?"PUSH_DELIVERY_FAILED":"PUSH_DELIVERY_UNKNOWN",false);
            });
        } catch(Exception ignored) { result=new Receipt(receiptId,"UNKNOWN","PUSH_DELIVERY_UNKNOWN",false); }
        Receipt completed=result;
        try {
            durable.executeWithoutResult(status-> repo.jdbc().update("""
                    update bark_notification_attempts set status=?,error_code=?,completed_at=? where operation_key=? and status='SENDING'
                    """,completed.status(),completed.errorCode(),time(clock.instant()),operation));
        } catch(Exception ignored) {
            // The committed SENDING marker still prevents retries after a crash or database outage.
            return new Receipt(receiptId,"UNKNOWN","PUSH_DELIVERY_UNKNOWN",false);
        }
        return completed;
    }

    public Receipt receiptForRun(UUID device, UUID role, UUID run) {
        return repo.jdbc().query("select receipt_id,status,error_code from bark_notification_attempts where operation_key=?",
                (rs,row)->new Receipt(rs.getObject("receipt_id",UUID.class),known(rs.getString("status")),
                        "SENDING".equals(rs.getString("status"))?"PUSH_DELIVERY_UNKNOWN":rs.getString("error_code"),true),
                operationKey(device,role,run,null)).stream().findFirst().orElse(null);
    }

    void recordExplicitRunRetry(UUID device,UUID role,UUID run,BarkPushClient.DeliveryOutcome outcome) {
        repo.jdbc().update("update bark_notification_attempts set status=?,error_code=?,completed_at=? where operation_key=?",
                outcome.name(),outcome==BarkPushClient.DeliveryOutcome.SENT?null:"PUSH_DELIVERY_"+outcome.name(),
                time(clock.instant()),operationKey(device,role,run,null));
    }

    private static String known(String status) { return "SENDING".equals(status)?"UNKNOWN":status; }
    private static String operationKey(UUID device,UUID role,UUID run,UUID turn) {
        return hash(device+":"+role+":"+(run==null?"turn:"+turn:"run:"+run));
    }
    private static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception ignored) { throw new IllegalStateException("PUSH_FINGERPRINT_FAILED"); }
    }
    public record Receipt(UUID receiptId,String status,String errorCode,boolean deduplicated) {}
}
