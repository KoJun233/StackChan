package com.kj.stackchan.delegation;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class RssAtomPublicFeedClient {
    static final int MAX_BODY_BYTES = 1024 * 1024;
    static final int MAX_ENTRIES = 40;
    private final PublicHttpsUrlPolicy policy;
    private final PublicHttpsTransport transport;

    @Autowired
    public RssAtomPublicFeedClient(PublicHttpsUrlPolicy policy, PinnedHttpsTransport transport) {
        this(policy, (PublicHttpsTransport) transport);
    }

    RssAtomPublicFeedClient(PublicHttpsUrlPolicy policy, PublicHttpsTransport transport) {
        this.policy = policy;
        this.transport = transport;
    }

    List<FeedEntry> fetch(String url, Instant from, Instant to, Set<String> excluded) {
        try {
            URI uri = policy.validateSyntax(url);
            PublicHttpsTransport.Response response = transport.exchange(uri, "GET", new byte[0],
                    Duration.ofSeconds(6), MAX_BODY_BYTES);
            if (response.status() < 200 || response.status() >= 300 || response.body().length > MAX_BODY_BYTES)
                throw new IllegalArgumentException();
            return parse(response.body(), uri, from, to, excluded);
        } catch (Exception exception) {
            throw new ResearchException("SOURCES_UNAVAILABLE");
        }
    }

    List<FeedEntry> parse(byte[] body, URI feedUrl, Instant from, Instant to, Set<String> excluded) {
        try {
            if (body.length > MAX_BODY_BYTES) throw new IllegalArgumentException();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute("http://www.oracle.com/xml/jaxp/properties/maxElementDepth", "64");
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException exception) throws SAXParseException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXParseException { throw exception; }
            });
            Element root = builder.parse(new ByteArrayInputStream(body)).getDocumentElement();
            String kind = localName(root);
            boolean atom = kind.equals("feed");
            if (!atom && !kind.equals("rss") && !kind.equals("RDF")) throw new IllegalArgumentException();
            Element channel = atom ? root : child(root, "channel");
            String source = plainText(text(channel, "title"), 120);
            if (source.isBlank()) source = feedUrl.getHost();
            var nodes = root.getElementsByTagNameNS("*", atom ? "entry" : "item");
            if (nodes.getLength() > 2000) throw new IllegalArgumentException();
            List<FeedEntry> result = new ArrayList<>();
            Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element entry = (Element) nodes.item(i);
                String title = plainText(text(entry, "title"), 200);
                Instant published = parseDate(atom ? first(text(entry, "published"), text(entry, "updated"))
                        : first(text(entry, "pubDate"), text(entry, "date")));
                if (title.isBlank() || published == null || published.isBefore(from) || published.isAfter(to)) continue;
                String link = atom ? atomLink(entry) : text(entry, "link");
                try {
                    String url = policy.validateSyntax(feedUrl.resolve(link.trim()).toString()).toString();
                    if (link.isBlank() || excluded.contains(url) || !seen.add(url)) continue;
                    String summary = plainText(atom ? first(text(entry, "summary"), text(entry, "content"))
                            : first(text(entry, "description"), text(entry, "encoded")), 1600);
                    result.add(new FeedEntry(title, summary, source, url, published));
                } catch (IllegalArgumentException ignored) {
                    // A malformed/private entry cannot supply a citation.
                }
            }
            return result.stream().sorted(Comparator.comparing(FeedEntry::publishedAt).reversed())
                    .limit(MAX_ENTRIES).toList();
        } catch (Exception exception) {
            throw new ResearchException("SOURCES_UNAVAILABLE");
        }
    }

    static String plainText(String input, int limit) {
        if (input == null) return "";
        String value = input.substring(0, Math.min(input.length(), 32000))
                .replace("&amp;", "&").replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        value = stripTags(value).replaceAll("[\\p{Cntrl}\\p{Cf}]", " ")
                .replaceAll("\\s+", " ").trim();
        return value.codePointCount(0, value.length()) <= limit ? value
                : value.substring(0, value.offsetByCodePoints(0, limit));
    }

    // Bounded linear scanning avoids hostile, unterminated HTML causing regex backtracking.
    private static String stripTags(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length();) {
            if (value.charAt(i) != '<') { result.append(value.charAt(i++)); continue; }
            int end = value.indexOf('>', i + 1);
            if (end < 0) { result.append(' ').append(value.substring(i + 1).replace('<', ' ')); break; }
            String tag = lower.substring(i + 1, end).trim();
            String hidden = tag.matches("script(?:\\s.*)?") ? "script" : tag.matches("style(?:\\s.*)?") ? "style" : null;
            if (hidden != null) {
                int closing = lower.indexOf("</" + hidden, end + 1);
                if (closing < 0) break;
                int closingEnd = lower.indexOf('>', closing);
                if (closingEnd < 0) break;
                end = closingEnd;
            }
            result.append(' ');
            i = end + 1;
        }
        return result.toString();
    }

    private static String atomLink(Element entry) {
        for (Node node = entry.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName(element).equals("link")
                    && (element.getAttribute("rel").isBlank() || element.getAttribute("rel").equals("alternate")))
                return element.getAttribute("href");
        }
        return "";
    }

    private static Instant parseDate(String text) {
        if (text == null || text.isBlank() || text.length() > 100) return null;
        for (DateTimeFormatter format : List.of(DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH))) {
            try { return OffsetDateTime.parse(text.trim(), format).toInstant(); }
            catch (RuntimeException ignored) {}
        }
        return null;
    }

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element && localName(element).equals(name)) return element;
        return null;
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        return element == null ? "" : element.getTextContent();
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static String first(String one, String two) { return one == null || one.isBlank() ? two : one; }

    record FeedEntry(String title, String excerpt, String sourceName, String url, Instant publishedAt) {}
}
