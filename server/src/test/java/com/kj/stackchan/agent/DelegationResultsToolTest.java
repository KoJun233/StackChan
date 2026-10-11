package com.kj.stackchan.agent;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.delegation.DelegationService;
import com.kj.stackchan.delegation.ResearchBriefGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelegationResultsToolTest {
    @Test void genericOutputsAndActualStepsRemainVisibleWithoutResearchItems() throws Exception {
        UUID device=UUID.randomUUID(),role=UUID.randomUUID(); var service=mock(DelegationService.class);
        var run=new DelegationService.RunSnapshot(UUID.randomUUID(),UUID.randomUUID(),"每日回顾",LocalDate.of(2026,10,10),
                DelegationService.RunStatus.SUCCEEDED,List.of(),List.of(),null,DelegationService.DeliveryStatus.NONE,0,
                Instant.EPOCH,Instant.EPOCH,Instant.EPOCH,"local:2026-10-10","今天已完成两个待办。",
                List.of(new com.kj.stackchan.delegation.DelegationTaskExecutor.ExecutionStep("current_personal_tasks","SUCCEEDED","工具已返回")),List.of());
        when(service.latestForDeviceRole(device,role)).thenReturn(List.of(run));
        var result=new ObjectMapper().readTree(new DelegationResultsTool(device,role,service,new ObjectMapper()).currentResults()).path("results").get(0);
        assertThat(result.path("output").asText()).isEqualTo(run.output());
        assertThat(result.path("steps").get(0).path("toolName").asText()).isEqualTo("current_personal_tasks");
        assertThat(result.path("items").isEmpty()).isTrue();
    }
    @Test
    void readsOnlySavedResultsInTheServerAssignedScopeAndKeepsSourceDates() throws Exception {
        UUID device = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var service = mock(DelegationService.class);
        var item = new ResearchBriefGenerator.BriefItem("新模型", "官方发布新模型", "可以评估实际用途", "官方",
                "https://example.com/news/model", Instant.parse("2026-10-08T01:00:00Z"));
        var run = new DelegationService.RunSnapshot(UUID.randomUUID(), UUID.randomUUID(), "AI 日报",
                LocalDate.of(2026, 10, 8), DelegationService.RunStatus.SUCCEEDED,
                java.util.Collections.nCopies(4, item), List.of(), null, DelegationService.DeliveryStatus.SENT,
                1, Instant.EPOCH, Instant.parse("2026-10-08T01:30:00Z"));
        when(service.latestForDeviceRole(device, role)).thenReturn(List.of(run));

        var json = new ObjectMapper().readTree(new DelegationResultsTool(device, role, service, new ObjectMapper()).currentResults());

        verify(service).latestForDeviceRole(device, role);
        var result = json.path("results").get(0);
        assertThat(result.path("localDate").asText()).isEqualTo("2026-10-08");
        assertThat(result.path("deliveryStatus").asText()).isEqualTo("SENT");
        assertThat(result.path("items").size()).isEqualTo(3);
        assertThat(result.path("hasMoreItems").asBoolean()).isTrue();
        assertThat(result.path("items").get(0).path("url").asText()).isEqualTo(item.url());
        assertThat(result.path("items").get(0).path("publishedAt").asText()).isEqualTo(item.publishedAt().toString());
        assertThat(json.toString()).doesNotContain("deviceKey", "instruction", "sourceUrls");
    }

    @Test
    void longCitationsStayCompleteWithinTheToolResultBudget() throws Exception {
        UUID device = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var service = mock(DelegationService.class);
        String url = "https://example.com/" + "a".repeat(2000);
        var item = new ResearchBriefGenerator.BriefItem("题".repeat(100), "文".repeat(100),
                "影响".repeat(80), "来源".repeat(60), url, Instant.EPOCH);
        var run = new DelegationService.RunSnapshot(UUID.randomUUID(), UUID.randomUUID(), "日报",
                LocalDate.of(2026, 10, 8), DelegationService.RunStatus.SUCCEEDED,
                java.util.Collections.nCopies(5, item), List.of(), null, DelegationService.DeliveryStatus.SENT,
                1, Instant.EPOCH, Instant.EPOCH);
        when(service.latestForDeviceRole(device, role)).thenReturn(java.util.Collections.nCopies(3, run));
        String output = new DelegationResultsTool(device, role, service, new ObjectMapper()).currentResults();
        assertThat(output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(8192);
        var result = new ObjectMapper().readTree(output).path("results").get(0);
        assertThat(result.path("items").get(0).path("url").asText()).isEqualTo(url);
        assertThat(result.path("hasMoreItems").asBoolean()).isTrue();
    }

    @Test
    void noSavedResultsDoesNotInventADigest() throws Exception {
        UUID device = UUID.randomUUID();
        UUID role = UUID.randomUUID();
        var service = mock(DelegationService.class);
        when(service.latestForDeviceRole(device, role)).thenReturn(List.of());
        var json = new ObjectMapper().readTree(new DelegationResultsTool(device, role, service, new ObjectMapper()).currentResults());
        assertThat(json.path("results").isEmpty()).isTrue();
    }
}
