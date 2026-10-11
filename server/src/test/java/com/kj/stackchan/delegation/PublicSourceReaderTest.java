package com.kj.stackchan.delegation;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PublicSourceReaderTest {
    private PublicSourceReader reader(String body,int status) {
        var policy=new PublicHttpsUrlPolicy();
        PublicHttpsTransport transport=(uri,method,bytes,timeout,max)->new PublicHttpsTransport.Response(status,body.getBytes(StandardCharsets.UTF_8));
        return new PublicSourceReader(policy,transport,new RssAtomPublicFeedClient(policy,transport),
                Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"),ZoneOffset.UTC));
    }
    @Test void readsArbitraryPublicPagesAsTextAndDoesNotRunScripts() {
        var source=reader("<html><head><title>项目进展</title><script>secret-script</script></head><body>新进展<a href='/next'>查看原文</a><a href='http://127.0.0.1/'>内部</a></body></html>",200)
                .read("https://example.com/news");
        assertThat(source.title()).isEqualTo("项目进展");
        assertThat(source.text()).contains("新进展").doesNotContain("secret-script");
        assertThat(source.links()).extracting(PublicSourceReader.Reference::url).containsExactly("https://example.com/next");
        assertThat(source.evidence().getFirst().url()).isEqualTo("https://example.com/news");
    }
    @Test void feedsAreOneOptionalResourceFormat() {
        var source=reader("<rss><channel><title>更新</title><item><title>版本发布</title><link>https://example.com/release</link><pubDate>Fri, 09 Oct 2026 08:00:00 GMT</pubDate><description>版本内容</description></item></channel></rss>",200)
                .read("https://example.com/feed.xml");
        assertThat(source.evidence()).hasSize(1);
        assertThat(source.evidence().getFirst().url()).isEqualTo("https://example.com/release");
    }
    @Test void privateUrlsAndRedirectsDoNotYieldEvidence() {
        assertThatThrownBy(()->reader("private",200).read("https://127.0.0.1/secret")).isInstanceOf(ResearchException.class);
        assertThatThrownBy(()->reader("moved",302).read("https://example.com/redirect")).isInstanceOf(ResearchException.class);
    }
}
