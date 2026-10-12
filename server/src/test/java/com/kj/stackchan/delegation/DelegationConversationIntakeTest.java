package com.kj.stackchan.delegation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import com.kj.stackchan.agent.AgentChannel;
import com.kj.stackchan.agent.AgentInvocationContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static com.kj.stackchan.delegation.DelegationService.*;

class DelegationConversationIntakeTest {
    private final DelegationService tasks=mock(DelegationService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"),ZoneOffset.UTC);
    private final DelegationConversationIntake intake=new DelegationConversationIntake(tasks,jdbc,clock);
    private final AgentInvocationContext context=new AgentInvocationContext(UUID.randomUUID(),UUID.randomUUID(),
            UUID.randomUUID(),UUID.randomUUID(),AgentChannel.WEB);
    private TaskSnapshot accepted() {
        return new TaskSnapshot(UUID.randomUUID(),context.deviceId(),context.roleId(),"每日练习","每天九点给我练习",List.of(),
                ScheduleType.DAILY,List.of(),LocalTime.of(9,0),"Asia/Shanghai",5,DeliveryMode.CONSOLE,true,
                clock.instant(),clock.instant(),clock.instant(),null,"每天九点给我练习","每天九点给你写作练习。",null,null,
                false,List.of(),List.of());
    }
    @Test void anExplicitTaskUsesServerScopeAndAcknowledgesOnlyAPersistedTask() {
        when(tasks.create(any())).thenReturn(accepted());
        assertThat(intake.supports(context,"每天九点给我一个写作练习")).isTrue();
        assertThat(intake.accept(context,"每天九点给我一个写作练习")).contains("已接下","每天九点");
        var input=ArgumentCaptor.forClass(TaskInput.class); verify(tasks).create(input.capture());
        assertThat(input.getValue().deviceId()).isEqualTo(context.deviceId());
        assertThat(input.getValue().roleId()).isEqualTo(context.roleId());
        assertThat(input.getValue().description()).isEqualTo("每天九点给我一个写作练习");
        assertThat(input.getValue().sourceUrls()).isEmpty(); verifyNoInteractions(jdbc);
    }
    @Test void missingTimeIsAskedAndTheReplyIsBoundToThisConversationAndRole() {
        when(tasks.create(any())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","希望每天几点？"))
                .thenReturn(accepted());
        assertThat(intake.accept(context,"每天给我一个写作练习")).isEqualTo("希望每天几点？");
        var followup=new AgentInvocationContext(UUID.randomUUID(),context.conversationId(),context.deviceId(),context.roleId(),AgentChannel.WEB);
        assertThat(intake.supports(followup,"九点")).isTrue();
        assertThat(intake.supports(new AgentInvocationContext(UUID.randomUUID(),UUID.randomUUID(),context.deviceId(),context.roleId(),AgentChannel.WEB),"九点")).isFalse();
        assertThat(intake.accept(followup,"九点")).contains("已接下");
        var input=ArgumentCaptor.forClass(TaskInput.class); verify(tasks,times(2)).create(input.capture());
        assertThat(input.getValue().description()).contains("每天给我一个写作练习","补充说明：九点");
        assertThat(intake.supports(followup,"九点")).isFalse();
    }
    @Test void decliningAClarificationDoesNotCreateAnotherTask() {
        when(tasks.create(any())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","几点？"));
        intake.accept(context,"每天给我练习");
        assertThat(intake.accept(context,"取消")).contains("取消");
        verify(tasks,times(1)).create(any()); assertThat(intake.supports(context,"九点")).isFalse();
    }
    @Test void hypotheticalExamplesAndQueriesNeverCreateTasks() {
        for(String message:List.of("例如每天九点给我一个练习","如果每天下午帮我开电脑呢","任务结果怎么样","你好","每天的委托有哪些"))
            assertThat(intake.supports(context,message)).as(message).isFalse();
        var background=new AgentInvocationContext(UUID.randomUUID(),null,context.deviceId(),context.roleId(),AgentChannel.DELEGATION);
        assertThat(intake.supports(background,"每天九点给我练习")).isFalse(); verifyNoInteractions(tasks);
    }
    @Test void unavailablePushDoesNotPretendTheTaskWasAccepted() {
        when(tasks.create(any())).thenThrow(new DelegationException("DELEGATION_PUSH_NOT_CONFIGURED"));
        assertThat(intake.accept(context,"每天九点把练习发手机")).contains("还没有保存","Bark").doesNotContain("已接下");
    }
}
