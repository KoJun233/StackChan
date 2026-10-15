package com.kj.stackchan.delegation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.agent.AgentOrchestrator;
import com.kj.stackchan.agent.AgentToolPolicyInterceptor.SourceRead;
import com.kj.stackchan.agent.AgentToolPolicyInterceptor.ToolEvidence;
import com.kj.stackchan.agent.AgentToolPolicyInterceptor.ToolStep;
import org.junit.jupiter.api.Test;
import static com.kj.stackchan.delegation.DelegationTaskExecutor.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Entirely synthetic pages: these are software checks, never real project evidence. */
class ProjectArtifactRendererTest {
    private static final String A="https://example.com/fake-a", B="https://example.com/fake-b";
    private static final Instant READ_AT=Instant.parse("2026-10-15T01:00:00Z");
    private final ObjectMapper mapper=new ObjectMapper();
    private final AgentDelegationTaskExecutor executor=new AgentDelegationTaskExecutor(mock(AgentOrchestrator.class),mapper);
    private final List<ToolEvidence> evidence=List.of(
            new ToolEvidence("E1","合成方案A",A,"合成方案A。线缆两条。可以调整高度。",A,READ_AT),
            new ToolEvidence("E2","合成方案B",B,"合成方案B。线缆一条。固定高度。",B,READ_AT));

