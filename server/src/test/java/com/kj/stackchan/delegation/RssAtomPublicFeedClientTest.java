package com.kj.stackchan.delegation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RssAtomPublicFeedClientTest {
    static final Instant FROM = Instant.parse("2026-10-07T00:00:00Z"), TO = Instant.parse("2026-10-08T12:00:00Z");
    static final URI SOURCE = URI.create("https://example.com/feed.xml");
    private final RssAtomPublicFeedClient client = new RssAtomPublicFeedClient(new PublicHttpsUrlPolicy(),
            (uri, method, body, timeout, max) -> new PublicHttpsTransport.Response(200, new byte[0]));

    @Test
    void parsesRssDatesAndPlainTextAndDropsOldFutureUndatedPrivateDuplicateAndExcludedItems() {
        String xml = "<rss><channel><title>Example source</title>"
                + item("Good", "https://example.com/1", "Thu, 08 Oct 2026 08:00:00 +0000", "&lt;b&gt;New&lt;/b&gt; item")
                + item("Duplicate", "https://example.com/1", "Thu, 08 Oct 2026 08:00:00 +0000", "duplicate")
                + item("Excluded", "https://example.com/2", "Thu, 08 Oct 2026 08:00:00 +0000", "hidden")
                + item("Old", "https://example.com/3", "Tue, 06 Oct 2026 08:00:00 +0000", "old")
                + item("Future", "https://example.com/4", "Fri, 09 Oct 2026 08:00:00 +0000", "future")
                + item("Undated", "https://example.com/5", "", "missing")
                + item("Private", "https://127.0.0.1/", "Thu, 08 Oct 2026 08:00:00 +0000", "private")
                + "</channel></rss>";
        var entries = client.parse(bytes(xml), SOURCE, FROM, TO, Set.of("https://example.com/2"));
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().title()).isEqualTo("Good");
        assertThat(entries.getFirst().excerpt()).isEqualTo("New item");
        assertThat(entries.getFirst().sourceName()).isEqualTo("Example source");
        assertThat(entries.getFirst().publishedAt()).isEqualTo(Instant.parse("2026-10-08T08:00:00Z"));
    }

    @Test
    void parsesAtomRelativeAlternateLinkAndPublishedDate() {
        String xml = """
                <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom source</title>
                <entry><title>New result</title><link rel="self" href="https://example.com/self"/>
                <link rel="alternate" href="/article"/><published>2026-10-08T08:30:00+08:00</published>
                <summary type="html">&lt;p&gt;Useful &amp;amp; current&lt;/p&gt;</summary></entry></feed>
                """;
        var entries = client.parse(bytes(xml), SOURCE, FROM, TO, Set.of());
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().url()).isEqualTo("https://example.com/article");
        assertThat(entries.getFirst().excerpt()).isEqualTo("Useful & current");
    }

    @Test
    void rejectsDtdXxeOversizeAndUnrecognizedXmlWithFixedErrors() {
        for (byte[] xml : new byte[][]{bytes("<!DOCTYPE rss [<!ENTITY leak SYSTEM 'file:///etc/passwd'>]><rss>&leak;</rss>"),
                new byte[RssAtomPublicFeedClient.MAX_BODY_BYTES + 1], bytes("<html><body>not a feed</body></html>")}) {
            assertThatThrownBy(() -> client.parse(xml, SOURCE, FROM, TO, Set.of()))
                    .isInstanceOf(ResearchException.class).hasMessage("SOURCES_UNAVAILABLE").hasNoCause();
        }
    }

    @Test
    void rejectsRedirectAndOversizeTransportResponseWithoutFollowup() {
        AtomicInteger calls = new AtomicInteger();
        var redirect = new RssAtomPublicFeedClient(new PublicHttpsUrlPolicy(), (uri, method, body, timeout, max) -> {
            calls.incrementAndGet();
            return new PublicHttpsTransport.Response(302, bytes("https://localhost/private"));
        });
        assertThatThrownBy(() -> redirect.fetch(SOURCE.toString(), FROM, TO, Set.of())).hasMessage("SOURCES_UNAVAILABLE");
        assertThat(calls).hasValue(1);
        var oversized = new RssAtomPublicFeedClient(new PublicHttpsUrlPolicy(), (uri, method, body, timeout, max) ->
                new PublicHttpsTransport.Response(200, new byte[max + 1]));
        assertThatThrownBy(() -> oversized.fetch(SOURCE.toString(), FROM, TO, Set.of())).hasMessage("SOURCES_UNAVAILABLE");
    }

    private static String item(String title, String url, String date, String description) {
        return "<item><title>" + title + "</title><link>" + url + "</link><pubDate>" + date
                + "</pubDate><description>" + description + "</description></item>";
    }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
