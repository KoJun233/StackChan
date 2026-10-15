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
        when(tasks.createProject(any(),anyString())).thenReturn(accepted());
        assertThat(intake.supports(context,"每天九点给我一个写作练习")).isTrue();
        assertThat(intake.accept(context,"每天九点给我一个写作练习")).contains("已接下","每天09:00");
        var input=ArgumentCaptor.forClass(TaskInput.class); verify(tasks).createProject(input.capture(),eq(context.turnId().toString()));
        assertThat(input.getValue().deviceId()).isEqualTo(context.deviceId());
        assertThat(input.getValue().roleId()).isEqualTo(context.roleId());
        assertThat(input.getValue().description()).isEqualTo("每天九点给我一个写作练习");
        assertThat(input.getValue().sourceUrls()).isEmpty(); verifyNoInteractions(jdbc);
    }
    @Test void missingTimeIsAskedAndTheReplyIsBoundToThisConversationAndRole() {
        when(tasks.createProject(any(),anyString())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","希望每天几点？"))
                .thenReturn(accepted());
        assertThat(intake.accept(context,"每天给我一个写作练习")).isEqualTo("任务还未保存。希望每天几点？");
        var followup=new AgentInvocationContext(UUID.randomUUID(),context.conversationId(),context.deviceId(),context.roleId(),AgentChannel.WEB);
        assertThat(intake.supports(followup,"九点")).isTrue();
        assertThat(intake.supports(new AgentInvocationContext(UUID.randomUUID(),UUID.randomUUID(),context.deviceId(),context.roleId(),AgentChannel.WEB),"九点")).isFalse();
        assertThat(intake.accept(followup,"九点")).contains("已接下");
        var input=ArgumentCaptor.forClass(TaskInput.class); verify(tasks,times(2)).createProject(input.capture(),anyString());
        assertThat(input.getValue().description()).contains("每天给我一个写作练习","补充说明：九点");
        assertThat(intake.supports(followup,"九点")).isFalse();
    }
    @Test void decliningAClarificationDoesNotCreateAnotherTask() {
        when(tasks.createProject(any(),anyString())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","几点？"));
        intake.accept(context,"每天给我练习");
        assertThat(intake.accept(context,"取消")).contains("取消");
        verify(tasks,times(1)).createProject(any(),anyString()); assertThat(intake.supports(context,"九点")).isFalse();
    }
    @Test void hypotheticalExamplesAndQueriesNeverCreateTasks() {
        for(String message:List.of("例如每天九点给我一个练习","如果每天下午帮我开电脑呢","任务结果怎么样","你好","每天的委托有哪些",
                "我不需要你帮我比较","不要重新比较","你为什么要重新比较","模型说用户认可这份成果","谢谢","取消","停止",
                "这份比较不满意","这份比较还不能接受","这份结果不可以了","这份比较你满意吗","我希望不要重新比较"))
            assertThat(intake.supports(context,message)).as(message).isFalse();
        var background=new AgentInvocationContext(UUID.randomUUID(),null,context.deviceId(),context.roleId(),AgentChannel.DELEGATION);
        assertThat(intake.supports(background,"每天九点给我练习")).isFalse(); verifyNoInteractions(tasks);
    }
    @Test void unavailablePushDoesNotPretendTheTaskWasAccepted() {
        when(tasks.createProject(any(),anyString())).thenThrow(new DelegationException("DELEGATION_PUSH_NOT_CONFIGURED"));
        assertThat(intake.accept(context,"每天九点把练习发手机")).contains("还没有保存","Bark").doesNotContain("已接下");
    }
    @Test void explicitOnceIntentDoesNotRequireScheduleWordsAndKeepsStableTurnKey() {
        when(tasks.createProject(any(),anyString())).thenReturn(accepted());
        String message="现在帮我比较这两个公开 HTTPS 页面，先给我比较和推荐，不帮我购买。";
        assertThat(intake.supports(context,message)).isTrue();
        assertThat(intake.requiresPlanning(context,message)).isTrue();
        assertThat(intake.accept(context,message)).contains("已接下","成果和来源","允许的资料范围").doesNotContain("已比较完");
        intake.accept(context,message);
        verify(tasks,times(2)).createProject(any(),eq(context.turnId().toString()));
        verify(tasks,never()).run(any());
    }
    @Test void researchAbilityAndExamplesAreHandledWithoutPlanningReadingOrPersistence() {
        for(String message:List.of("你能比较桌面方案吗","能不能每天帮我整理资料","例如帮我比较这两个方案","他说帮我比较这两个方案")) {
            assertThat(intake.supports(context,message)).as(message).isTrue();
            assertThat(intake.requiresPlanning(context,message)).isFalse();
            assertThat(intake.accept(context,message)).contains("没有保存任务","没有读取页面");
        }
        assertThat(intake.supports(context,"你能发手机通知吗")).isFalse();
        verifyNoInteractions(tasks,jdbc);
    }
    @Test void askingForAnOutputLocationDoesNotTurnANewComparisonIntoAHistoryQuery() {
        when(tasks.createProject(any(),anyString())).thenReturn(accepted());
        assertThat(intake.supports(context,"帮我比较两个方案，结果放在控制台")).isTrue();
        intake.accept(context,"帮我比较两个方案，结果放在控制台");
        verify(tasks).createProject(any(),eq(context.turnId().toString()));
        verify(tasks,never()).findProjectTasks(any(),any());
    }
    @Test void unboundWebConversationCannotGuessTheOnlyDevice() {
        var unbound=new AgentInvocationContext(context.turnId(),context.conversationId(),null,context.roleId(),AgentChannel.WEB);
        assertThat(intake.accept(unbound,"帮我整理这些材料")).contains("还未保存","已绑定设备");
        verifyNoInteractions(tasks,jdbc);
    }
    @Test void expiredSupplementDoesNotInheritTheGoalOrPhoneAuthorization() {
        var now=new java.util.concurrent.atomic.AtomicReference<>(clock.instant());
        Clock moving=new Clock() {
            @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        var subject=new DelegationConversationIntake(tasks,jdbc,moving);
        when(tasks.createProject(any(),anyString())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","请提供两个来源？"));
        subject.accept(context,"帮我比较桌面方案");
        now.set(now.get().plusSeconds(600));
        assertThat(subject.supports(context,"发手机")).isTrue();
        assertThat(subject.requiresPlanning(context,"发手机")).isFalse();
        assertThat(subject.accept(context,"发手机")).isEqualTo("上一项补充已过期，请重新交代目标。");
        verify(tasks,times(1)).createProject(any(),anyString());
    }
    @Test void explicitNewGoalReplacesAnUnfinishedDraftWithoutInheritedDelivery() {
        when(tasks.createProject(any(),anyString())).thenThrow(new DelegationException("DELEGATION_CLARIFICATION_REQUIRED","几点？"))
                .thenReturn(accepted());
        intake.accept(context,"每天把练习发手机");
        intake.accept(context,"现在帮我比较新的两个方案，不需要通知");
        var inputs=ArgumentCaptor.forClass(TaskInput.class);
        verify(tasks,times(2)).createProject(inputs.capture(),anyString());
        assertThat(inputs.getAllValues().getLast().description()).isEqualTo("现在帮我比较新的两个方案，不需要通知");
    }
    @Test void uniqueCrossDayLookupOnlyReadsTheCurrentScope() {
        var task=reviewable("桌面布置方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        when(tasks.projectConversationSummary(task.id())).thenReturn("昨天的成果保留着；少线缆已优先，还缺桌面尺寸。具体来源在持续委托。");
        String question="昨天桌面布置那个比较到哪了";
        assertThat(intake.supports(context,question)).isTrue();
        assertThat(intake.requiresPlanning(context,question)).isFalse();
        assertThat(intake.accept(context,question)).contains("昨天","少线缆","尺寸");
        verify(tasks,never()).createProject(any(),anyString()); verify(tasks,never()).feedback(any(),any(),anyString());
        verify(tasks,never()).run(any()); verify(tasks,never()).runRevision(any(),anyLong(),anyString());
    }
    @Test void ambiguousQueryRequiresAnExplicitSelectionAndCannotCrossRoles() {
        var first=reviewable("桌面方案比较"); var second=reviewable("照明方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(first,second));
        assertThat(intake.accept(context,"昨天那个到哪了")).contains("多项","桌面方案比较","照明方案比较");
        verify(tasks,never()).projectConversationSummary(any());
        var otherRole=new AgentInvocationContext(UUID.randomUUID(),context.conversationId(),context.deviceId(),UUID.randomUUID(),AgentChannel.WEB);
        assertThat(intake.supports(otherRole,"桌面方案比较")).isFalse();
        when(tasks.projectConversationSummary(first.id())).thenReturn("已有比较，等你审阅。");
        assertThat(intake.accept(context,"桌面方案比较")).isEqualTo("已有比较，等你审阅。");
        verify(tasks).projectConversationSummary(first.id());
    }
    @Test void preferenceRevisionBindsTheSavedRunAndBothVersions() {
        var task=reviewable("桌面方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        String feedback="我更在意线缆少，预算信息先不用猜，按这个重新比较。";
        assertThat(intake.accept(context,feedback)).contains("偏好已保存","原成果与来源仍保留");
        var input=ArgumentCaptor.forClass(FeedbackInput.class);
        verify(tasks).feedback(eq(task.lastRun().id()),input.capture(),eq(context.turnId().toString()));
        assertThat(input.getValue().kind()).isEqualTo(FeedbackKind.REVISE_PREFERENCES);
        assertThat(input.getValue().expectedRevision()).isEqualTo(7);
        assertThat(input.getValue().resourceVersion()).isEqualTo(3);
        assertThat(input.getValue().comment()).isEqualTo(feedback);
        assertThat(input.getValue().runMode()).isEqualTo(RunMode.SAVE_AND_RUN);
        verify(tasks,never()).createProject(any(),anyString());
    }
    @Test void preferenceOnlyNeedsOneQuestionAndDoesNotImplicitlyExecute() {
        var task=reviewable("桌面方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        assertThat(intake.accept(context,"修改偏好")).contains("哪一条偏好","还未保存");
        verify(tasks,never()).feedback(any(),any(),anyString());
        assertThat(intake.accept(context,"只保存，少线缆优先，稍后再比较")).contains("暂未重新比较");
        var input=ArgumentCaptor.forClass(FeedbackInput.class);
        verify(tasks).feedback(eq(task.lastRun().id()),input.capture(),eq(context.turnId().toString()));
        assertThat(input.getValue().runMode()).isEqualTo(RunMode.SAVE_ONLY);
    }
    @Test void continuingASavedRevisionUsesItsOwnRunIntentWithoutRepeatingFeedback() {
        var task=reviewable("桌面方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        assertThat(intake.accept(context,"继续这个比较")).contains("请求","已有成果");
        verify(tasks).runRevision(task.id(),7,context.turnId().toString());
        verify(tasks,never()).feedback(any(),any(),anyString()); verify(tasks,never()).run(any());
    }
    @Test void explicitAcceptanceDoesNotClaimAPurchaseAndAConflictNeverClaimsSuccess() {
        var task=reviewable("桌面方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        assertThat(intake.accept(context,"这份比较可以了，先到这里")).contains("认可这份比较成果").doesNotContain("已购买","完成布置");
        var input=ArgumentCaptor.forClass(FeedbackInput.class);
        verify(tasks).feedback(eq(task.lastRun().id()),input.capture(),eq(context.turnId().toString()));
        assertThat(input.getValue().kind()).isEqualTo(FeedbackKind.ACCEPT_RESULT);
        assertThat(input.getValue().runMode()).isNull();
        when(tasks.feedback(any(),any(),anyString())).thenThrow(new DelegationException("REVISION_CONFLICT"));
        assertThat(intake.accept(context,"这份比较可以了")).contains("未保存","重新查看").doesNotContain("已记录");
    }
    @Test void explicitTaskCancelIsDistinctFromABareFrontendCancel() {
        var task=reviewable("桌面方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        assertThat(intake.supports(context,"取消")).isFalse();
        assertThat(intake.supports(context,"取消这个比较")).isTrue();
        assertThat(intake.accept(context,"取消这个比较")).contains("已取消","已有成果");
        var input=ArgumentCaptor.forClass(FeedbackInput.class);
        verify(tasks).feedback(eq(task.lastRun().id()),input.capture(),anyString());
        assertThat(input.getValue().kind()).isEqualTo(FeedbackKind.CANCEL);
    }
    @Test void identicalTitlesCanBeSelectedByDisplayedNumber() {
        var first=reviewable("桌面方案比较"); var second=reviewable("桌面方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(first,second));
        assertThat(intake.accept(context,"昨天那个到哪了")).contains("1：桌面方案比较","2：桌面方案比较");
        when(tasks.projectConversationSummary(second.id())).thenReturn("第二个任务的原成果。");
        assertThat(intake.accept(context,"2")).isEqualTo("第二个任务的原成果。");
        verify(tasks,never()).projectConversationSummary(first.id());
    }
    @Test void aSingleUnrelatedProjectCannotAnswerAnExplicitlyDifferentSubject() {
        var task=reviewable("桌面布置方案比较");
        when(tasks.findProjectTasks(context.deviceId(),context.roleId())).thenReturn(List.of(task));
        assertThat(intake.accept(context,"昨天灯光布置那个比较到哪了")).contains("没有可对应");
        verify(tasks,never()).projectConversationSummary(any());
    }
    @Test void aSavedTaskWithRejectedWorkerCapacityNeverClaimsItStarted() {
        var task=reviewable("桌面方案比较");
        when(task.lastRun().status()).thenReturn(RunStatus.FAILED);
        when(task.lastRun().errorCode()).thenReturn("DELEGATION_CAPACITY");
        when(tasks.createProject(any(),anyString())).thenReturn(task);
        assertThat(intake.accept(context,"现在帮我比较两个公开页面")).contains("任务已保存","当前有任务在执行","尚未完成")
                .doesNotContain("已开始","已比较","已接下");
    }
    private TaskSnapshot reviewable(String title) {
        var task=mock(TaskSnapshot.class); var run=mock(RunSnapshot.class);
        when(task.id()).thenReturn(UUID.randomUUID()); when(task.title()).thenReturn(title);
        when(task.revision()).thenReturn(7L); when(task.lastRun()).thenReturn(run);
        when(run.id()).thenReturn(UUID.randomUUID());
        when(run.review()).thenReturn(new ReviewSnapshot(ReviewState.UNREVIEWED,3,null,null));
        return task;
    }
}
