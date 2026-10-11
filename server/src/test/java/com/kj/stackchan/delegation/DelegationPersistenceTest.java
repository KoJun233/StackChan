package com.kj.stackchan.delegation;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.config.AppProperties;
import com.kj.stackchan.llm.SecretCipher;
import com.kj.stackchan.role.CompanionRoleEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kj.stackchan.delegation.DelegationService.*;

@Testcontainers
class DelegationPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4").asCompatibleSubstituteFor("postgres"));
    private JdbcTemplate jdbc;
    private DelegationRepository repo;
    private DelegationService service;
    private DelegationTaskExecutor generator;
    private DelegationTaskPlanner planner;
    private BarkPushClient bark;
    private MutableClock clock;
    private UUID device;
    private final UUID role=CompanionRoleEntity.DEFAULT_ROLE_ID;
    private DataSourceTransactionManager transactionManager;
    private BarkNotificationService notifications;
    private com.kj.stackchan.agent.AgentSettingsService agentSettings;
    @BeforeAll static void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load().migrate();
    }
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword());
        jdbc=new JdbcTemplate(source); transactionManager=new DataSourceTransactionManager(source);
        jdbc.update("delete from bark_notification_attempts");
        jdbc.update("delete from devices");
        jdbc.update("update delegation_push_settings set key_ciphertext=null,key_iv=null,enabled=false where id=1");
        device=UUID.randomUUID();
        jdbc.update("insert into devices(id,hardware_id,firmware_version) values(?,?,?)",device,"delegation-"+device,"test");
        clock=new MutableClock();
        generator=mock(DelegationTaskExecutor.class); planner=mock(DelegationTaskPlanner.class); bark=mock(BarkPushClient.class);
        when(generator.execute(any())).thenReturn(brief());
        when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.SENT);
        var policy=mock(PublicHttpsUrlPolicy.class);
        when(policy.validate(anyString())).thenAnswer(invocation->URI.create(invocation.getArgument(0)));
        var properties=new AppProperties(); properties.setSecretsEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        repo=spy(new DelegationRepository(jdbc,new ObjectMapper().findAndRegisterModules()));
        service=new DelegationService(repo,planner,generator,bark,policy,new SecretCipher(properties),clock,transactionManager);
        agentSettings=mock(com.kj.stackchan.agent.AgentSettingsService.class);
        when(agentSettings.runtimeSettings()).thenReturn(new com.kj.stackchan.agent.AgentSettingsService.RuntimeSettings(true,true,true,clock.instant()));
        when(agentSettings.isEnabled(com.kj.stackchan.agent.AgentCapabilityType.BUILTIN_TOOL,"send_phone_notification")).thenReturn(true);
        notifications=new BarkNotificationService(repo,bark,new SecretCipher(properties),agentSettings,clock,transactionManager);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"notifications",notifications);
    }
    @AfterEach void close() { service.stop(); }

    private com.kj.stackchan.agent.AgentInvocationContext phoneContext() {
        return new com.kj.stackchan.agent.AgentInvocationContext(UUID.randomUUID(),UUID.randomUUID(),device,role,com.kj.stackchan.agent.AgentChannel.WEB);
    }
    private void configurePhone() { service.savePushSettings(new PushSettingsInput("https://api.day.app","only_server_knows_key",false,true)); }

    @Test void phoneToolReusesEncryptedSettingsAndDeduplicatesAcrossRestartsWithoutStoringContent() {
        configurePhone(); var context=phoneContext();
        var first=notifications.send(context,"我的通知","这是任意内容，不是日报");
        assertThat(first.status()).isEqualTo("SENT"); assertThat(first.deduplicated()).isFalse();
        assertThat(notifications.send(context,"我的通知","这是任意内容，不是日报").receiptId()).isEqualTo(first.receiptId());
        var restarted=new BarkNotificationService(repo,bark,new SecretCipher(secretProperties()),agentSettings,clock,transactionManager);
        assertThat(restarted.send(context,"我的通知","这是任意内容，不是日报").deduplicated()).isTrue();
        assertThat(restarted.send(context,"改成别的通知","新正文").errorCode()).isEqualTo("PUSH_ALREADY_ATTEMPTED");
        verify(bark,times(1)).send("https://api.day.app","only_server_knows_key","我的通知","这是任意内容，不是日报");
        assertThat(repo.encode(jdbc.queryForMap("select * from bark_notification_attempts"))).doesNotContain("only_server_knows_key","我的通知","任意内容");
    }
    private AppProperties secretProperties() {
        var properties=new AppProperties(); properties.setSecretsEncryptionKey(Base64.getEncoder().encodeToString(new byte[32])); return properties;
    }

    @Test void phoneToolUnknownOrInterruptedDeliveryNeverAutomaticallyResends() {
        configurePhone(); when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.UNKNOWN);
        var context=phoneContext(); assertThat(notifications.send(context,"通知","正文").status()).isEqualTo("UNKNOWN");
        assertThat(notifications.send(context,"通知","正文").status()).isEqualTo("UNKNOWN");
        jdbc.update("update bark_notification_attempts set status='SENDING',error_code=null,completed_at=null");
        assertThat(notifications.send(context,"通知","正文").errorCode()).isEqualTo("PUSH_DELIVERY_UNKNOWN");
        verify(bark,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }

    @Test void concurrentPhoneCallsShareOneCommittedOutboundMarker() throws Exception {
        configurePhone(); var context=phoneContext(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        when(bark.send(anyString(),anyString(),anyString(),anyString())).thenAnswer(call->{
            entered.countDown(); assertThat(release.await(5,TimeUnit.SECONDS)).isTrue(); return BarkPushClient.DeliveryOutcome.SENT;
        });
        try(var workers=Executors.newFixedThreadPool(2)) {
            var first=workers.submit(()->notifications.send(context,"通知","正文"));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
            var second=workers.submit(()->notifications.send(context,"通知","正文"));
            assertThat(second.get(5,TimeUnit.SECONDS).status()).isEqualTo("UNKNOWN");
            release.countDown(); assertThat(first.get(5,TimeUnit.SECONDS).status()).isEqualTo("SENT");
        }
        verify(bark,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }

    @Test void invalidContentDisabledSettingsAndRevokedToolDoNotSend() {
        assertThat(notifications.send(phoneContext(),"标题","无配置").errorCode()).isEqualTo("PUSH_NOT_CONFIGURED");
        configurePhone();
        assertThat(notifications.send(phoneContext(),"标题","中".repeat(934)).errorCode()).isEqualTo("PUSH_CONTENT_INVALID");
        when(agentSettings.isEnabled(com.kj.stackchan.agent.AgentCapabilityType.BUILTIN_TOOL,"send_phone_notification")).thenReturn(false);
        assertThat(notifications.send(phoneContext(),"标题","已撤销").errorCode()).isEqualTo("PUSH_NOT_AUTHORIZED");
        verifyNoInteractions(bark);
    }

    @Test void aScheduledPhoneToolCommitsUnderTheTaskGuardAndReplacesAutomaticDelivery() throws Exception {
        configurePhone();
        when(planner.plan(any())).thenReturn(new DelegationTaskPlanner.TaskPlan("手机通知任务","每天九点发送通知",ScheduleType.DAILY,List.of(),LocalTime.of(9,0),
                "Asia/Shanghai",null,null,DeliveryMode.BARK,true,List.of("send_phone_notification"),List.of("send_phone_notification"),null));
        when(generator.execute(any())).thenAnswer(call->{
            DelegationTaskExecutor.TaskExecutionRequest request=call.getArgument(0);
            var context=new com.kj.stackchan.agent.AgentInvocationContext(UUID.randomUUID(),null,device,role,
                    com.kj.stackchan.agent.AgentChannel.DELEGATION,request.runId(),request.attempt());
            var sent=(BarkNotificationService.Receipt)request.guard().execute(()->notifications.send(context,"直接通知","目标要求的正文"));
            assertThat(sent.status()).isEqualTo("SENT");
            return new DelegationTaskExecutor.TaskExecutionResult("Bark已接受通知",RunStatus.SUCCEEDED,
                    List.of(new DelegationTaskExecutor.ExecutionStep("send_phone_notification","SUCCEEDED","Bark接受")),List.of());
        });
        var task=service.create(description("每天九点用手机通知工具推送一句鼓励")); var run=service.run(task.id());
        await(run.id(),RunStatus.SUCCEEDED,DeliveryStatus.SENT);
        assertThat(repo.run(run.id()).deliveryAttempts()).isEqualTo(1);
        service.tick(); verify(bark,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }

    @Test void unknownScheduledPhoneToolIsBlockedAndDoesNotTriggerAutomaticDelivery() throws Exception {
        configurePhone(); when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.UNKNOWN);
        when(planner.plan(any())).thenReturn(new DelegationTaskPlanner.TaskPlan("手机通知任务","每天九点发送通知",ScheduleType.DAILY,List.of(),LocalTime.of(9,0),
                "Asia/Shanghai",null,null,DeliveryMode.BARK,true,List.of("send_phone_notification"),List.of("send_phone_notification"),null));
        when(generator.execute(any())).thenAnswer(call->{
            DelegationTaskExecutor.TaskExecutionRequest request=call.getArgument(0);
            var context=new com.kj.stackchan.agent.AgentInvocationContext(UUID.randomUUID(),null,device,role,
                    com.kj.stackchan.agent.AgentChannel.DELEGATION,request.runId(),request.attempt());
            request.guard().execute(()->notifications.send(context,"通知","正文"));
            return new DelegationTaskExecutor.TaskExecutionResult("通知结果不确定，请检查手机",RunStatus.BLOCKED,
                    List.of(new DelegationTaskExecutor.ExecutionStep("send_phone_notification","FAILED","结果不确定")),List.of());
        });
        var task=service.create(description("每天九点用手机通知工具推送一句鼓励")); var run=service.run(task.id());
        await(run.id(),RunStatus.BLOCKED,DeliveryStatus.UNKNOWN); service.tick();
        verify(bark,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }

    @Test void aDescriptionIsPlannedOnceAndCanGenerateContentWithoutAnySources() throws Exception {
        when(planner.plan(any())).thenReturn(plan(ScheduleType.DAILY,false,List.of(),null,null));
        when(generator.execute(any())).thenReturn(new DelegationTaskExecutor.TaskExecutionResult("今日练习：写下三个优先事项。",RunStatus.SUCCEEDED,List.of(),List.of()));
        var task=service.create(description("每天九点给我一个写作练习"));
        assertThat(task.description()).isEqualTo("每天九点给我一个写作练习");
        assertThat(task.understanding()).isEqualTo("每天九点执行你的任务，成果留在控制台。");
        assertThat(task.sourceUrls()).isEmpty(); assertThat(task.requiresToolEvidence()).isFalse();
        var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,null);
        assertThat(repo.run(run.id()).output()).contains("今日练习"); assertThat(repo.run(run.id()).items()).isEmpty();
        var request=org.mockito.ArgumentCaptor.forClass(DelegationTaskExecutor.TaskExecutionRequest.class);
        verify(generator).execute(request.capture());
        assertThat(request.getValue().deviceId()).isEqualTo(device); assertThat(request.getValue().roleId()).isEqualTo(role);
        assertThat(request.getValue().description()).isEqualTo(task.description());
        assertThat(request.getValue().guard()).isNotNull(); verify(planner,times(1)).plan(any());
    }
    @Test void anIncompleteDescriptionAsksForTheMissingFactWithoutCreatingATask() {
        when(planner.plan(any())).thenReturn(new DelegationTaskPlanner.TaskPlan(null,null,null,List.of(),null,"Asia/Shanghai",null,null,
                DeliveryMode.CONSOLE,false,List.of(),List.of(),"希望每天几点执行？"));
        assertThatThrownBy(()->service.create(description("每天给我一个写作练习")))
                .isInstanceOfSatisfying(DelegationException.class,e->{
                    assertThat(e.code()).isEqualTo("DELEGATION_CLARIFICATION_REQUIRED");
                    assertThat(e.clarification()).isEqualTo("希望每天几点执行？");
                });
        assertThat(service.list(device,role,0,20).total()).isZero(); verifyNoInteractions(generator);
    }
    @Test void anExternalActionCannotSucceedWithOnlyAnUnrelatedToolOrAModelAssertion() throws Exception {
        when(planner.plan(any())).thenReturn(plan(ScheduleType.DAILY,true,List.of("computer_start"),null,null));
        when(generator.execute(any())).thenReturn(new DelegationTaskExecutor.TaskExecutionResult("电脑已启动",RunStatus.SUCCEEDED,
                List.of(new DelegationTaskExecutor.ExecutionStep("current_time","SUCCEEDED","已读取时间")),List.of()));
        var task=service.create(description("每天九点启动已接入的电脑")); var run=service.run(task.id()); await(run.id(),RunStatus.BLOCKED,DeliveryStatus.NONE);
        assertThat(repo.run(run.id()).output()).doesNotContain("电脑已启动").contains("未执行"); verifyNoInteractions(bark);
    }
    @Test void anIntervalHasMultipleDurableOccurrencesOnTheSameDayAndSkipsMissedPeriods() throws Exception {
        when(planner.plan(any())).thenReturn(plan(ScheduleType.INTERVAL,false,List.of(),clock.instant().minus(Duration.ofHours(1)),60));
        when(generator.execute(any())).thenReturn(new DelegationTaskExecutor.TaskExecutionResult("一条练习",RunStatus.SUCCEEDED,List.of(),List.of()));
        var task=service.create(description("每小时给我一个写作练习")); var first=service.run(task.id()); await(first.id(),RunStatus.SUCCEEDED,null);
        clock.advance(Duration.ofMinutes(20)); assertThat(service.run(task.id()).id()).isEqualTo(first.id());
        clock.advance(Duration.ofMinutes(40)); service.tick();
        waitUntil(()->service.runs(task.id(),0,20).total()==2 && service.runs(task.id(),0,20).list().getFirst().status()==RunStatus.SUCCEEDED);
        assertThat(service.get(task.id()).nextRunAt()).isEqualTo(clock.instant().plus(Duration.ofHours(1)));
        clock.advance(Duration.ofHours(5)); service.tick();
        waitUntil(()->service.runs(task.id(),0,20).total()==3 && workersIdle());
        assertThat(service.runs(task.id(),0,20).list().stream().map(RunSnapshot::occurrenceKey).distinct()).hasSize(3);
        verify(generator,times(3)).execute(any());
    }
    @Test void aOneTimeTaskRemainsIdempotentAfterRestartAndOnLaterDays() throws Exception {
        when(planner.plan(any())).thenReturn(plan(ScheduleType.ONCE,false,List.of(),clock.instant().plusSeconds(600),null));
        when(generator.execute(any())).thenReturn(new DelegationTaskExecutor.TaskExecutionResult("一次练习",RunStatus.SUCCEEDED,List.of(),List.of()));
        var task=service.create(description("十分钟后给我一个写作练习")); clock.advance(Duration.ofMinutes(10)); service.tick();
        waitUntil(()->service.runs(task.id(),0,20).total()==1 && service.runs(task.id(),0,20).list().getFirst().status()==RunStatus.SUCCEEDED);
        var first=service.runs(task.id(),0,20).list().getFirst(); assertThat(service.get(task.id()).nextRunAt()).isNull();
        service.recover(true); clock.advance(Duration.ofDays(3)); service.tick();
        assertThat(service.run(task.id()).id()).isEqualTo(first.id()); assertThat(service.runs(task.id(),0,20).total()).isEqualTo(1);
        verify(generator,times(1)).execute(any());
    }
    @Test void aPausedTaskCannotStartADeferredToolOperation() throws Exception {
        when(planner.plan(any())).thenReturn(plan(ScheduleType.DAILY,true,List.of("read_public_source"),null,null));
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1); var actions=new AtomicInteger();
        when(generator.execute(any())).thenAnswer(invocation->{
            DelegationTaskExecutor.TaskExecutionRequest request=invocation.getArgument(0);
            entered.countDown(); assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();
            request.guard().execute(()->actions.incrementAndGet()); return brief();
        });
        var task=service.create(description("每天九点检查公开资讯")); var run=service.run(task.id()); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        service.setEnabled(task.id(),false); release.countDown(); waitUntil(this::workersIdle);
        assertThat(actions).hasValue(0); assertThat(repo.run(run.id()).status()).isEqualTo(RunStatus.CANCELLED);
    }
    @Test void editingAnUnchangedLegacyDescriptionPreservesItsExistingScopeSourcesAndSchedule() {
        var task=service.create(input(DeliveryMode.CONSOLE,true));
        var edited=service.update(task.id(),new TaskInput(device,role,null,null,null,null,null,null,"America/New_York",0,null,true,task.description(),null,null));
        assertThat(edited.localTime()).isEqualTo(task.localTime()); assertThat(edited.sourceUrls()).isEqualTo(task.sourceUrls());
        assertThat(edited.zoneId()).isEqualTo("America/New_York"); assertThat(edited.understanding()).contains("America/New_York");
        verifyNoInteractions(planner);
    }
    private TaskInput description(String description) {
        return new TaskInput(device,role,null,null,null,null,null,null,"Asia/Shanghai",0,null,true,description,null,null);
    }
    private DelegationTaskPlanner.TaskPlan plan(ScheduleType type,boolean requires,List<String> required,Instant once,Integer interval) {
        var allowed=new java.util.ArrayList<>(required); allowed.add("current_time");
        return new DelegationTaskPlanner.TaskPlan("通用任务","每天九点执行你的任务，成果留在控制台。",type,List.of(),LocalTime.of(9,0),
                "Asia/Shanghai",once,interval,DeliveryMode.CONSOLE,requires,allowed,required,null);
    }

    @Test void migrationIsRepeatableAndDeviceCascadeRemovesTaskAndRun() throws Exception {
        var task=service.create(input(DeliveryMode.CONSOLE,true));
        var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,null);
        assertThat(Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword()).load().migrate().migrationsExecuted).isZero();
        jdbc.update("delete from devices where id=?",device);
        assertThat(jdbc.queryForObject("select count(*) from delegations",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from delegation_runs",Integer.class)).isZero();
    }
    @Test void concurrentManualAndSchedulerCallsShareOnePersistentDailySlot() throws Exception {
        var task=service.create(input(DeliveryMode.CONSOLE,true));
        jdbc.update("update delegations set next_run_at=? where id=?",DelegationRepository.time(clock.instant().minusSeconds(1)),task.id());
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(generator.execute(any())).thenAnswer(invocation->{ entered.countDown(); assertThat(release.await(5,TimeUnit.SECONDS)).isTrue(); return brief(); });
        try(var callers=Executors.newFixedThreadPool(8)) {
            var calls=new java.util.ArrayList<java.util.concurrent.Future<RunSnapshot>>();
            for(int i=0;i<8;i++) calls.add(callers.submit(()->service.run(task.id())));
            assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue(); service.tick();
            UUID slot=calls.getFirst().get(5,TimeUnit.SECONDS).id();
            for(var call:calls) assertThat(call.get(5,TimeUnit.SECONDS).id()).isEqualTo(slot);
            release.countDown(); await(slot,RunStatus.SUCCEEDED,null);
            verify(generator,times(1)).execute(any());
            assertThat(service.runs(task.id(),0,20).total()).isEqualTo(1);
            assertThat(service.get(task.id()).nextRunAt()).isAfter(clock.instant());
        }
    }
    @ParameterizedTest @ValueSource(strings={"pause","update","delete","archive"})
    void aLateGenerationCannotDeliverAfterTheDelegationChanges(String change) throws Exception {
        configurePush();
        UUID scopedRole=change.equals("archive")?insertRole():role;
        var task=service.create(withRole(input(DeliveryMode.BARK,true),scopedRole));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        when(generator.execute(any())).thenAnswer(invocation->{ entered.countDown(); release.await(5,TimeUnit.SECONDS); finished.countDown(); return brief(); });
        var run=service.run(task.id()); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        switch(change) {
            case "pause" -> service.setEnabled(task.id(),false);
            case "update" -> service.update(task.id(),withTitle(task.enabled()?withRole(input(DeliveryMode.BARK,true),scopedRole):input(DeliveryMode.BARK,false),"新主题"));
            case "delete" -> service.delete(task.id());
            case "archive" -> archiveRole(scopedRole);
        }
        release.countDown(); assertThat(finished.await(5,TimeUnit.SECONDS)).isTrue();
        waitUntil(this::workersIdle);
        verify(bark,never()).send(anyString(),anyString(),anyString(),anyString());
        if(change.equals("delete")) assertThat(repo.run(run.id())).isNull();
        else assertThat(repo.run(run.id()).status()).isEqualTo(RunStatus.CANCELLED);
    }
    @Test void expiredGenerationCanBeManuallyRetriedWithoutAllowingItsOldAttemptToOverwrite() throws Exception {
        var task=service.create(input(DeliveryMode.CONSOLE,true));
        AtomicInteger calls=new AtomicInteger(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(generator.execute(any())).thenAnswer(invocation->{
            if(calls.incrementAndGet()==1) { entered.countDown(); release.await(5,TimeUnit.SECONDS); return new DelegationTaskExecutor.TaskExecutionResult("没有新内容",RunStatus.NO_UPDATES,List.of(),List.of()); }
            return brief();
        });
        var first=service.run(task.id()); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        clock.advance(Duration.ofMinutes(11)); service.recover(false);
        assertThat(repo.run(first.id()).status()).isEqualTo(RunStatus.FAILED);
        var second=service.run(task.id()); assertThat(second.id()).isEqualTo(first.id());
        await(second.id(),RunStatus.SUCCEEDED,null); release.countDown();
        waitUntil(this::workersIdle);
        assertThat(repo.run(first.id()).attempt()).isEqualTo(2);
        assertThat(repo.run(first.id()).output()).isEqualTo("已执行任务");
    }
    @Test void deliveryRetryOnlyUsesTheSavedBrief() throws Exception {
        configurePush(); when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.FAILED,BarkPushClient.DeliveryOutcome.SENT);
        var task=service.create(input(DeliveryMode.BARK,true)); var run=service.run(task.id());
        await(run.id(),RunStatus.SUCCEEDED,DeliveryStatus.FAILED);
        var resent=service.retryDelivery(run.id()); assertThat(resent.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(resent.deliveryAttempts()).isEqualTo(2); verify(generator,times(1)).execute(any());
        verify(bark,times(2)).send(anyString(),anyString(),anyString(),anyString());
    }
    @Test void unknownOutcomeIsNeverAutomaticallyOrManuallyRepeated() throws Exception {
        configurePush(); when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.UNKNOWN);
        var task=service.create(input(DeliveryMode.BARK,true)); var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,DeliveryStatus.UNKNOWN);
        clock.advance(Duration.ofMinutes(20)); service.tick(); service.recover(true);
        assertThatThrownBy(()->service.retryDelivery(run.id())).isInstanceOf(DelegationException.class).hasMessage("DELEGATION_DELIVERY_NOT_RETRYABLE");
        verify(bark,times(1)).send(anyString(),anyString(),anyString(),anyString());
    }
    @Test void knownFailureRetriesAreBoundedToThreeAttempts() throws Exception {
        configurePush(); when(bark.send(anyString(),anyString(),anyString(),anyString())).thenReturn(BarkPushClient.DeliveryOutcome.FAILED);
        var task=service.create(input(DeliveryMode.BARK,true)); var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,DeliveryStatus.FAILED);
        for(int i=2;i<=3;i++) {
            clock.advance(Duration.ofMinutes(6)); service.tick();
            final int target=i; waitUntil(()->repo.run(run.id()).deliveryAttempts()==target && repo.run(run.id()).deliveryStatus()==DeliveryStatus.FAILED);
        }
        clock.advance(Duration.ofMinutes(6)); service.tick();
        assertThat(repo.run(run.id()).deliveryNextAt()).isNull(); verify(bark,times(3)).send(anyString(),anyString(),anyString(),anyString());
        verify(generator,times(1)).execute(any());
    }
    @Test void pushKeysAreEncryptedWriteOnlyPreservedWhenBlankAndExplicitlyCleared() throws Exception {
        configurePush();
        String stored=jdbc.queryForObject("select key_ciphertext from delegation_push_settings",String.class);
        assertThat(stored).doesNotContain("fixture-device-key");
        assertThat(repo.encode(service.pushSettings())).doesNotContain("fixture-device-key",stored,"ciphertext","deviceKey");
        service.savePushSettings(new PushSettingsInput("https://api.day.app","",false,true));
        assertThat(jdbc.queryForObject("select key_ciphertext from delegation_push_settings",String.class)).isEqualTo(stored);
        service.savePushSettings(new PushSettingsInput("https://api.day.app","",true,false));
        assertThat(service.pushSettings().keyConfigured()).isFalse();
        assertThatThrownBy(()->service.create(input(DeliveryMode.BARK,true))).hasMessage("DELEGATION_PUSH_NOT_CONFIGURED");
        assertThat(service.create(input(DeliveryMode.BARK,false)).enabled()).isFalse();
    }
    @Test void taskScopeCannotBeChangedAndAgentResultsRespectDeviceAndRole() throws Exception {
        UUID otherRole=insertRole(); var task=service.create(input(DeliveryMode.CONSOLE,true)); var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,null);
        assertThatThrownBy(()->service.update(task.id(),withRole(input(DeliveryMode.CONSOLE,true),otherRole))).hasMessage("DELEGATION_SCOPE_IMMUTABLE");
        assertThat(service.latestForDeviceRole(device,role)).hasSize(1);
        assertThat(service.latestForDeviceRole(UUID.randomUUID(),role)).isEmpty();
        assertThat(service.latestForDeviceRole(device,otherRole)).isEmpty();
        jdbc.update("delete from companion_roles where id=?",otherRole);
    }
    @Test void noUpdatesIsExplicitAndDoesNotPushOrInventItems() throws Exception {
        configurePush(); when(generator.execute(any())).thenReturn(new DelegationTaskExecutor.TaskExecutionResult("来源暂不可用",RunStatus.NO_UPDATES,List.of(),List.of()));
        var task=service.create(input(DeliveryMode.BARK,true)); var run=service.run(task.id()); await(run.id(),RunStatus.NO_UPDATES,DeliveryStatus.NONE);
        assertThat(repo.run(run.id()).output()).isEqualTo("来源暂不可用");
        verifyNoInteractions(bark);
    }
    @Test void restartingMarksInterruptedSendingUnknownInsteadOfReplaying() throws Exception {
        var task=service.create(input(DeliveryMode.CONSOLE,true)); var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,null);
        jdbc.update("update delegation_runs set delivery_status='SENDING',delivery_attempts=1 where id=?",run.id()); service.recover(true); service.tick();
        assertThat(repo.run(run.id()).deliveryStatus()).isEqualTo(DeliveryStatus.UNKNOWN); verifyNoInteractions(bark);
    }
    @Test void pauseWaitsForAnAlreadyStartedSendAndPreventsEveryLaterSend() throws Exception {
        configurePush(); var task=service.create(input(DeliveryMode.BARK,true));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(bark.send(anyString(),anyString(),anyString(),anyString())).thenAnswer(invocation->{ entered.countDown(); release.await(5,TimeUnit.SECONDS); return BarkPushClient.DeliveryOutcome.SENT; });
        var run=service.run(task.id()); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        try(var pauseCaller=Executors.newSingleThreadExecutor()) {
            CountDownLatch requested=new CountDownLatch(1);
            var paused=pauseCaller.submit(()->{ requested.countDown(); return service.setEnabled(task.id(),false); });
            assertThat(requested.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->paused.get(100,TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown(); assertThat(paused.get(5,TimeUnit.SECONDS).enabled()).isFalse();
            waitUntil(this::workersIdle); service.tick();
            assertThat(repo.run(run.id()).deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
            verify(bark,times(1)).send(anyString(),anyString(),anyString(),anyString());
        }
    }
    @Test void recoveryBetweenTheCompletionReadAndWriteCannotReviveAFailedRun() throws Exception {
        configurePush(); var task=service.create(input(DeliveryMode.BARK,true));
        var injected=new java.util.concurrent.atomic.AtomicBoolean();
        try(var recovery=Executors.newSingleThreadExecutor()) {
            doAnswer(invocation->{
                RunEntity captured=(RunEntity)invocation.callRealMethod();
                if(Thread.currentThread().getName().equals("delegation-worker") && captured!=null
                        && captured.status()==RunStatus.RUNNING && injected.compareAndSet(false,true)) {
                    // A separate connection commits recovery after the worker has read RUNNING and before it writes its result.
                    recovery.submit(()->service.recover(true)).get(5,TimeUnit.SECONDS);
                }
                return captured;
            }).when(repo).run(any(UUID.class));
            var run=service.run(task.id()); waitUntil(injected::get); waitUntil(this::workersIdle);
            assertThat(injected).isTrue(); assertThat(repo.run(run.id()).status()).isEqualTo(RunStatus.FAILED);
            assertThat(repo.run(run.id()).items()).isEmpty(); verifyNoInteractions(bark);
        }
    }
    @Test void excessManualWorkFailsVisiblyInsteadOfWaitingInAnUnboundedQueue() throws Exception {
        CountDownLatch entered=new CountDownLatch(2),release=new CountDownLatch(1);
        when(generator.execute(any())).thenAnswer(invocation->{ entered.countDown(); release.await(5,TimeUnit.SECONDS); return brief(); });
        var first=service.create(input(DeliveryMode.CONSOLE,true));
        var second=service.create(withTitle(input(DeliveryMode.CONSOLE,true),"第二个委托"));
        var third=service.create(withTitle(input(DeliveryMode.CONSOLE,true),"第三个委托"));
        service.run(first.id()); service.run(second.id()); assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();
        var overflow=service.run(third.id()); assertThat(overflow.status()).isEqualTo(RunStatus.FAILED);
        assertThat(overflow.errorCode()).isEqualTo("DELEGATION_CAPACITY");
        release.countDown(); waitUntil(this::workersIdle);
        verify(generator,times(2)).execute(any());
        var retried=service.run(third.id()); await(retried.id(),RunStatus.SUCCEEDED,null);
        assertThat(retried.id()).isEqualTo(overflow.id()); assertThat(repo.run(retried.id()).attempt()).isEqualTo(2);
        verify(generator,times(3)).execute(any());
    }
    @Test void permanentlyDeletingARoleCascadesItsDelegationsAndResults() throws Exception {
        UUID other=insertRole(); var task=service.create(withRole(input(DeliveryMode.CONSOLE,true),other));
        var run=service.run(task.id()); await(run.id(),RunStatus.SUCCEEDED,null); waitUntil(this::workersIdle);
        jdbc.update("delete from companion_roles where id=?",other);
        assertThat(repo.task(task.id(),false)).isNull(); assertThat(repo.run(run.id())).isNull();
    }
    @Test void upgradesThePreviousSchemaAndPreservesEveryExistingMigrationChecksumAndPersonalRow() {
        // On the task branch target 57 stops at V56. The same test includes the retained V57 in the integration source.
        String schema="delegation_upgrade_"+UUID.randomUUID().toString().replace("-","");
        var configuration=Flyway.configure().dataSource(POSTGRES.getJdbcUrl(),POSTGRES.getUsername(),POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema);
        var previous=java.util.Arrays.stream(configuration.load().info().all()).map(org.flywaydb.core.api.MigrationInfo::getVersion)
                .filter(java.util.Objects::nonNull).filter(version->version.compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("58"))<0)
                .max(java.util.Comparator.naturalOrder()).orElseThrow();
        var baseline=configuration.target(previous.toString()).load(); baseline.migrate();
        assertThat(baseline.info().current().getVersion().toString()).isIn("56","57");
        var checksums=jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where success and version is not null order by installed_rank");
        UUID retainedDevice=UUID.randomUUID(),conversation=UUID.randomUUID();
        jdbc.update("insert into "+schema+".devices(id,hardware_id,firmware_version) values(?,?,?)",retainedDevice,"upgrade-retained","test");
        jdbc.update("insert into "+schema+".conversations(id,title,created_at,updated_at,role_id) values(?,'原有对话',now(),now(),?)",conversation,role);
        var upgraded=configuration.target("58").load(); assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgraded.info().current().getVersion().toString()).isEqualTo("58");
        assertThat(jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where success and version is not null and version<>'58' order by installed_rank"))
                .isEqualTo(checksums);
        assertThat(jdbc.queryForObject("select title from "+schema+".conversations where id=?",String.class,conversation)).isEqualTo("原有对话");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('delegations','delegation_runs','delegation_push_settings')",Integer.class,schema)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from "+schema+".delegation_push_settings",Integer.class)).isEqualTo(1);
        assertThat(upgraded.validateWithResult().validationSuccessful).isTrue();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }
    private void configurePush() { service.savePushSettings(new PushSettingsInput("https://api.day.app","fixture-device-key",false,true)); }
    private TaskInput input(DeliveryMode mode,boolean enabled) {
        return new TaskInput(device,role,"AI 日报","整理重要进展并说明对我的影响",List.of("https://example.com/feed.xml"),
                ScheduleType.DAILY,List.of(),LocalTime.of(8,30),"Asia/Shanghai",5,mode,enabled);
    }
    private static TaskInput withRole(TaskInput i,UUID role) { return new TaskInput(i.deviceId(),role,i.title(),i.instruction(),i.sourceUrls(),i.scheduleType(),i.daysOfWeek(),i.localTime(),i.zoneId(),i.maxItems(),i.deliveryMode(),i.enabled()); }
    private static TaskInput withTitle(TaskInput i,String title) { return new TaskInput(i.deviceId(),i.roleId(),title,i.instruction(),i.sourceUrls(),i.scheduleType(),i.daysOfWeek(),i.localTime(),i.zoneId(),i.maxItems(),i.deliveryMode(),i.enabled()); }
    private UUID insertRole() {
        UUID id=UUID.randomUUID(); jdbc.update("""
                insert into companion_roles(id,name,tone,reply_length,proactivity,background_instructions,topic_boundaries,taboos,is_default,created_at,updated_at)
                values(?,'测试伙伴','WARM','SHORT','BALANCED','','','',false,now(),now())
                """,id); return id;
    }
    private void archiveRole(UUID id) {
        var roleRepository=mock(com.kj.stackchan.role.CompanionRoleRepository.class);
        var roles=mock(com.kj.stackchan.role.DeviceActiveRoleRepository.class);
        var entity=new CompanionRoleEntity("测试伙伴",com.kj.stackchan.persona.PersonaTone.values()[0],
                com.kj.stackchan.persona.PersonaReplyLength.values()[0],com.kj.stackchan.persona.PersonaProactivity.values()[0],"","","",clock.instant());
        org.springframework.test.util.ReflectionTestUtils.setField(entity,"id",id);
        when(roleRepository.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(entity));
        var defaultRole=mock(CompanionRoleEntity.class); when(defaultRole.getId()).thenReturn(role);
        when(roleRepository.findByDefaultRoleTrue()).thenReturn(java.util.Optional.of(defaultRole));
        var roleService=new com.kj.stackchan.role.CompanionRoleService(roleRepository,roles,mock(com.kj.stackchan.device.DeviceRepository.class),
                mock(com.kj.stackchan.speech.VoiceTurnRepository.class),mock(com.kj.stackchan.reminder.ReminderRepository.class),clock,
                mock(com.kj.stackchan.notification.NotificationIntegrationRepository.class));
        org.springframework.test.util.ReflectionTestUtils.setField(roleService,"delegationJdbc",jdbc);
        new TransactionTemplate(transactionManager).executeWithoutResult(status->roleService.archive(id));
        assertThat(service.get(repo.jdbc().queryForObject("select id from delegations where role_id=?",UUID.class,id)).enabled()).isFalse();
    }
    private static DelegationTaskExecutor.TaskExecutionResult brief() {
        return new DelegationTaskExecutor.TaskExecutionResult("已执行任务",RunStatus.SUCCEEDED,
                List.of(new DelegationTaskExecutor.ExecutionStep("read_public_source","SUCCEEDED","实际执行完成")),List.of());
    }
    private void await(UUID id,RunStatus status,DeliveryStatus delivery) throws Exception {
        waitUntil(()->{ var r=repo.run(id); return r!=null && r.status()==status && (delivery==null || r.deliveryStatus()==delivery); });
    }
    private boolean workersIdle() {
        return ((java.util.concurrent.ThreadPoolExecutor)org.springframework.test.util.ReflectionTestUtils.getField(service,"executor")).getActiveCount()==0;
    }
    private static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(!condition.getAsBoolean() && System.nanoTime()<end) Thread.sleep(15);
        assertThat(condition.getAsBoolean()).as("background operation finished").isTrue();
    }
    private static class MutableClock extends Clock {
        volatile Instant now=Instant.parse("2026-10-09T01:00:00Z");
        void advance(Duration amount) { now=now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