    @Test void rendersFiveSectionsWithActualSourcesAndUnknownDimensions() throws Exception {
        var result=execute(artifact(),evidence,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        assertThat(result.output()).contains("## 任务理解","## 方案比较","## 建议与理由","## 缺失信息与限制","## 下一步供你决定",
                "占地：来源未提供","读取时间 "+READ_AT,"线缆一条","等待你审阅");
        assertThat(result.evidence()).extracting(ExecutionEvidence::inputUrl).containsExactly(A,B);
    }
    @Test void oneUnreadSourceBlocksWholeComparisonAndPreservesKnownFacts() throws Exception {
        var result=execute(artifact(),evidence,reads(A),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.output()).contains("合成方案A","尚未成功读取",B).doesNotContain("线缆一条");
    }
    @Test void anExplicitFailedReadCannotBeHiddenByModelSuccess() throws Exception {
        var reads=new java.util.ArrayList<>(reads(A));
        reads.add(new SourceRead(B,false,List.of(),READ_AT));
        var result=execute(artifact(),evidence,reads,request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
    }
    @Test void legacyEvidenceWithoutAnActualReadMappingCannotEstablishProjectSuccess() throws Exception {
        var legacy=evidence.stream().map(item->new ToolEvidence(item.id(),item.title(),item.url(),item.detail())).toList();
        var result=execute(artifact(),legacy,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.evidence()).isEmpty();
    }
    @Test void anUnsupportedNumericClaimIsNotRenderedAsFact() throws Exception {
        var root=mapper.readTree(artifact());
        var dimension=(com.fasterxml.jackson.databind.node.ObjectNode)root.path("artifact").path("options").get(0).path("dimensions").get(0);
        dimension.put("value","只要12345元"); dimension.put("quote","只要12345元");
        var result=execute(mapper.writeValueAsString(root),evidence,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.output()).contains("没有可核对的来源原文").doesNotContain("12345");
    }
    @Test void aSupportedQuoteIsRenderedInsteadOfAnInventedParaphrase() throws Exception {
        var root=mapper.readTree(artifact());
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("artifact").path("options").get(0).path("dimensions").get(0)).put("value","只要12345元");
        var result=execute(mapper.writeValueAsString(root),evidence,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        assertThat(result.output()).contains("线缆两条").doesNotContain("12345");
    }
    @Test void duplicatePagesForOneOptionCannotBecomeTwoOptions() throws Exception {
        var root=mapper.readTree(artifact());
        ((com.fasterxml.jackson.databind.node.ObjectNode)root.path("artifact").path("options").get(1)).put("identityQuote","合成方案A");
        var duplicate=List.of(evidence.getFirst(),new ToolEvidence("E2","合成方案A",B,evidence.getFirst().detail(),B,READ_AT));
        var result=execute(mapper.writeValueAsString(root),duplicate,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.output()).contains("重复方案","只有一个可确认的有效方案");
    }
    @Test void aSingleOptionComparisonAsksForSecondRatherThanPretendingSuccess() throws Exception {
        var root=mapper.readTree(artifact());
        ((com.fasterxml.jackson.databind.node.ArrayNode)root.path("artifact").path("options")).remove(1);
        var result=execute(mapper.writeValueAsString(root),evidence,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.output()).contains("补充第二个不同方案");
    }
    @Test void modelCannotInventAnAdditionalUrl() throws Exception {
        String answer=artifact().replace("少线缆优先时建议方案B","详见 https://unread.example.com/offer");
        var result=execute(answer,evidence,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.output()).doesNotContain("unread.example.com");
    }
    @Test void modelCannotClaimUnperformedPurchaseSelectionOrUserAcceptance() throws Exception {
        for(String claim:List.of("已为你选定方案B并已经下单","用户已经认可最新结果","手机已阅读本次通知")) {
            var result=execute(artifact().replace("少线缆优先时建议方案B",claim),evidence,reads(A,B),request(null));
            assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
            assertThat(result.output()).doesNotContain(claim);
        }
    }
    @Test void aMalformedModelArtifactStillDeliversAvailableFactsAndFiveSections() throws Exception {
        var result=execute("not-json",evidence,reads(A,B),request(null));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.output()).contains("## 任务理解","## 方案比较","## 建议与理由","## 缺失信息与限制","## 下一步供你决定","合成方案A");
    }
    @Test void revisionExplainsItsActualPriorArtifactAndUserFeedback() throws Exception {
        UUID prior=UUID.randomUUID();
        var result=execute(artifact(),evidence,reads(A,B),request(prior));
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        assertThat(result.output()).contains(prior.toString(),"我更在意线缆少，预算信息先不用猜");
    }
    @Test void pureTextGenerationUsesSameFiveSectionsWithoutInventingExternalFacts() throws Exception {
        var context=new ProjectExecutionContext(1,List.of(),"TEXT_ONLY",null,null,null);
        var request=new TaskExecutionRequest(UUID.randomUUID(),UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),"合成提纲",
                "写一份合成提纲","生成提纲",READ_AT,false,List.of(),List.of(),new LegacyHints(List.of(),5),op->op.get(),context);
        String answer=mapper.writeValueAsString(Map.of("status","SUCCEEDED","artifact",Map.of("understanding","生成提纲",
                "options",List.of(Map.of("name","提纲","identityQuote","提纲","evidenceIds",List.of(),"dimensions",
                        List.of(Map.of("name","内容","value","一、问题；二、计划","evidenceIds",List.of(),"quote","")))),
                "recommendation","按需要调整提纲","limitations",List.of("这是生成内容"),"nextChoices",List.of("审阅提纲"))));
        var result=execute(answer,List.of(),List.of(),request);
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        assertThat(result.output()).contains("生成内容","仅本地文字生成");
        assertThat(result.evidence()).isEmpty();
    }
    @Test void executorPassesSavedSourcesAndRevisionHistoryIntoTheExistingAgent() throws Exception {
        var agent=mock(AgentOrchestrator.class);
        var executor=new AgentDelegationTaskExecutor(agent,mapper);
        var response=new AgentOrchestrator.TaskAgentResponse(artifact(),List.of(new ToolStep("read_public_source","SUCCEEDED","合成读取")),evidence,reads(A,B));
        when(agent.executeTask(any(),anyList(),any(),eq(List.of(A,B)))).thenReturn(response);
        UUID previous=UUID.randomUUID(); var request=request(previous);
        assertThat(executor.execute(request).status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        var capture=org.mockito.ArgumentCaptor.forClass(AgentOrchestrator.AgentRequest.class);
        verify(agent).executeTask(capture.capture(),eq(List.of("read_public_source")),any(),eq(List.of(A,B)));
        assertThat(capture.getValue().userMessage()).contains(previous.toString(),"原成果：优先整理，方案A","我更在意线缆少，预算信息先不用猜");
        assertThat(capture.getValue().context().deviceId()).isEqualTo(request.deviceId());
        assertThat(capture.getValue().context().roleId()).isEqualTo(request.roleId());
        assertThat(capture.getValue().context().delegationRunId()).isEqualTo(request.runId());
        assertThat(capture.getValue().systemPrompt()).contains("只有status和artifact").doesNotContain("字段仅为");
    }
    @Test void aMissingProjectCapabilityReturnsAReadableBlockedArtifactWithoutCallingTheModel() {
        var agent=mock(AgentOrchestrator.class);
        var executor=new AgentDelegationTaskExecutor(agent,mapper);
        var base=request(null);
        var request=new TaskExecutionRequest(base.taskId(),base.runId(),1,base.deviceId(),base.roleId(),base.title(),base.description(),base.understanding(),
                base.scheduledAt(),true,List.of(),List.of(),null,base.guard(),base.projectContext());
        var result=executor.execute(request);
        assertThat(result.status()).isEqualTo(DelegationService.RunStatus.BLOCKED);
        assertThat(result.output()).contains("## 方案比较","## 下一步供你决定");
        verifyNoInteractions(agent);
    }
    @Test void legacySourceHintsRemainPreferencesAndUseTheOriginalAgentContract() {
        var agent=mock(AgentOrchestrator.class); var executor=new AgentDelegationTaskExecutor(agent,mapper);
        when(agent.executeTask(any(),anyList(),any())).thenReturn(new AgentOrchestrator.TaskAgentResponse(
                "{\"status\":\"SUCCEEDED\",\"output\":\"旧任务实际读取后的结果\",\"evidenceIds\":[\"E1\"]}",
                List.of(new ToolStep("read_public_source","SUCCEEDED","旧任务合成读取")),evidence));
        var base=request(null);
        var legacy=new TaskExecutionRequest(base.taskId(),base.runId(),1,base.deviceId(),base.roleId(),base.title(),base.description(),base.understanding(),
                base.scheduledAt(),true,List.of("read_public_source"),List.of("read_public_source"),new LegacyHints(List.of(A),5),base.guard());
        assertThat(executor.execute(legacy).status()).isEqualTo(DelegationService.RunStatus.SUCCEEDED);
        verify(agent).executeTask(any(),eq(List.of("read_public_source")),any());
        verify(agent,never()).executeTask(any(),anyList(),any(),anyList());
    }

    private TaskExecutionResult execute(String json,List<ToolEvidence> evidence,List<SourceRead> reads,TaskExecutionRequest request) throws Exception {
        return executor.projectResult(new AgentOrchestrator.TaskAgentResponse(json,
                List.of(new ToolStep("read_public_source","SUCCEEDED","合成读取")),evidence,reads),request);
    }
    private TaskExecutionRequest request(UUID prior) {
        return new TaskExecutionRequest(UUID.randomUUID(),UUID.randomUUID(),1,UUID.randomUUID(),UUID.randomUUID(),"合成比较",
                "比较两个方案；空间有限，容易整理，线缆少","只读比较",READ_AT,true,List.of("read_public_source"),List.of("read_public_source"),
                new LegacyHints(List.of(A,B),5),op->op.get(),new ProjectExecutionContext(prior==null?1:2,List.of(A,B),"GIVEN_URLS",
                prior,prior==null?null:"我更在意线缆少，预算信息先不用猜",prior==null?null:"原成果：优先整理，方案A"));
    }
    private List<SourceRead> reads(String... urls) {
        return java.util.Arrays.stream(urls).map(url->new SourceRead(url,true,List.of(A.equals(url)?"E1":"E2"),READ_AT)).toList();
    }
    private String artifact() throws Exception {
        return mapper.writeValueAsString(Map.of("status","SUCCEEDED","artifact",Map.of(
                "understanding","只比较给定公开材料，按少线缆偏好提供建议",
                "options",List.of(option("合成方案A","E1","线缆两条"),option("合成方案B","E2","线缆一条")),
                "recommendation","少线缆优先时建议方案B","limitations",List.of("来源未提供尺寸与预算"),"nextChoices",List.of("补充桌面尺寸"))));
    }
    private Map<String,Object> option(String name,String id,String quote) {
        return Map.of("name",name,"identityQuote",name,"evidenceIds",List.of(id),"dimensions",List.of(
                Map.of("name","线缆","value",quote,"evidenceIds",List.of(id),"quote",quote),
                Map.of("name","占地","value","来源未提供","evidenceIds",List.of(),"quote","")));
    }
}
