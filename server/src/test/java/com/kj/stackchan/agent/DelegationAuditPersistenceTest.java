package com.kj.stackchan.agent;

import java.util.UUID;

import com.kj.stackchan.delegation.BarkPushClient;
import com.kj.stackchan.delegation.DelegationTaskExecutor;
import com.kj.stackchan.delegation.DelegationTaskPlanner;
import com.kj.stackchan.llm.LlmRuntimeClientFactory;
import com.kj.stackchan.role.CompanionRoleEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/** Checks real entity mapping and transaction commit; best-effort agent auditing must not hide a schema mismatch. */
@SpringBootTest
@Testcontainers
class DelegationAuditPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse(
            "postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AgentToolAuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired DelegationTaskPlanner planner;
    @Autowired DelegationTaskExecutor executor;
    @Autowired AgentOrchestrator orchestrator;
    @MockitoBean LlmRuntimeClientFactory llm;
    @MockitoBean BarkPushClient bark;

    @Test
    void recordsBackgroundScopeAndAttemptThroughTheRealAuditServiceWithoutAConversation() {
        UUID deviceId = UUID.randomUUID(), runId = UUID.randomUUID(), turnId = UUID.randomUUID();
        jdbc.update("insert into devices(id,hardware_id,firmware_version) values(?,?,?)",
                deviceId, "delegation-audit-fixture-" + deviceId, "test");
        var context = new AgentInvocationContext(turnId, null, deviceId, CompanionRoleEntity.DEFAULT_ROLE_ID,
                AgentChannel.DELEGATION, runId, 3);

        assertThat(planner).isNotNull();
        assertThat(executor).isNotNull();
        assertThat(orchestrator).isNotNull();

        audit.record(context, null, CurrentTimeTool.ID, AgentToolSource.BUILTIN, null,
                AgentToolOutcome.SUCCESS, 5, 96, false);

        assertThat(jdbc.queryForMap("select turn_id,conversation_id,device_id,channel,delegation_run_id,delegation_attempt,"
                + "tool_name,outcome from agent_tool_invocations where turn_id=?", turnId))
                .containsEntry("turn_id", turnId).containsEntry("conversation_id", null).containsEntry("device_id", deviceId)
                .containsEntry("channel", "DELEGATION").containsEntry("delegation_run_id", runId)
                .containsEntry("delegation_attempt", 3).containsEntry("tool_name", CurrentTimeTool.ID)
                .containsEntry("outcome", "SUCCESS");
        jdbc.update("delete from devices where id=?", deviceId);
        assertThat(jdbc.queryForMap("select device_id,delegation_run_id,delegation_attempt from agent_tool_invocations where turn_id=?", turnId))
                .containsEntry("device_id", null).containsEntry("delegation_run_id", runId).containsEntry("delegation_attempt", 3);
        verifyNoInteractions(llm, bark);
    }
}
