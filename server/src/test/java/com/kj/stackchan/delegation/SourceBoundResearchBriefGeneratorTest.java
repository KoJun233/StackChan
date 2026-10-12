package com.kj.stackchan.delegation;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.delegation.ResearchBriefGenerator.ResearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SourceBoundResearchBriefGeneratorTest {
    private final RssAtomPublicFeedClient feeds = mock(RssAtomPublicFeedClient.class);
    private final ResearchRequest request = new ResearchRequest("生态保护日报", "整理本周生态保护新进展",
            List.of("https://example.com/feed"), RssAtomPublicFeedClientTest.FROM, RssAtomPublicFeedClientTest.TO,
            5, List.of());

    @Test
    void selectsCandidateIdAndMapsOnlyRealMetadataForAnySubject() {
        stubCandidate();
        var generator = generator((system, user, timeout) -> {
            assertThat(user).contains("生态保护", "C1", "Original title").doesNotContain("https://example.com/original");
            return "{\"items\":[{\"id\":\"C1\",\"summary\":\"新的保护措施\",\"impact\":\"可供本地计划参考\"}]}";
        });
        var result = generator.generate(request);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().url()).isEqualTo("https://example.com/original");
        assertThat(result.items().getFirst().title()).isEqualTo("Original title");
        assertThat(result.items().getFirst().sourceName()).isEqualTo("Actual source");
        assertThat(result.warnings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"items\":[{\"id\":\"UNKNOWN\",\"summary\":\"s\",\"impact\":\"i\"}]}",
            "{\"items\":[{\"id\":\"<b>C1</b>\",\"summary\":\"s\",\"impact\":\"i\"}]}",
            "{\"items\":[{\"id\":\"C1\",\"summary\":\"s\",\"impact\":\"i\",\"url\":\"https://invented.com/\"}]}",
            "{\"items\":[{\"id\":\"C1\",\"summary\":\"s\",\"impact\":\"i\"},{\"id\":\"C1\",\"summary\":\"s\",\"impact\":\"i\"}]}",
            "{\"items\":[],\"items\":[]}", "{\"items\":[]} {\"items\":[]}", "free-form answer",
            "{\"items\":[{\"id\":\"C1\",\"summary\":\"https://invented.com/\",\"impact\":\"i\"}]}"
    })
    void refusesUnknownDuplicateOrUnboundedModelReferences(String answer) {
        stubCandidate();
        assertThatThrownBy(() -> generator((system, user, timeout) -> answer).generate(request))
                .isInstanceOf(ResearchException.class).hasMessage("MODEL_OUTPUT_INVALID").hasNoCause();
    }

    @Test
    void reportsNoUpdatesWithoutCallingModelAndAllSourceFailureWithoutFallback() throws Exception {
        var model = mock(SourceBoundResearchBriefGenerator.ModelCompletion.class);
        when(feeds.fetch(anyString(), any(), any(), any())).thenReturn(List.of());
        assertThat(generator(model).generate(request).items()).isEmpty();
        verifyNoInteractions(model);
        when(feeds.fetch(anyString(), any(), any(), any())).thenThrow(new RuntimeException("secret provider body"));
        assertThatThrownBy(() -> generator(model).generate(request)).hasMessage("SOURCES_UNAVAILABLE").hasNoCause();
        verifyNoInteractions(model);
    }

    @Test
    void retainsIndexedSafeWarningsWhenOneSourceFails() {
        var two = new ResearchRequest(request.title(), request.instruction(),
                List.of("https://example.com/failed", "https://example.com/empty"), request.from(), request.to(), 5, List.of());
        when(feeds.fetch("https://example.com/failed", two.from(), two.to(), Set.of()))
                .thenThrow(new RuntimeException("private-data"));
        when(feeds.fetch("https://example.com/empty", two.from(), two.to(), Set.of())).thenReturn(List.of());
        var result = generator((system, user, timeout) -> { throw new AssertionError(); }).generate(two);
        assertThat(result.warnings()).containsExactly("SOURCE_1_UNAVAILABLE");
    }

    @Test
    void boundsTotalTimeModelOutputAndProviderErrors() {
        stubCandidate();
        var timeout = new SourceBoundResearchBriefGenerator(feeds, new ObjectMapper(), (system, user, budget) -> {
            Thread.sleep(1000);
            return "{\"items\":[]}";
        }, Duration.ofMillis(50));
        assertThatThrownBy(() -> timeout.generate(request)).hasMessage("RESEARCH_TIMEOUT").hasNoCause();
        assertThatThrownBy(() -> generator((system, user, budget) -> "x".repeat(16001)).generate(request))
                .hasMessage("MODEL_OUTPUT_INVALID");
        assertThatThrownBy(() -> generator((system, user, budget) -> {
            throw new IllegalStateException("sk-secret provider payload");
        }).generate(request)).hasMessage("MODEL_UNAVAILABLE").hasNoCause();
    }

    private void stubCandidate() {
        when(feeds.fetch(anyString(), any(), any(), any())).thenReturn(List.of(
                new RssAtomPublicFeedClient.FeedEntry("Original title", "Source facts", "Actual source",
                        "https://example.com/original", RssAtomPublicFeedClientTest.TO.minusSeconds(3600))));
    }

    private SourceBoundResearchBriefGenerator generator(SourceBoundResearchBriefGenerator.ModelCompletion model) {
        return new SourceBoundResearchBriefGenerator(feeds, new ObjectMapper(), model, Duration.ofSeconds(2));
    }
}
