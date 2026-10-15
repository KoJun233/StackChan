package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectSourcePolicyTest {
    private static final Instant READ_AT=Instant.parse("2026-10-15T01:00:00Z");
    private static final String SOURCE="https://example.com/allowed";

    @Test void checksTheCanonicalSavedScopeBeforeTheActualHandlerAndMapsReadTime() {
        var policy=policy(List.of(SOURCE));
        var result=policy.interceptToolCall(request("HTTPS://EXAMPLE.COM:443/a/../allowed"),ignored->success(SOURCE));
        assertThat(result.isError()).isFalse();
        assertThat(policy.sourceReads()).hasSize(1);
        assertThat(policy.sourceReads().getFirst().inputUrl()).isEqualTo(SOURCE);
        assertThat(policy.sourceReads().getFirst().readAt()).isEqualTo(READ_AT);
        assertThat(policy.evidence().getFirst().inputUrl()).isEqualTo(SOURCE);
        assertThat(policy.evidence().getFirst().readAt()).isEqualTo(READ_AT);
    }
    @Test void aLinkFromThePageCannotExpandTheSavedScope() {
        var policy=policy(List.of(SOURCE));
        var handler=mock(ToolCallHandler.class);
        assertThat(policy.interceptToolCall(request("https://other.example.com/linked"),handler).isError()).isTrue();
        verifyNoInteractions(handler);
        assertThat(policy.evidence()).isEmpty();
        assertThat(policy.sourceReads()).isEmpty();
    }
    @Test void anAllowedHostDoesNotAuthorizeItsOtherPathsOrQuery() {
        var handler=mock(ToolCallHandler.class);
        for(String url:List.of("https://example.com/other","https://example.com/allowed?expand=true"))
            assertThat(policy(List.of(SOURCE)).interceptToolCall(request(url),handler).isError()).isTrue();
        verifyNoInteractions(handler);
    }
    @Test void privateCredentialsAndSecretQueryCannotReachTheTransport() {
        var handler=mock(ToolCallHandler.class);
        for(String url:List.of("https://127.0.0.1/private","https://example.com/allowed?token=private","https://user:password@example.com/allowed"))
            assertThat(policy(List.of(SOURCE)).interceptToolCall(request(url),handler).isError()).isTrue();
        verifyNoInteractions(handler);
    }
    @Test void malformedOrMissingUrlIsRejectedBeforeTheHandler() {
        var handler=mock(ToolCallHandler.class);
        var policy=policy(List.of(SOURCE));
        assertThat(policy.interceptToolCall(raw("{}"),handler).isError()).isTrue();
        assertThat(policy.interceptToolCall(raw("{\"url\":9}"),handler).isError()).isTrue();
        verifyNoInteractions(handler);
    }
    @Test void duplicateUrlFieldsCannotExploitDifferentPolicyAndHandlerJsonInterpretation() {
        var handler=mock(ToolCallHandler.class); var policy=policy(List.of(SOURCE));
        assertThat(policy.interceptToolCall(raw("{\"url\":\"https://other.example.com/private\",\"url\":\""+SOURCE+"\"}"),handler).isError()).isTrue();
        assertThat(policy.interceptToolCall(raw("{\"url\":\""+SOURCE+"\"} {\"url\":\"https://other.example.com/private\"}"),handler).isError()).isTrue();
        verifyNoInteractions(handler);
    }
    @Test void anActualSourceFailureIsRetainedSeparatelyFromOtherSuccessfulReads() {
        var policy=policy(List.of(SOURCE));
        policy.interceptToolCall(request(SOURCE),ignored->ToolCallResponse.error("call",PublicSourceReadTool.ID,"unavailable"));
        assertThat(policy.sourceReads()).hasSize(1);
        assertThat(policy.sourceReads().getFirst().succeeded()).isFalse();
        assertThat(policy.sourceReads().getFirst().evidenceIds()).isEmpty();
        assertThat(policy.evidence()).isEmpty();
    }
    @Test void revisionGuardRunsAgainBeforeTheAuthorizedRead() {
        var policy=policy(List.of(SOURCE)).withExecutionGuard(operation->{throw new IllegalStateException("changed");});
        var handler=mock(ToolCallHandler.class);
        assertThat(policy.interceptToolCall(request(SOURCE),handler).isError()).isTrue();
        verifyNoInteractions(handler);
        assertThat(policy.sourceReads().getFirst().succeeded()).isFalse();
    }
    @Test void feedEntryEvidenceRemainsMappedToTheActuallyReadAuthorizedFeed() {
        var policy=policy(List.of(SOURCE));
        assertThat(policy.interceptToolCall(request(SOURCE),ignored->success("https://example.com/entry-not-fetched")).isError()).isFalse();
        assertThat(policy.evidence().getFirst().url()).isEqualTo("https://example.com/entry-not-fetched");
        assertThat(policy.evidence().getFirst().inputUrl()).isEqualTo(SOURCE);
    }
    @Test void explicitLegacyExplorationWithoutAListKeepsItsExistingPublicReadBehavior() {
        var policy=policy(List.of());
        assertThat(policy.interceptToolCall(request("https://another.example.com/page"),ignored->success("https://another.example.com/page")).isError()).isFalse();
        assertThat(policy.sourceReads().getFirst().inputUrl()).isEqualTo("https://another.example.com/page");
    }
    private AgentToolPolicyInterceptor policy(List<String> urls) {
        var context=new AgentInvocationContext(UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),AgentChannel.DELEGATION,UUID.randomUUID(),1);
        return new AgentToolPolicyInterceptor(context,Map.of(PublicSourceReadTool.ID,new AgentToolPolicyInterceptor.ToolAuditMetadata(
                AgentToolSource.BUILTIN,null,null)),mock(AgentToolAuditService.class),new ObjectMapper(),8192,24576)
                .withPublicSourceScope(urls).withClock(Clock.fixed(READ_AT,ZoneOffset.UTC));
    }
    private ToolCallRequest request(String url) { return raw("{\"url\":\""+url+"\"}"); }
    private ToolCallRequest raw(String arguments) {
        return ToolCallRequest.builder().toolName(PublicSourceReadTool.ID).arguments(arguments).toolCallId("call").context(Map.of()).build();
    }
    private ToolCallResponse success(String url) {
        return ToolCallResponse.of("call",PublicSourceReadTool.ID,"{\"evidence\":[{\"title\":\"合成来源\",\"url\":\""+url+"\",\"detail\":\"合成正文\"}]}");
    }
}
