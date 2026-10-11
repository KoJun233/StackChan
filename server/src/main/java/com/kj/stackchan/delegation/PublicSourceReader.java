package com.kj.stackchan.delegation;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Reads one public resource chosen by the agent; it never follows redirects or page instructions. */
@Component
public class PublicSourceReader {
    private final PublicHttpsUrlPolicy policy;
    private final PublicHttpsTransport transport;
    private final RssAtomPublicFeedClient feeds;
    private final Clock clock;

    @Autowired
    public PublicSourceReader(PublicHttpsUrlPolicy policy, PinnedHttpsTransport transport,
                              RssAtomPublicFeedClient feeds, Clock clock) {
        this(policy, (PublicHttpsTransport) transport, feeds, clock);
    }
    PublicSourceReader(PublicHttpsUrlPolicy policy, PublicHttpsTransport transport,
                       RssAtomPublicFeedClient feeds, Clock clock) {
        this.policy = policy; this.transport = transport; this.feeds = feeds; this.clock = clock;
    }
    public Source read(String url) {
        try {
            URI uri = policy.validateSyntax(url);
            var response = transport.exchange(uri, "GET", new byte[0], Duration.ofSeconds(6), 1024 * 1024);
            if (response.status() < 200 || response.status() >= 300) throw new IllegalArgumentException();
            String body = new String(response.body(), StandardCharsets.UTF_8);
            String head = body.substring(0, Math.min(body.length(), 1024));
            if (head.matches("(?is).*<(?:rss|feed|rdf:RDF)(?:\\s|>).*")) {
                var entries = feeds.parse(response.body(), uri, clock.instant().minus(Duration.ofDays(31)),
                        clock.instant(), Set.of()).stream().limit(20).toList();
                List<Reference> references = entries.stream().map(entry -> new Reference(entry.title(),
                        entry.url(), entry.publishedAt() + " " + entry.excerpt())).toList();
                return new Source(uri.toString(), uri.getHost(), "", List.of(), references);
            }
            return html(body, uri);
        } catch (Exception ignored) { throw new ResearchException("PUBLIC_SOURCE_UNAVAILABLE"); }
    }
    private Source html(String body, URI uri) throws Exception {
        StringBuilder title = new StringBuilder(), text = new StringBuilder();
        var links = new LinkedHashMap<String, Reference>();
        new ParserDelegator().parse(new StringReader(body), new HTMLEditorKit.ParserCallback() {
            int hidden;
            boolean inTitle;
            String link;
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden++;
                if (tag == HTML.Tag.TITLE) inTitle = true;
                if (tag == HTML.Tag.A && links.size() < 30) {
                    Object href = attrs.getAttribute(HTML.Attribute.HREF);
                    try { link = href == null ? null : policy.validateSyntax(uri.resolve(href.toString()).toString()).toString(); }
                    catch (RuntimeException ignored) { link = null; }
                }
            }
            @Override public void handleEndTag(HTML.Tag tag, int position) {
                if (tag == HTML.Tag.SCRIPT || tag == HTML.Tag.STYLE) hidden = Math.max(0, hidden - 1);
                if (tag == HTML.Tag.TITLE) inTitle = false;
                if (tag == HTML.Tag.A) link = null;
            }
            @Override public void handleText(char[] chars, int position) {
                if (hidden > 0) return;
                String value = new String(chars).strip();
                if (inTitle && title.length() < 200) title.append(value);
                if (text.length() < 14000) text.append(value, 0, Math.min(value.length(), 14000 - text.length())).append('\n');
                if (link != null && !value.isBlank()) links.putIfAbsent(link, new Reference(plain(value, 200), link, ""));
            }
        }, true);
        String heading = title.isEmpty() ? uri.getHost() : plain(title.toString(), 200);
        List<Reference> evidence = List.of(new Reference(heading, uri.toString(), plain(text.toString(), 1200)));
        return new Source(uri.toString(), heading, plain(text.toString(), 14000), List.copyOf(links.values()), evidence);
    }
    private static String plain(String value, int max) {
        return value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip().substring(0, Math.min(max,
                value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").strip().length()));
    }
    public record Reference(String title, String url, String detail) {}
    public record Source(String url, String title, String text, List<Reference> links, List<Reference> evidence) {}
}
