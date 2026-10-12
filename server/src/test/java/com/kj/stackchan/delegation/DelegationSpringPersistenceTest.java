package com.kj.stackchan.delegation;

import java.net.URI;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.role.CompanionRoleEntity;
import com.kj.stackchan.role.CompanionRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.kj.stackchan.delegation.DelegationService.*;

/** Full Spring context exercises exception-translation proxies, startup recovery and the real role transaction. */
@SpringBootTest
@Testcontainers
class DelegationSpringPersistenceTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",POSTGRES::getUsername);
        registry.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @Autowired DelegationRepository repository;
    @Autowired DelegationService service;
    @Autowired CompanionRoleService roles;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean DelegationTaskExecutor generator;
    @MockitoBean DelegationTaskPlanner planner;
    @MockitoBean PublicHttpsUrlPolicy policy;
    @MockitoBean BarkPushClient bark;
    private UUID device;

    @BeforeEach void setup() {
        jdbc.update("delete from devices");
        jdbc.update("update delegation_push_settings set key_ciphertext=null,key_iv=null,enabled=false where id=1");
        device=UUID.randomUUID(); jdbc.update("insert into devices(id,hardware_id,firmware_version) values(?,?,?)",device,"spring-delegation-"+device,"test");
        when(policy.validate(anyString())).thenAnswer(invocation->URI.create(invocation.getArgument(0)));
        when(generator.execute(any())).thenReturn(new DelegationTaskExecutor.TaskExecutionResult("没有新内容",RunStatus.NO_UPDATES,List.of(),List.of()));
    }
    @Test void startsAndReadsWritesAndRecoversThroughTheRealRepositoryProxy() throws Exception {
        assertThat(AopUtils.isCglibProxy(repository)).isTrue();
        service.recover(true);
        var task=service.create(input(CompanionRoleEntity.DEFAULT_ROLE_ID));
        assertThat(service.get(task.id()).title()).isEqualTo("持续资料");
        assertThat(service.list(device,task.roleId(),0,20).total()).isEqualTo(1);
        var run=service.run(task.id());
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(service.runs(task.id(),0,20).list().getFirst().status()==RunStatus.RUNNING && System.nanoTime()<end) Thread.sleep(15);
        assertThat(service.runs(task.id(),0,20).list().getFirst().status()).isEqualTo(RunStatus.NO_UPDATES);
        assertThat(service.latestForDeviceRole(device,task.roleId()).getFirst().id()).isEqualTo(run.id());
        service.savePushSettings(new PushSettingsInput("https://api.day.app","spring-fixture-key",false,true));
        assertThat(service.pushSettings().keyConfigured()).isTrue();
        assertThat(service.setEnabled(task.id(),false).enabled()).isFalse();
        service.delete(task.id()); assertThat(service.list(device,null,0,20).total()).isZero();
        verifyNoInteractions(bark);
    }
    @Test void realRoleArchiveTransactionCancelsPendingWorkAndRestoreDoesNotResumeIt() {
        var role=roles.create(new CompanionRoleService.RoleCommand("委托伙伴",PersonaTone.WARM,PersonaReplyLength.SHORT,
                PersonaProactivity.BALANCED,"","","",null,"#FF4FA3"));
        var task=service.create(input(role.id())); UUID runId=UUID.randomUUID();
        jdbc.update("""
                insert into delegation_runs(id,task_id,task_title,scheduled_date,task_revision,attempt,status,created_at,started_at,occurrence_key,scheduled_at)
                values(?,?,?,current_date,1,1,'RUNNING',now(),now(),'local:'||current_date::text,now())
                """,runId,task.id(),task.title());
        assertThat(roles.archive(role.id()).archivedAt()).isNotNull();
        assertThat(service.get(task.id()).enabled()).isFalse();
        assertThat(service.runs(task.id(),0,20).list().getFirst().status()).isEqualTo(RunStatus.CANCELLED);
        roles.restore(role.id()); assertThat(service.get(task.id()).enabled()).isFalse();
        verifyNoInteractions(generator,bark);
    }
    private TaskInput input(UUID role) {
        return new TaskInput(device,role,"持续资料","根据公开来源整理",List.of("https://example.com/rss"),ScheduleType.DAILY,List.of(),
                LocalTime.of(8,30),"Asia/Shanghai",5,DeliveryMode.CONSOLE,true);
    }
}
