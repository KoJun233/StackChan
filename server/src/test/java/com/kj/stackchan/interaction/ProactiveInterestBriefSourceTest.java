package com.kj.stackchan.interaction;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProactiveInterestBriefSourceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    void publishesOnlyACompletedRefresh() {
        HackerNewsBriefClient client = mock(HackerNewsBriefClient.class);
        InterestBrief brief = new InterestBrief(
                "Hacker News", "New AI research", "https://example.com/paper", NOW.minusSeconds(60), NOW
        );
        when(client.fetch(NOW)).thenReturn(List.of(brief));
        ProactiveInterestBriefSource source = new ProactiveInterestBriefSource(
                client, Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(source.current()).isEmpty();
        source.refresh();

        assertThat(source.current()).containsExactly(brief);
    }
}
