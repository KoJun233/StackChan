package com.kj.stackchan.delegation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

import com.kj.stackchan.llm.SecretCipher;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static com.kj.stackchan.delegation.DelegationRepository.time;
import static com.kj.stackchan.delegation.DelegationTaskExecutor.*;

@Service
public class DelegationService {
    private final DelegationRepository repo;
    private final DelegationTaskPlanner planner;
    private final DelegationTaskExecutor taskExecutor;
    private final BarkPushClient bark;
    private final PublicHttpsUrlPolicy urls;
    private final SecretCipher cipher;
    private final Clock clock;
    private final TransactionTemplate tx;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private BarkNotificationService notifications;
    // No pending job queue: capacity is two independent, bounded workers.
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r,"delegation-worker"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());

    public DelegationService(DelegationRepository repo, DelegationTaskPlanner planner, DelegationTaskExecutor taskExecutor, BarkPushClient bark,
                             PublicHttpsUrlPolicy urls, SecretCipher cipher, Clock clock,
                             PlatformTransactionManager transactionManager) {
        this.repo=repo; this.planner=planner; this.taskExecutor=taskExecutor; this.bark=bark; this.urls=urls; this.cipher=cipher;
        this.clock=clock; this.tx=new TransactionTemplate(transactionManager);
    }
    @PreDestroy public void stop() { executor.shutdownNow(); }

    public TaskPage list(UUID deviceId, UUID roleId, int from, int limit) {
        int size = size(limit), offset = Math.max(from,0);
        String filter = " where (?::uuid is null or device_id=?::uuid) and (?::uuid is null or role_id=?::uuid)";
        List<UUID> ids = repo.jdbc().query("select id from delegations"+filter+" order by created_at desc,id desc limit ? offset ?",
                (rs,row)->rs.getObject(1,UUID.class),deviceId,deviceId,roleId,roleId,size,offset);
        long total = repo.jdbc().queryForObject("select count(*) from delegations"+filter,Long.class,deviceId,deviceId,roleId,roleId);
        return new TaskPage(ids.stream().map(this::get).toList(),total);
    }
    public TaskSnapshot get(UUID id) { return snapshot(requireTask(id)); }
    public TaskSnapshot create(TaskInput raw) {
        Prepared prepared = prepare(raw); TaskInput input = prepared.input();
        return tx.execute(status -> {
            requireNotInterrupted();
            lockScope(input.deviceId(),input.roleId(),true);
            requireDelivery(input);
            Instant now=clock.instant(); UUID id=UUID.randomUUID();
            repo.jdbc().update("""
                    insert into delegations(id,device_id,role_id,title,instruction,source_urls,schedule_type,days_of_week,
                      local_time,zone_id,max_items,delivery_mode,enabled,revision,next_run_at,created_at,updated_at,
                      understanding,requires_tool_evidence,allowed_tool_names,required_tool_names,once_at,interval_minutes)
                    values(?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?,?,?,?,?,?,?)
                    """,id,input.deviceId(),input.roleId(),input.title(),input.instruction(),repo.encode(input.sourceUrls()),
                    input.scheduleType().name(),repo.encode(input.daysOfWeek()),input.localTime(),input.zoneId(),input.maxItems(),
                    input.deliveryMode().name(),input.enabled(),time(input.enabled()?DelegationSchedule.next(input,now):null),time(now),time(now),
                    prepared.understanding(),prepared.requiresToolEvidence(),repo.encode(prepared.allowedToolNames()),repo.encode(prepared.requiredToolNames()),time(input.onceAt()),input.intervalMinutes());
            return get(id);
        });
    }
    public TaskSnapshot update(UUID id,TaskInput raw) {
        Prepared prepared=prepareUpdate(raw,requireTask(id)); TaskInput input=prepared.input();
        return tx.execute(status -> {
            requireNotInterrupted();
            TaskEntity task=lockTask(id,true);
            if (!task.input().deviceId().equals(input.deviceId()) || !task.input().roleId().equals(input.roleId()))
                throw new DelegationException("DELEGATION_SCOPE_IMMUTABLE");
            requireDelivery(input); Instant now=clock.instant();
            invalidate(id,now);
            repo.jdbc().update("""
                    update delegations set title=?,instruction=?,source_urls=?,schedule_type=?,days_of_week=?,local_time=?,
                    zone_id=?,max_items=?,delivery_mode=?,enabled=?,revision=revision+1,next_run_at=?,updated_at=?,
                    understanding=?,requires_tool_evidence=?,allowed_tool_names=?,required_tool_names=?,once_at=?,interval_minutes=? where id=?
                    """,input.title(),input.instruction(),repo.encode(input.sourceUrls()),input.scheduleType().name(),
                    repo.encode(input.daysOfWeek()),input.localTime(),input.zoneId(),input.maxItems(),input.deliveryMode().name(),
                    input.enabled(),time(input.enabled()?DelegationSchedule.next(input,now):null),time(now),
                    prepared.understanding(),prepared.requiresToolEvidence(),repo.encode(prepared.allowedToolNames()),repo.encode(prepared.requiredToolNames()),time(input.onceAt()),input.intervalMinutes(),id);
            return get(id);
        });
    }
    public TaskSnapshot setEnabled(UUID id,boolean enabled) {
        return tx.execute(status -> {
            TaskEntity task=lockTask(id,enabled); Instant now=clock.instant();
            if(task.input().enabled()==enabled) return snapshot(task);
            TaskInput input=withEnabled(task.input(),enabled); requireDelivery(input); invalidate(id,now);
            repo.jdbc().update("update delegations set enabled=?,revision=revision+1,next_run_at=?,updated_at=? where id=?",
                    enabled,time(enabled?DelegationSchedule.next(input,now):null),time(now),id);
            return get(id);
        });
    }
    public void delete(UUID id) {
        tx.executeWithoutResult(status -> { lockTask(id,false); repo.jdbc().update("delete from delegations where id=?",id); });
    }
    private void invalidate(UUID taskId,Instant now) {
        repo.jdbc().update("""
                update delegation_runs set status=case when status='RUNNING' then 'CANCELLED' else status end,
                    completed_at=case when status='RUNNING' then ? else completed_at end,
                    error_code=case when status='RUNNING' then 'DELEGATION_CHANGED' else error_code end,
                    delivery_status=case when delivery_status in ('PENDING','FAILED') then 'SKIPPED' else delivery_status end,
                    delivery_next_at=null where task_id=?
                """,time(now),taskId);
    }

    public RunPage runs(UUID taskId,int from,int limit) {
        requireTask(taskId);
        return new RunPage(repo.history(taskId,size(limit),Math.max(from,0)).stream().map(this::snapshot).toList(),
                repo.jdbc().queryForObject("select count(*) from delegation_runs where task_id=?",Long.class,taskId));
    }
    public List<RunSnapshot> latestForDeviceRole(UUID deviceId,UUID roleId) {
        if(deviceId==null || roleId==null) return List.of();
        List<UUID> ids=repo.jdbc().query("""
                select r.id from delegation_runs r join delegations d on d.id=r.task_id
                  join companion_roles cr on cr.id=d.role_id
                where d.device_id=? and d.role_id=? and cr.archived_at is null
                order by coalesce(r.completed_at,r.created_at) desc,r.id desc limit 3
                """,(rs,row)->rs.getObject(1,UUID.class),deviceId,roleId);
        return ids.stream().map(repo::run).filter(Objects::nonNull).map(this::snapshot).toList();
    }
    public RunSnapshot run(UUID taskId) { return start(taskId,false); }
    private RunSnapshot start(UUID taskId,boolean scheduled) {
        Claim claim=tx.execute(status -> {
            TaskEntity task=lockTask(taskId,true); TaskInput input=task.input();
            if(!input.enabled()) throw new DelegationException("DELEGATION_DISABLED");
            Instant now=clock.instant(); LocalDate date=LocalDate.ofInstant(now,ZoneId.of(input.zoneId()));
            if(scheduled && (task.nextRunAt()==null || task.nextRunAt().isAfter(now))) return null;
            Instant scheduledAt=DelegationSchedule.occurrence(input,now);
            if(scheduled) {
                boolean todayAllowed=input.scheduleType()!=ScheduleType.WEEKLY || input.daysOfWeek().contains(date.getDayOfWeek().getValue());
                if(!todayAllowed || scheduledAt.isAfter(now)) {
                    repo.jdbc().update("update delegations set next_run_at=? where id=?",time(DelegationSchedule.next(input,now)),taskId);
                    return null;
                }
            }
            // Advance to a future local occurrence, skipping missed dates without catching up repeatedly.
            if(scheduled || task.nextRunAt()!=null && !task.nextRunAt().isAfter(now))
                repo.jdbc().update("update delegations set next_run_at=? where id=?",time(DelegationSchedule.next(input,now)),taskId);
            String occurrenceKey=DelegationSchedule.occurrenceKey(input,scheduledAt,date);
            RunEntity existing=repo.slot(taskId,occurrenceKey);
            if(existing!=null && (scheduled || existing.status()!=RunStatus.FAILED && existing.status()!=RunStatus.CANCELLED && existing.status()!=RunStatus.BLOCKED))
                return new Claim(task,existing,false);
            UUID id=existing==null?UUID.randomUUID():existing.id();
            if(existing==null) {
                repo.jdbc().update("""
                        insert into delegation_runs(id,task_id,task_title,scheduled_date,task_revision,attempt,status,created_at,started_at,
                            occurrence_key,scheduled_at)
                        values(?,?,?,?,?,1,'RUNNING',?,?,?,?)
                        """,id,taskId,input.title(),date,task.revision(),time(now),time(now),occurrenceKey,time(scheduledAt));
            } else {
                repo.jdbc().update("""
                        update delegation_runs set task_title=?,task_revision=?,attempt=attempt+1,status='RUNNING',items='[]',
                          source_warnings='[]',output='',steps='[]',evidence='[]',error_code=null,delivery_status='NONE',delivery_attempts=0,delivery_next_at=null,
                          started_at=?,completed_at=null where id=?
                        """,input.title(),task.revision(),time(now),id);
            }
            return new Claim(task,repo.run(id),true);
        });
        if(claim==null) return null;
        if(claim.execute()) {
            try { executor.execute(()->generate(claim)); }
            catch(RejectedExecutionException ignored) { fail(claim,"DELEGATION_CAPACITY"); }
        }
        return snapshot(repo.run(claim.run().id()));
    }
    private void generate(Claim claim) {
        try {
            var input=claim.task().input();
            var result=taskExecutor.execute(new TaskExecutionRequest(claim.task().id(),claim.run().id(),claim.run().attempt(),
                    input.deviceId(),input.roleId(),input.title(),input.instruction(),claim.task().understanding(),
                    claim.run().scheduledAt(),claim.task().requiresToolEvidence(),claim.task().allowedToolNames(),claim.task().requiredToolNames(),
                    new LegacyHints(input.sourceUrls(),input.maxItems()),operation -> guardedOperation(claim,operation)));
            result=validateResult(result,claim.task());
            var delivered=notifications==null?null:notifications.receiptForRun(input.deviceId(),input.roleId(),claim.run().id());
            if(delivered!=null && !"SENT".equals(delivered.status()) && result.status()==RunStatus.SUCCEEDED)
                result=new TaskExecutionResult("手机通知未获得 Bark 接受确认，请先检查手机历史；本次不自动重发。",RunStatus.BLOCKED,result.steps(),result.evidence());
            var completedResult=result;
            boolean saved=Boolean.TRUE.equals(tx.execute(status -> {
                TaskEntity task=lockTaskOrNull(claim.task().id());
                if(task==null) return false;
                RunEntity run=repo.run(claim.run().id());
                if(!current(claim,task,run)) return false;
                boolean success=completedResult.status()==RunStatus.SUCCEEDED; Instant finished=clock.instant();
                var phoneReceipt=notifications==null?null:notifications.receiptForRun(input.deviceId(),input.roleId(),run.id());
                int changed=repo.jdbc().update("""
                        update delegation_runs set status=?,output=?,steps=?,evidence=?,completed_at=?,delivery_status=?,error_code=?
                        where id=? and status='RUNNING' and attempt=? and task_revision=?
                        """,completedResult.status().name(),completedResult.output(),repo.encode(completedResult.steps()),
                        repo.encode(completedResult.evidence()),time(finished),
                        phoneReceipt!=null?phoneReceipt.status():success && input.deliveryMode()==DeliveryMode.BARK?"PENDING":"NONE",
                        completedResult.status()==RunStatus.BLOCKED?"DELEGATION_CAPABILITY_BLOCKED":null,
                        run.id(),claim.run().attempt(),claim.task().revision());
                if(phoneReceipt!=null) repo.jdbc().update("update delegation_runs set delivery_attempts=1,delivery_next_at=null where id=?",run.id());
                return changed==1 && phoneReceipt==null && success && input.deliveryMode()==DeliveryMode.BARK;
            }));
            if(saved) deliver(claim.run().id(),false);
        } catch(ResearchException exception) { fail(claim,exception.code()); }
        catch(DelegationException exception) { fail(claim,exception.code()); }
        catch(Exception ignored) { fail(claim,"DELEGATION_GENERATION_FAILED"); }
    }
    private Object guardedOperation(Claim claim,java.util.function.Supplier<Object> operation) {
        return tx.execute(status -> {
            TaskEntity task=lockTaskOrNull(claim.task().id());
            RunEntity run=task==null?null:repo.run(claim.run().id());
            if(task==null || !current(claim,task,run)) throw new DelegationException("DELEGATION_CHANGED");
            return operation.get();
        });
    }
    private TaskExecutionResult validateResult(TaskExecutionResult result,TaskEntity task) {
        if(result==null || result.output()==null || result.output().length()>32768 || result.steps()==null
                || result.evidence()==null || result.steps().size()>20 || result.evidence().size()>20
                || result.status()!=RunStatus.SUCCEEDED && result.status()!=RunStatus.NO_UPDATES && result.status()!=RunStatus.BLOCKED)
            throw new DelegationException("DELEGATION_RESULT_INVALID");
        if(result.status()==RunStatus.SUCCEEDED && result.output().isBlank()) throw new DelegationException("DELEGATION_RESULT_INVALID");
        for(var step:result.steps()) {
            if(step==null) throw new DelegationException("DELEGATION_RESULT_INVALID");
            text(step.toolName(),160); text(step.status(),32); text(step.summary(),1000);
        }
        for(var evidence:result.evidence()) {
            if(evidence==null) throw new DelegationException("DELEGATION_RESULT_INVALID");
            text(evidence.title(),200);
            if(evidence.detail()!=null && evidence.detail().length()>2000 || evidence.url()!=null && evidence.url().length()>2048)
                throw new DelegationException("DELEGATION_RESULT_INVALID");
            if(evidence.url()!=null && !evidence.url().isBlank()) {
                try { urls.validateSyntax(evidence.url()); }
                catch(Exception ignored) { throw new DelegationException("DELEGATION_RESULT_INVALID"); }
            }
        }
        List<String> required=task.requiredToolNames();
        if(required.isEmpty() && task.allowedToolNames().isEmpty() && !task.input().sourceUrls().isEmpty()) required=List.of("read_public_source");
        if(result.status()==RunStatus.SUCCEEDED && task.requiresToolEvidence()
                && (required.isEmpty() || required.stream().anyMatch(name->result.steps().stream()
                .noneMatch(step->name.equals(step.toolName()) && "SUCCEEDED".equals(step.status())))))
            return new TaskExecutionResult("当前已接入的能力没有完成这项任务，未执行所要求的外部操作。",RunStatus.BLOCKED,
                    result.steps(),result.evidence());
        return new TaskExecutionResult(result.output(),result.status(),List.copyOf(result.steps()),List.copyOf(result.evidence()));
    }
    private boolean current(Claim claim,TaskEntity task,RunEntity run) {
        if(run==null || run.status()!=RunStatus.RUNNING || run.attempt()!=claim.run().attempt()) return false;
        if(!run.startedAt().plus(Duration.ofMinutes(10)).isAfter(clock.instant())) {
            repo.jdbc().update("""
                    update delegation_runs set status='FAILED',error_code='DELEGATION_INTERRUPTED',completed_at=?
                    where id=? and status='RUNNING' and attempt=? and task_revision=?
                    """,time(clock.instant()),run.id(),claim.run().attempt(),claim.task().revision());
            return false;
        }
        if(task.revision()!=claim.task().revision() || !task.input().enabled() || !roleActive(task.input().roleId())) {
            repo.jdbc().update("""
                    update delegation_runs set status='CANCELLED',error_code='DELEGATION_CHANGED',completed_at=?
                    where id=? and status='RUNNING' and attempt=? and task_revision=?
                    """,time(clock.instant()),run.id(),claim.run().attempt(),claim.task().revision()); return false;
        }
        return true;
    }
    private void fail(Claim claim,String code) {
        tx.executeWithoutResult(status -> {
            TaskEntity task=lockTaskOrNull(claim.task().id());
            if(task==null) return;
            RunEntity run=repo.run(claim.run().id());
            if(!current(claim,task,run)) return;
            repo.jdbc().update("""
                    update delegation_runs set status='FAILED',error_code=?,completed_at=?
                    where id=? and status='RUNNING' and attempt=? and task_revision=?
                    """,safeCode(code),time(clock.instant()),run.id(),claim.run().attempt(),claim.task().revision());
            var delivered=notifications==null?null:notifications.receiptForRun(task.input().deviceId(),task.input().roleId(),run.id());
            if(delivered!=null) repo.jdbc().update("update delegation_runs set delivery_status=?,delivery_attempts=1,delivery_next_at=null where id=?",
                    delivered.status(),run.id());
        });
    }

    public RunSnapshot retryDelivery(UUID runId) {
        RunEntity run=repo.run(runId);
        if(run==null) throw missing();
        if(run.status()!=RunStatus.SUCCEEDED || run.deliveryStatus()!=DeliveryStatus.FAILED)
            throw new DelegationException("DELEGATION_DELIVERY_NOT_RETRYABLE");
        deliver(runId,true); return snapshot(repo.run(runId));
    }
    private void deliver(UUID runId,boolean manual) {
        // A durable marker is committed before outbound I/O. A process crash leaves SENDING and is recovered as UNKNOWN.
        boolean claimed=Boolean.TRUE.equals(tx.execute(status -> {
            RunEntity initial=repo.run(runId); if(initial==null) return false;
            TaskEntity task=lockTaskOrNull(initial.taskId()); if(task==null) return false;
            RunEntity run=repo.run(runId);
            PushSettingsEntity settings=repo.settings(true);
            if(!canDeliver(task,run,settings)) { skipDelivery(runId); return false; }
            if(run.deliveryStatus()!=DeliveryStatus.PENDING && run.deliveryStatus()!=DeliveryStatus.FAILED) return false;
            if(!manual && run.deliveryStatus()==DeliveryStatus.FAILED
                    && (run.deliveryAttempts()>=3 || run.deliveryNextAt()==null || run.deliveryNextAt().isAfter(clock.instant()))) return false;
            repo.jdbc().update("update delegation_runs set delivery_status='SENDING',delivery_attempts=delivery_attempts+1,delivery_next_at=null where id=?",runId);
            return true;
        }));
        if(!claimed) return;
        try {
            tx.executeWithoutResult(status -> {
                RunEntity initial=repo.run(runId); if(initial==null) return;
                TaskEntity task=lockTaskOrNull(initial.taskId()); if(task==null) return;
                RunEntity run=repo.run(runId); PushSettingsEntity settings=repo.settings(true);
                if(run==null || run.deliveryStatus()!=DeliveryStatus.SENDING) return;
                if(!canDeliver(task,run,settings)) { skipDelivery(runId); return; }
                // The bounded HTTPS client runs under these locks, so pause/delete/disable cannot complete before a late send.
                String body;
                try { body=run.output().isBlank()?BarkPushClient.renderBody(run.items()):BarkPushClient.renderBody(run.output(),run.evidence()); }
                catch(IllegalArgumentException ignored) {
                    repo.jdbc().update("update delegation_runs set delivery_status='FAILED',error_code='PUSH_CONTENT_TOO_LARGE',delivery_next_at=null where id=?",runId);
                    return;
                }
                var outcome=bark.send(settings.serverUrl(),cipher.decrypt(new SecretCipher.EncryptedSecret(settings.ciphertext(),settings.iv())),run.taskTitle(),body);
                if(notifications!=null) notifications.recordExplicitRunRetry(task.input().deviceId(),task.input().roleId(),run.id(),outcome);
                Instant retry=outcome==BarkPushClient.DeliveryOutcome.FAILED && run.deliveryAttempts()<3
                        ?clock.instant().plus(Duration.ofMinutes(run.deliveryAttempts()==1?1:5)):null;
                String code=outcome==BarkPushClient.DeliveryOutcome.FAILED?"DELEGATION_DELIVERY_FAILED":
                        outcome==BarkPushClient.DeliveryOutcome.UNKNOWN?"DELEGATION_DELIVERY_UNKNOWN":null;
                repo.jdbc().update("update delegation_runs set delivery_status=?,delivery_next_at=?,error_code=? where id=?",outcome.name(),time(retry),code,runId);
            });
        } catch(Exception ignored) {
            // Never repeat an outbound request whose outcome might be unknown.
            repo.jdbc().update("update delegation_runs set delivery_status='UNKNOWN',error_code='DELEGATION_DELIVERY_UNKNOWN',delivery_next_at=null where id=? and delivery_status='SENDING'",runId);
        }
    }
    private boolean canDeliver(TaskEntity task,RunEntity run,PushSettingsEntity settings) {
        return run!=null && run.status()==RunStatus.SUCCEEDED && task.input().enabled()
                && task.input().deliveryMode()==DeliveryMode.BARK && task.revision()==run.taskRevision()
                && roleActive(task.input().roleId()) && settings.enabled() && settings.ciphertext()!=null;
    }
    private void skipDelivery(UUID id) {
        repo.jdbc().update("update delegation_runs set delivery_status='SKIPPED',delivery_next_at=null where id=? and delivery_status in ('PENDING','FAILED','SENDING')",id);
    }

    public PushSettingsSnapshot pushSettings() { return snapshot(repo.settings(false)); }
    public PushSettingsSnapshot savePushSettings(PushSettingsInput input) {
        if(input==null || input.serverUrl()==null) throw new DelegationException("DELEGATION_PUSH_INVALID");
        String serverUrl=input.serverUrl().trim();
        validateUrl(serverUrl,"DELEGATION_PUSH_URL_INVALID");
        if(java.net.URI.create(serverUrl).getRawQuery()!=null) throw new DelegationException("DELEGATION_PUSH_URL_INVALID");
        String key=input.deviceKey()==null?"":input.deviceKey().trim();
        if(!key.isEmpty() && !key.matches("[A-Za-z0-9_-]{1,512}")) throw new DelegationException("DELEGATION_PUSH_KEY_INVALID");
        if(input.clearDeviceKey() && !key.isEmpty()) throw new DelegationException("DELEGATION_PUSH_KEY_CONFLICT");
        SecretCipher.EncryptedSecret encrypted=key.isEmpty()?null:cipher.encrypt(key);
        return tx.execute(status -> {
            PushSettingsEntity old=repo.settings(true);
            String ciphertext=input.clearDeviceKey()?null:encrypted==null?old.ciphertext():encrypted.ciphertext();
            String iv=input.clearDeviceKey()?null:encrypted==null?old.iv():encrypted.initializationVector();
            if(input.enabled() && ciphertext==null) throw new DelegationException("DELEGATION_PUSH_KEY_REQUIRED");
            repo.jdbc().update("update delegation_push_settings set server_url=?,key_ciphertext=?,key_iv=?,enabled=?,revision=revision+1,updated_at=? where id=1",
                    serverUrl,ciphertext,iv,input.enabled(),time(clock.instant()));
            return pushSettings();
        });
    }

    public void recover(boolean startup) {
        Instant now=clock.instant();
        repo.jdbc().update("""
                update delegation_runs set status='FAILED',error_code='DELEGATION_INTERRUPTED',completed_at=?
                where status='RUNNING' and (? or started_at<?)
                """,time(now),startup,time(now.minus(Duration.ofMinutes(10))));
        if(startup) repo.jdbc().update("update delegation_runs set delivery_status='UNKNOWN',delivery_next_at=null where delivery_status='SENDING'");
    }
    public void tick() {
        recover(false);
        var due=repo.jdbc().query("select id from delegations where enabled and next_run_at<=? order by next_run_at limit 20",
                (rs,row)->rs.getObject(1,UUID.class),time(clock.instant()));
        for(UUID id:due) {
            if(executor.getActiveCount()>=2) break;
            try { start(id,true); } catch(DelegationException ignored) {
                // Archived roles cannot resume themselves. FK cascades account for concurrently deleted scopes.
                tx.executeWithoutResult(status -> {
                    TaskEntity task=lockTaskOrNull(id);
                    if(task!=null && !roleActive(task.input().roleId())) {
                        invalidate(id,clock.instant());
                        repo.jdbc().update("update delegations set enabled=false,next_run_at=null,revision=revision+1,updated_at=? where id=?",time(clock.instant()),id);
                    }
                });
            }
        }
        List<UUID> retry=repo.jdbc().query("select id from delegation_runs where delivery_status='FAILED' and delivery_attempts<3 and delivery_next_at<=? order by delivery_next_at limit 10",
                (rs,row)->rs.getObject(1,UUID.class),time(clock.instant()));
        for(UUID id:retry) {
            try { executor.execute(()->deliver(id,false)); }
            catch(RejectedExecutionException ignored) { break; }
        }
        List<UUID> pending=repo.jdbc().query("select id from delegation_runs where delivery_status='PENDING' order by completed_at limit 10",(rs,row)->rs.getObject(1,UUID.class));
        for(UUID id:pending) {
            try { executor.execute(()->deliver(id,false)); }
            catch(RejectedExecutionException ignored) { break; }
        }
    }

    private Prepared prepareUpdate(TaskInput raw,TaskEntity existing) {
        if(raw!=null && raw.scheduleType()==null && Objects.equals(raw.description(),existing.input().instruction())) {
            var old=existing.input();
            TaskInput preserved=new TaskInput(raw.deviceId(),raw.roleId(),old.title(),old.instruction(),old.sourceUrls(),
                    old.scheduleType(),old.daysOfWeek(),old.localTime(),raw.zoneId()==null?old.zoneId():raw.zoneId(),old.maxItems(),
                    raw.deliveryMode()==null?old.deliveryMode():raw.deliveryMode(),raw.enabled(),old.instruction(),old.onceAt(),old.intervalMinutes());
            boolean changedSettings=!Objects.equals(preserved.zoneId(),old.zoneId()) || preserved.deliveryMode()!=old.deliveryMode();
            String understanding=existing.understanding();
            if(changedSettings) understanding=understanding.substring(0,Math.min(understanding.length(),3500))
                    +"\n当前安排："+preserved.scheduleType()+"，"+preserved.localTime()+"，"+preserved.zoneId()+"，交付="+preserved.deliveryMode();
            return new Prepared(validate(preserved),understanding,existing.requiresToolEvidence(),existing.allowedToolNames(),existing.requiredToolNames());
        }
        return prepare(raw);
    }
    private Prepared prepare(TaskInput raw) {
        if(raw==null || raw.deviceId()==null || raw.roleId()==null) throw new DelegationException("DELEGATION_INPUT_INVALID");
        if(raw.description()==null || raw.description().isBlank()) {
            TaskInput legacy=validate(raw);
            return new Prepared(legacy,legacy.instruction(),true,List.of(),List.of());
        }
        String description=text(raw.description(),4000);
        String zone=raw.zoneId()==null || raw.zoneId().isBlank()?"Asia/Shanghai":raw.zoneId().trim();
        try { ZoneId.of(zone); } catch(Exception ignored) { throw new DelegationException("DELEGATION_ZONE_INVALID"); }
        String planningDescription=description;
        if(raw.scheduleType()!=null) planningDescription += "\n用户明确指定安排："+raw.scheduleType()+"，时区="+zone
                +"，当地时间="+raw.localTime()+"，星期="+raw.daysOfWeek()+"，首次时间="+raw.onceAt()+"，间隔分钟="+raw.intervalMinutes();
        var plan=planner.plan(new DelegationTaskPlanner.PlanningRequest(raw.deviceId(),raw.roleId(),planningDescription,zone,
                clock.instant(),raw.deliveryMode()));
        requireNotInterrupted();
        if(plan==null) throw new DelegationException("DELEGATION_PLANNING_FAILED");
        if(plan.clarification()!=null && !plan.clarification().isBlank())
            throw new DelegationException("DELEGATION_CLARIFICATION_REQUIRED",text(plan.clarification(),1000));
        TaskInput input=new TaskInput(raw.deviceId(),raw.roleId(),raw.title()==null?plan.title():raw.title(),description,
                raw.sourceUrls(),raw.scheduleType()==null?plan.scheduleType():raw.scheduleType(),
                raw.scheduleType()==null?plan.daysOfWeek():raw.daysOfWeek(),raw.scheduleType()==null?plan.localTime():raw.localTime(),
                raw.scheduleType()==null?plan.zoneId():zone,raw.maxItems()==0?5:raw.maxItems(),
                raw.deliveryMode()==null?plan.deliveryMode():raw.deliveryMode(),raw.enabled(),description,
                raw.scheduleType()==null?plan.onceAt():raw.onceAt(),raw.scheduleType()==null?plan.intervalMinutes():raw.intervalMinutes());
        List<String> allowed=plan.allowedToolNames()==null?List.of():plan.allowedToolNames();
        if(allowed.size()>100 || allowed.stream().anyMatch(name->name==null || !name.matches("[A-Za-z0-9_.:-]{1,160}")))
            throw new DelegationException("DELEGATION_PLAN_INVALID");
        List<String> required=plan.requiredToolNames()==null?List.of():plan.requiredToolNames();
        if(required.size()>100 || !allowed.containsAll(required)) throw new DelegationException("DELEGATION_PLAN_INVALID");
        return new Prepared(validate(input),text(plan.understanding(),4000),plan.requiresToolEvidence(),allowed.stream().distinct().toList(),required.stream().distinct().toList());
    }
    private TaskInput validate(TaskInput input) {
        if(input==null || input.deviceId()==null || input.roleId()==null || input.scheduleType()==null || input.deliveryMode()==null
                || input.maxItems()<0 || input.maxItems()>10) throw new DelegationException("DELEGATION_INPUT_INVALID");
        String title=text(input.title(),200), instruction=text(input.instruction(),4000);
        String zone=input.zoneId()==null?"Asia/Shanghai":input.zoneId().trim();
        try { ZoneId.of(zone); } catch(Exception ignored) { throw new DelegationException("DELEGATION_ZONE_INVALID"); }
        LocalTime localTime=input.localTime();
        if(localTime==null && (input.scheduleType()==ScheduleType.DAILY || input.scheduleType()==ScheduleType.WEEKLY))
            throw new DelegationException("DELEGATION_TIME_INVALID");
        if(localTime==null) localTime=LocalTime.MIDNIGHT;
        if(localTime.getSecond()!=0 || localTime.getNano()!=0) throw new DelegationException("DELEGATION_TIME_INVALID");
        List<String> suppliedSources=input.sourceUrls()==null?List.of():input.sourceUrls();
        if(suppliedSources.size()>8) throw new DelegationException("DELEGATION_SOURCES_INVALID");
        List<String> sources=new ArrayList<>();
        for(String source:suppliedSources) {
            if(source==null || source.length()>2048) throw new DelegationException("DELEGATION_SOURCES_INVALID");
            String normalized=source.trim(); validateUrl(normalized,"DELEGATION_SOURCE_URL_INVALID");
            if(!sources.contains(normalized)) sources.add(normalized);
        }
        List<Integer> suppliedDays=input.daysOfWeek()==null?List.of():input.daysOfWeek();
        if(suppliedDays.stream().anyMatch(d->d==null || d<1 || d>7) || input.scheduleType()==ScheduleType.WEEKLY && suppliedDays.isEmpty())
            throw new DelegationException("DELEGATION_DAYS_INVALID");
        List<Integer> days=input.scheduleType()==ScheduleType.WEEKLY?suppliedDays.stream().distinct().sorted().toList():List.of();
        Instant onceAt=input.onceAt(); Integer interval=input.intervalMinutes();
        if(input.scheduleType()==ScheduleType.ONCE && onceAt==null) throw new DelegationException("DELEGATION_TIME_INVALID");
        if(input.scheduleType()==ScheduleType.INTERVAL) {
            if(interval==null || interval<1 || interval>525600) throw new DelegationException("DELEGATION_INTERVAL_INVALID");
            if(onceAt==null) onceAt=clock.instant();
        } else interval=null;
        if(input.scheduleType()==ScheduleType.DAILY || input.scheduleType()==ScheduleType.WEEKLY) onceAt=null;
        return new TaskInput(input.deviceId(),input.roleId(),title,instruction,List.copyOf(sources),input.scheduleType(),days,
                localTime,zone,input.maxItems()==0?5:input.maxItems(),input.deliveryMode(),input.enabled(),instruction,onceAt,interval);
    }
    private void validateUrl(String url,String code) {
        try { urls.validate(url); } catch(Exception ignored) { throw new DelegationException(code); }
    }
    private void requireDelivery(TaskInput input) {
        if(input.enabled() && input.deliveryMode()==DeliveryMode.BARK) {
            PushSettingsEntity settings=repo.settings(true);
            if(!settings.enabled() || settings.ciphertext()==null) throw new DelegationException("DELEGATION_PUSH_NOT_CONFIGURED");
        }
    }
    private TaskEntity requireTask(UUID id) { TaskEntity task=repo.task(id,false); if(task==null) throw missing(); return task; }
    private TaskEntity lockTask(UUID id,boolean activeRole) {
        TaskEntity initial=requireTask(id); lockScope(initial.input().deviceId(),initial.input().roleId(),activeRole);
        TaskEntity task=repo.task(id,true); if(task==null) throw missing(); return task;
    }
    private TaskEntity lockTaskOrNull(UUID id) {
        TaskEntity initial=repo.task(id,false); if(initial==null) return null;
        if(!lockScopeIfPresent(initial.input().deviceId(),initial.input().roleId())) return null;
        return repo.task(id,true);
    }
    private void lockScope(UUID deviceId,UUID roleId,boolean activeRole) {
        if(!lockScopeIfPresent(deviceId,roleId)) throw new DelegationException("DELEGATION_SCOPE_INVALID");
        if(activeRole && !roleActive(roleId)) throw new DelegationException("DELEGATION_ROLE_ARCHIVED");
    }
    private boolean lockScopeIfPresent(UUID deviceId,UUID roleId) {
        var devices=repo.jdbc().queryForList("select id from devices where id=? for update",UUID.class,deviceId);
        if(devices.isEmpty()) return false;
        return !repo.jdbc().queryForList("select id from companion_roles where id=? for update",UUID.class,roleId).isEmpty();
    }
    private boolean roleActive(UUID id) {
        return Boolean.TRUE.equals(repo.jdbc().queryForObject("select exists(select 1 from companion_roles where id=? and archived_at is null)",Boolean.class,id));
    }
    private TaskSnapshot snapshot(TaskEntity task) {
        TaskInput i=task.input(); var last=repo.history(task.id(),1,0);
        return new TaskSnapshot(task.id(),i.deviceId(),i.roleId(),i.title(),i.instruction(),i.sourceUrls(),i.scheduleType(),
                i.daysOfWeek(),i.localTime(),i.zoneId(),i.maxItems(),i.deliveryMode(),i.enabled(),task.nextRunAt(),task.createdAt(),task.updatedAt(),
                last.isEmpty()?null:snapshot(last.getFirst()),i.instruction(),task.understanding(),i.onceAt(),i.intervalMinutes(),
                task.requiresToolEvidence(),task.allowedToolNames(),task.requiredToolNames());
    }
    private RunSnapshot snapshot(RunEntity run) {
        return new RunSnapshot(run.id(),run.taskId(),run.taskTitle(),run.scheduledDate(),run.status(),run.items(),run.sourceWarnings(),
                run.errorCode(),run.deliveryStatus(),run.deliveryAttempts(),run.createdAt(),run.completedAt(),
                run.scheduledAt(),run.occurrenceKey(),run.output(),run.steps(),run.evidence());
    }
    private PushSettingsSnapshot snapshot(PushSettingsEntity settings) {
        return new PushSettingsSnapshot(settings.serverUrl(),settings.ciphertext()!=null,settings.enabled(),settings.updatedAt());
    }
    private static String text(String value,int max) {
        if(value==null || value.isBlank() || value.trim().length()>max) throw new DelegationException("DELEGATION_TEXT_INVALID");
        return value.trim();
    }
    private static void requireNotInterrupted() {
        if(Thread.currentThread().isInterrupted()) throw new DelegationException("DELEGATION_PLANNING_CANCELLED");
    }
    private static int size(int limit) { return Math.min(Math.max(limit,1),100); }
    private static String safeCode(String code) { return code!=null && code.matches("[A-Z][A-Z0-9_]{1,79}")?code:"DELEGATION_GENERATION_FAILED"; }
    private static DelegationException missing() { return new DelegationException("DELEGATION_NOT_FOUND",true); }
    private static TaskInput withEnabled(TaskInput i,boolean enabled) {
        return new TaskInput(i.deviceId(),i.roleId(),i.title(),i.instruction(),i.sourceUrls(),i.scheduleType(),i.daysOfWeek(),
                i.localTime(),i.zoneId(),i.maxItems(),i.deliveryMode(),enabled,i.description(),i.onceAt(),i.intervalMinutes());
    }

    private record Claim(TaskEntity task,RunEntity run,boolean execute) {}
    private record Prepared(TaskInput input,String understanding,boolean requiresToolEvidence,List<String> allowedToolNames,List<String> requiredToolNames) {}
    public enum ScheduleType { ONCE,DAILY,WEEKLY,INTERVAL }
    public enum DeliveryMode { CONSOLE,BARK }
    public enum RunStatus { RUNNING,SUCCEEDED,NO_UPDATES,FAILED,CANCELLED,BLOCKED }
    public enum DeliveryStatus { NONE,PENDING,SENDING,SENT,FAILED,UNKNOWN,SKIPPED }
    public record TaskInput(UUID deviceId,UUID roleId,String title,String instruction,List<String> sourceUrls,
                            ScheduleType scheduleType,List<Integer> daysOfWeek,LocalTime localTime,String zoneId,
                            int maxItems,DeliveryMode deliveryMode,boolean enabled,String description,Instant onceAt,Integer intervalMinutes) {
        public TaskInput(UUID deviceId,UUID roleId,String title,String instruction,List<String> sourceUrls,
                         ScheduleType scheduleType,List<Integer> daysOfWeek,LocalTime localTime,String zoneId,
                         int maxItems,DeliveryMode deliveryMode,boolean enabled) {
            this(deviceId,roleId,title,instruction,sourceUrls,scheduleType,daysOfWeek,localTime,zoneId,maxItems,deliveryMode,enabled,null,null,null);
        }
    }
    public record TaskSnapshot(UUID id,UUID deviceId,UUID roleId,String title,String instruction,List<String> sourceUrls,
                               ScheduleType scheduleType,List<Integer> daysOfWeek,@com.fasterxml.jackson.annotation.JsonFormat(pattern="HH:mm") LocalTime localTime,String zoneId,int maxItems,
                               DeliveryMode deliveryMode,boolean enabled,Instant nextRunAt,Instant createdAt,Instant updatedAt,RunSnapshot lastRun,
                               String description,String understanding,Instant onceAt,Integer intervalMinutes,
                               boolean requiresToolEvidence,List<String> allowedToolNames,List<String> requiredToolNames) {
        public TaskSnapshot(UUID id,UUID deviceId,UUID roleId,String title,String instruction,List<String> sourceUrls,
                            ScheduleType scheduleType,List<Integer> daysOfWeek,LocalTime localTime,String zoneId,int maxItems,
                            DeliveryMode deliveryMode,boolean enabled,Instant nextRunAt,Instant createdAt,Instant updatedAt,RunSnapshot lastRun) {
            this(id,deviceId,roleId,title,instruction,sourceUrls,scheduleType,daysOfWeek,localTime,zoneId,maxItems,deliveryMode,
                    enabled,nextRunAt,createdAt,updatedAt,lastRun,instruction,instruction,null,null,true,List.of(),List.of());
        }
    }
    public record RunSnapshot(UUID id,UUID taskId,String taskTitle,LocalDate scheduledDate,RunStatus status,
                              List<ResearchBriefGenerator.BriefItem> items,List<String> sourceWarnings,String errorCode,
                              DeliveryStatus deliveryStatus,int deliveryAttempts,Instant createdAt,Instant completedAt,
                              Instant scheduledAt,String occurrenceKey,String output,List<ExecutionStep> steps,List<ExecutionEvidence> evidence) {
        public RunSnapshot(UUID id,UUID taskId,String taskTitle,LocalDate scheduledDate,RunStatus status,
                           List<ResearchBriefGenerator.BriefItem> items,List<String> sourceWarnings,String errorCode,
                           DeliveryStatus deliveryStatus,int deliveryAttempts,Instant createdAt,Instant completedAt) {
            this(id,taskId,taskTitle,scheduledDate,status,items,sourceWarnings,errorCode,deliveryStatus,deliveryAttempts,createdAt,completedAt,
                    createdAt,"local:"+scheduledDate,"",List.of(),List.of());
        }
    }
    public record TaskPage(List<TaskSnapshot> list,long total) {}
    public record RunPage(List<RunSnapshot> list,long total) {}
    public record PushSettingsInput(String serverUrl,String deviceKey,boolean clearDeviceKey,boolean enabled) {
        @Override public String toString() { return "PushSettingsInput[redacted]"; }
    }
    public record PushSettingsSnapshot(String serverUrl,boolean keyConfigured,boolean enabled,Instant updatedAt) {}
}
