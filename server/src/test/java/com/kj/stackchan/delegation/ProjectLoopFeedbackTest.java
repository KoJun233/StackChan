package com.kj.stackchan.delegation;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static com.kj.stackchan.delegation.DelegationService.*;
import static com.kj.stackchan.delegation.DelegationTaskExecutor.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual PostgreSQL locks, Spring transactions and bounded workers; all source/model output is synthetic. */
@SpringBootTest(properties="app.delegation.scheduler-enabled=false")
@org.springframework.context.annotation.Import(ProjectLoopFeedbackTest.ProjectClockConfiguration.class)
@Testcontainers
class ProjectLoopFeedbackTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired DelegationService service;
    @Autowired OperationReceiptService receipts;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DelegationTaskExecutor generator;
    @MockitoBean DelegationTaskPlanner planner;
    @MockitoBean PublicHttpsUrlPolicy urls;
    @MockitoBean BarkPushClient bark;
    @Autowired ProjectClock clock;
    UUID device; final UUID role=CompanionRoleEntity.DEFAULT_ROLE_ID;
    static final Instant NOW=Instant.parse("2026-10-15T03:00:00Z");
    static final String BODY="## 任务理解\n合成夹具比较\n\n## 方案比较\nA 与 B；尺寸：来源未提供\n\n## 建议与理由\n按容易整理优先建议 A\n\n## 缺失信息与限制\n来源未提供尺寸\n\n## 下一步供你决定\n补充桌面尺寸";
    @BeforeEach void setup() {
        clock.now=NOW;
        jdbc.update("delete from devices");
        jdbc.update("update delegation_push_settings set enabled=false,key_ciphertext=null,key_iv=null where id=1");
        device=UUID.randomUUID();jdbc.update("insert into devices(id,hardware_id,firmware_version) values(?,?,?)",device,"project-fixture-"+device,"test");
        when(urls.validate(anyString())).thenAnswer(i->URI.create(i.getArgument(0)));
        when(generator.execute(any())).thenReturn(result(BODY));
        when(planner.plan(any())).thenReturn(new DelegationTaskPlanner.TaskPlan("合成比较","合成比较一次",ScheduleType.ONCE,List.of(),null,
                "Asia/Shanghai",NOW,null,DeliveryMode.CONSOLE,true,List.of("read_public_source","send_phone_notification"),List.of("read_public_source"),null));
    }
    @Test void commitsImmediateIntentAndReceiptOnceAndOldRunCannotBypassRevisionSlot() throws Exception {
        String key=key(); var task=service.createProject(input(),key); var first=await(task.id());
        assertThat(first.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(service.createProject(input(),key).id()).isEqualTo(task.id());
        assertThat(service.run(task.id()).id()).isEqualTo(first.id());
        assertThat(service.runRevision(task.id(),1,key()).id()).isEqualTo(first.id());
        verify(planner,times(1)).plan(any());verify(generator,times(1)).execute(any());verifyNoInteractions(bark);
        assertThat(service.get(task.id()).allowedToolNames()).containsExactly("read_public_source");
        assertThat(first.taskRevision()).isEqualTo(1);assertThat(first.attempt()).isEqualTo(1);assertThat(first.review().resourceVersion()).isEqualTo(1);
        var receipt=receipts.get(device,role,OperationReceiptService.Action.DELEGATION_CREATE,key,null);
        assertThat(receipt.status()).isEqualTo("COMMITTED");assertThat(receipt.resourceIds()).containsEntry("taskId",task.id()).containsEntry("runId",first.id());
        assertThat(receipts.get(UUID.randomUUID(),role,receipt.action(),key,null).status()).isEqualTo("NOT_FOUND");
        assertThat(service.findProjectTasks(device,UUID.randomUUID())).isEmpty();
    }
    @Test void revisionPreservesOldOutputAndRunsWithinSameTaskWithCausalContext() throws Exception {
        var task=service.createProject(input(),key()); var old=await(task.id()); String key=key();
        var command=new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,"我更在意线缆少，预算信息先不用猜",1,1,RunMode.SAVE_AND_RUN);
        when(generator.execute(any())).thenAnswer(invocation -> {
            TaskExecutionRequest req=invocation.getArgument(0);
            assertThat(req.projectContext().previousOutput()).isEqualTo(BODY);
            assertThat(req.projectContext().supersedesRunId()).isEqualTo(old.id());
            assertThat(req.projectContext().preferenceFeedback()).contains("线缆少");
            return result(BODY.replace("容易整理","线缆少"));
        });
        var feedback=service.feedback(old.id(),command,key);var revised=await(task.id());
        assertThat(feedback.newRunId()).isEqualTo(revised.id());assertThat(revised.taskId()).isEqualTo(task.id());assertThat(revised.taskRevision()).isEqualTo(2);
        assertThat(revised.review().supersedesRunId()).isEqualTo(old.id());assertThat(revised.output()).contains("线缆少");
        assertThat(service.runs(task.id(),0,20).list()).hasSize(2).anySatisfy(run->{assertThat(run.id()).isEqualTo(old.id());assertThat(run.output()).isEqualTo(BODY);});
        assertThat(service.feedback(old.id(),command,key)).isEqualTo(feedback);
        assertThatThrownBy(()->service.feedback(old.id(),new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,"不同正文",1,1,RunMode.SAVE_AND_RUN),key))
                .isInstanceOf(DelegationException.class).hasMessage("IDEMPOTENCY_CONFLICT");
        assertThat(service.projectSummary(task.id()).feedbackHistory()).hasSize(1);
        clock.now=NOW.plusSeconds(86400);
        assertThat(service.projectConversationSummary(task.id())).contains("线缆少","完整成果");verify(generator,times(2)).execute(any());
    }
    @Test void saveOnlyThenContinueUsesNewRevisionOccurrenceAndDifferentKeysCannotDuplicateIt() throws Exception {
        var task=service.createProject(input(),key());var old=await(task.id());
        var saved=service.feedback(old.id(),new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,"优先线缆少",1,1,null),key());
        assertThat(saved.newRunId()).isNull();assertThat(service.runs(task.id(),0,20).total()).isEqualTo(1);
        var run=service.runRevision(task.id(),2,key());await(task.id());
        assertThat(service.runRevision(task.id(),2,key()).id()).isEqualTo(run.id());assertThat(run.occurrenceKey()).startsWith("revision:2:");
        verify(generator,times(2)).execute(any());
    }
    @Test void twoTabsWithSameVersionsHaveOneWinnerAndNoSilentOverwrite() throws Exception {
        var task=service.createProject(input(),key());var run=await(task.id());var start=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<String> action=()-> {start.await();try {service.feedback(run.id(),new FeedbackInput(FeedbackKind.ACCEPT_RESULT,"",1,1,null),key());return "OK";}
                catch(DelegationException e) {return e.code();}};
            var a=pool.submit(action);var b=pool.submit(action);start.countDown();
            assertThat(List.of(a.get(8,TimeUnit.SECONDS),b.get(8,TimeUnit.SECONDS))).containsExactlyInAnyOrder("OK","REVISION_CONFLICT");
            assertThat(service.projectSummary(task.id()).feedbackHistory()).hasSize(1);
            assertThat(service.get(task.id()).lastRun().status()).isEqualTo(RunStatus.SUCCEEDED);
            assertThat(service.get(task.id()).lastRun().review().reviewState()).isEqualTo(ReviewState.ACCEPTED);
        } finally {pool.shutdownNow();}
    }
    @Test void lateOldResultAfterRevisionCannotReplaceNewOutputOrSend() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var count=new AtomicInteger();
        when(generator.execute(any())).thenAnswer(i->{if(count.incrementAndGet()==1){entered.countDown();release.await(8,TimeUnit.SECONDS);return result("过期旧结果");}return result(BODY);});
        var task=service.createProject(input(),key());assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();var old=service.get(task.id()).lastRun();
        try {
            var revised=service.feedback(old.id(),new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,"优先线缆少",1,1,RunMode.SAVE_AND_RUN),key());
            assertThat(revised.newRunId()).isNotEqualTo(old.id());await(task.id());release.countDown();
            assertThat(service.runs(task.id(),0,20).list()).anySatisfy(r->{assertThat(r.id()).isEqualTo(old.id());assertThat(r.status()).isEqualTo(RunStatus.CANCELLED);assertThat(r.output()).isEmpty();});
            assertThat(service.get(task.id()).lastRun().output()).isEqualTo(BODY);verifyNoInteractions(bark);
        } finally {release.countDown();}
    }
    @Test void explicitCancellationIsDurableAndAllLegacyResumePathsRejectIt() throws Exception {
        var task=service.createProject(input(),key());var run=await(task.id());
        service.feedback(run.id(),new FeedbackInput(FeedbackKind.CANCEL,"先取消这个比较",1,1,null),key());
        assertThat(service.get(task.id()).cancelledAt()).isEqualTo(NOW);assertThat(service.get(task.id()).enabled()).isFalse();
        assertThatThrownBy(()->service.setEnabled(task.id(),true)).hasMessage("INVALID_STATE");
        assertThatThrownBy(()->service.run(task.id())).hasMessage("INVALID_STATE");
        assertThatThrownBy(()->service.runRevision(task.id(),2,key())).hasMessage("INVALID_STATE");
        assertThatThrownBy(()->service.update(task.id(),input())).hasMessage("INVALID_STATE");
        assertThat(service.get(task.id()).lastRun().output()).isEqualTo(BODY);verify(generator,times(1)).execute(any());
    }
    @Test void deletedPrivateHistoryDoesNotMakeOldCreationKeyExecutableAgain() throws Exception {
        String key=key();var task=service.createProject(input(),key);await(task.id());service.delete(task.id());
        assertThat(jdbc.queryForObject("select count(*) from delegation_result_reviews",Integer.class)).isZero();
        assertThatThrownBy(()->service.createProject(input(),key)).hasMessage("DELEGATION_NOT_FOUND");
        assertThat(receipts.get(device,role,OperationReceiptService.Action.DELEGATION_CREATE,key,null).status()).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("select result_metadata from companion_operation_receipts where operation_key=?",String.class,key)).doesNotContain("合成","https://");
        verify(generator,times(1)).execute(any());
    }
    @Test void failedOperationIsSavedAndSameKeyNeverRetriesPlanning() {
        String key=key();when(planner.plan(any())).thenThrow(new DelegationException("DEPENDENCY_UNAVAILABLE"));
        assertThatThrownBy(()->service.createProject(input(),key)).hasMessage("DEPENDENCY_UNAVAILABLE");
        assertThatThrownBy(()->service.createProject(input(),key)).hasMessage("DEPENDENCY_UNAVAILABLE");
        assertThat(receipts.get(device,role,OperationReceiptService.Action.DELEGATION_CREATE,key,null).status()).isEqualTo("FAILED");
        assertThat(service.list(device,role,0,20).total()).isZero();verify(planner,times(1)).plan(any());
    }
    @Test void invalidKeysModesAndUnprovidedSourcesNeverCreateWork() {
        assertThatThrownBy(()->service.createProject(input(),"short")).hasMessage("INVALID_REQUEST");
        TaskInput one=new TaskInput(device,role,null,null,List.of(),null,List.of(),null,null,0,null,true,"帮我比较 https://fake.example/a",null,null);
        assertThatThrownBy(()->service.createProject(one,key())).isInstanceOf(DelegationException.class);
        verifyNoInteractions(planner,generator,bark);
    }
    @Test void capacityFailureKeepsCommittedTaskAndExplicitRetryCreatesOnlyOneNewRun() throws Exception {
        var entered=new CountDownLatch(2);var release=new CountDownLatch(1);
        when(generator.execute(any())).thenAnswer(i->{entered.countDown();release.await(8,TimeUnit.SECONDS);return result(BODY);});
        var a=service.createProject(input(),key());var b=service.createProject(input(),key());
        assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        try {
            String key=key();var rejected=service.createProject(input(),key);
            assertThat(rejected.lastRun().status()).isEqualTo(RunStatus.FAILED);
            assertThat(rejected.lastRun().errorCode()).isEqualTo("DELEGATION_CAPACITY");
            assertThat(receipts.get(device,role,OperationReceiptService.Action.DELEGATION_CREATE,key,null).status()).isEqualTo("COMMITTED");
            release.countDown();await(a.id());await(b.id());
            var workers=(java.util.concurrent.ThreadPoolExecutor)org.springframework.test.util.ReflectionTestUtils.getField(service,"executor");
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(workers.getActiveCount()!=0 && System.nanoTime()<end) Thread.sleep(5);
            assertThat(workers.getActiveCount()).isZero();
            var retry=service.runRevision(rejected.id(),1,key());await(rejected.id());
            assertThat(retry.id()).isNotEqualTo(rejected.lastRun().id());
            assertThat(service.runs(rejected.id(),0,20).total()).isEqualTo(2);
            assertThat(service.get(rejected.id()).lastRun().id()).isEqualTo(retry.id());
        } finally {release.countDown();}
    }
    @Test void interruptedPendingReceiptFailsClosedAndNeverExecutesAnOldIntent() {
        String key=key();jdbc.update("insert into companion_operation_receipts(device_id,role_id,action,operation_key,request_digest,status,created_at,updated_at) values(?,?,'DELEGATION_CREATE',?,?,'PENDING',?,?)",
                device,role,key,"a".repeat(64),java.sql.Timestamp.from(NOW),java.sql.Timestamp.from(NOW));
        receipts.recoverInterrupted();
        assertThat(receipts.get(device,role,OperationReceiptService.Action.DELEGATION_CREATE,key,null).status()).isEqualTo("FAILED");
        verifyNoInteractions(planner,generator,bark);
    }
    @Test void keyedRecurringCreationKeepsTheLegacyScheduleWhenManuallyRun() throws Exception {
        when(planner.plan(any())).thenReturn(new DelegationTaskPlanner.TaskPlan("合成日报","每天整理一次",ScheduleType.DAILY,List.of(),java.time.LocalTime.of(9,0),
                "Asia/Shanghai",null,null,DeliveryMode.CONSOLE,true,List.of("read_public_source"),List.of("read_public_source"),null));
        var input=new TaskInput(device,role,null,null,List.of(),null,List.of(),null,null,0,null,true,"每天九点整理公开资料",null,null);
        var task=service.createProject(input,key());var next=task.nextRunAt();
        assertThat(next).isNotNull();assertThat(service.findProjectTasks(device,role)).isEmpty();
        var first=service.run(task.id());await(task.id());
        assertThat(service.get(task.id()).nextRunAt()).isEqualTo(next);
        assertThat(service.run(task.id()).id()).isEqualTo(first.id());
        verify(generator,times(1)).execute(any());
    }
    @Test void scheduledOnceAndManualContinueShareOneRevisionIntent() throws Exception {
        assertScheduledIntentShared(false);
    }
    @Test void pausedThenResumedFutureProjectStillSharesItsScheduledRevisionIntent() throws Exception {
        assertScheduledIntentShared(true);
    }
    private void assertScheduledIntentShared(boolean resume) throws Exception {
        var future=new TaskInput(device,role,null,null,List.of(),ScheduleType.ONCE,List.of(),null,"Asia/Shanghai",0,DeliveryMode.CONSOLE,true,
                input().description(),NOW.plusSeconds(60),null);
        var task=service.createProject(future,key());
        if(resume) {service.setEnabled(task.id(),false);task=service.setEnabled(task.id(),true);}
        verifyNoInteractions(generator);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(generator.execute(any())).thenAnswer(i->{entered.countDown();release.await(8,TimeUnit.SECONDS);return result(BODY);});
        clock.now=NOW.plusSeconds(60);service.tick();
        try {
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var scheduled=service.get(task.id()).lastRun();
            assertThat(service.run(task.id()).id()).isEqualTo(scheduled.id());
            assertThat(service.runRevision(task.id(),task.revision(),key()).id()).isEqualTo(scheduled.id());
            assertThat(service.runs(task.id(),0,20).total()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from delegation_revision_intents where task_id=? and task_revision=?",Integer.class,task.id(),task.revision())).isEqualTo(1);
            release.countDown();await(task.id());
            assertThat(service.runRevision(task.id(),task.revision(),key()).id()).isEqualTo(scheduled.id());
            verify(generator,times(1)).execute(any());verifyNoInteractions(bark);
        } finally {release.countDown();}
    }
    @Test void changingProjectToRecurringKeepsItsHistoryAndRestoresOccurrenceScheduling() throws Exception {
        var task=service.createProject(input(),key());var old=await(task.id());
        var daily=new TaskInput(device,role,null,null,List.of(),ScheduleType.DAILY,List.of(),java.time.LocalTime.of(9,0),"Asia/Shanghai",0,DeliveryMode.CONSOLE,true,
                "每天九点比较这两个公开页面 https://fake.example/a https://fake.example/b",null,null);
        var changed=service.update(task.id(),daily);var next=changed.nextRunAt();
        assertThat(next).isNotNull();assertThat(service.findProjectTasks(device,role)).isEmpty();
        var run=service.run(task.id());await(task.id());
        assertThat(service.get(task.id()).nextRunAt()).isEqualTo(next);
        assertThat(service.run(task.id()).id()).isEqualTo(run.id());
        assertThat(service.runs(task.id(),0,20).list()).anySatisfy(r->{assertThat(r.id()).isEqualTo(old.id());assertThat(r.output()).isEqualTo(BODY);});
        assertThatThrownBy(()->service.runRevision(task.id(),changed.revision(),key())).hasMessage("INVALID_STATE");
        assertThatThrownBy(()->service.feedback(old.id(),new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,"优先线缆少",changed.revision(),2,RunMode.SAVE_AND_RUN),key())).hasMessage("INVALID_STATE");
    }
    @Test void currentPreferenceCanWithdrawPhoneDeliveryWithoutGrantingItAgain() throws Exception {
        service.savePushSettings(new PushSettingsInput("https://api.day.app","synthetic-project-fixture",false,true));
        when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.SENT);
        var phone=new TaskInput(device,role,null,null,List.of(),null,List.of(),null,null,0,DeliveryMode.BARK,true,
                input().description()+"，成果发到手机",null,null);
        var task=service.createProject(phone,key());var old=await(task.id());
        verify(bark,timeout(5000).times(1)).send(anyString(),anyString(),anyString(),anyString());
        clearInvocations(bark);
        service.feedback(old.id(),new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,"这次不要发到手机，按线缆少优先重新比较",1,1,RunMode.SAVE_AND_RUN),key());
        var revised=await(task.id());
        assertThat(service.get(task.id()).deliveryMode()).isEqualTo(DeliveryMode.CONSOLE);
        assertThat(revised.deliveryStatus()).isEqualTo(DeliveryStatus.NONE);
        verifyNoInteractions(bark);
    }
    @Test void modelCannotGrantExplorationAndExplicitSourceRevisionRefreshesTools() throws Exception {
        var plain=new TaskInput(device,role,null,null,List.of(),null,List.of(),null,null,0,null,true,"帮我写一份介绍，只根据我说的话",null,null);
        var task=service.createProject(plain,key());await(task.id());
        assertThat(service.get(task.id()).allowedToolNames()).isEmpty();
        var revised=service.feedback(task.lastRun().id(),new FeedbackInput(FeedbackKind.REVISE_PREFERENCES,
                "加入这两个公开页面 https://fake.example/a https://fake.example/b 按此重新整理",1,1,RunMode.SAVE_AND_RUN),key());
        await(task.id());assertThat(revised.newRunId()).isNotNull();
        assertThat(service.get(task.id()).sourceUrls()).containsExactly("https://fake.example/a","https://fake.example/b");
        assertThat(service.get(task.id()).allowedToolNames()).containsExactly("read_public_source");
        assertThat(service.get(task.id()).requiresToolEvidence()).isTrue();
    }
    private TaskInput input() {return new TaskInput(device,role,null,null,List.of(),null,List.of(),null,null,0,null,true,
            "现在帮我比较这两个公开页面 https://fake.example/a https://fake.example/b ，先给建议，不购买",null,null);}
    private static String key() {return UUID.randomUUID().toString();}
    private TaskExecutionResult result(String output) {return new TaskExecutionResult(output,RunStatus.SUCCEEDED,
            List.of(new ExecutionStep("read_public_source","SUCCEEDED","合成来源")),List.of(new ExecutionEvidence("合成 A","https://fake.example/a","假页面 A","https://fake.example/a",NOW),new ExecutionEvidence("合成 B","https://fake.example/b","假页面 B","https://fake.example/b",NOW)));}
    private RunSnapshot await(UUID task) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);RunSnapshot run;
        do {run=service.get(task).lastRun();if(run!=null && run.status()!=RunStatus.RUNNING)return run;Thread.sleep(10);}while(System.nanoTime()<end);
        throw new AssertionError("Synthetic project worker did not finish");
    }
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    static class ProjectClockConfiguration {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        ProjectClock projectClock() {return new ProjectClock();}
    }
    static class ProjectClock extends Clock {
        volatile Instant now=NOW;
        public Instant instant() {return now;}
        public java.time.ZoneId getZone() {return java.time.ZoneOffset.UTC;}
        public Clock withZone(java.time.ZoneId zone) {return Clock.fixed(now,zone);}
    }
}
