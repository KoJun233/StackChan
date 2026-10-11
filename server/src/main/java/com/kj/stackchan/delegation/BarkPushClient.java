package com.kj.stackchan.delegation;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.delegation.ResearchBriefGenerator.BriefItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BarkPushClient {
    static final int MAX_BODY_BYTES = 2800;
    static final int MAX_PAYLOAD_BYTES = 3600;
    private final PublicHttpsUrlPolicy policy;
    private final PublicHttpsTransport transport;
    private final ObjectMapper mapper;

    @Autowired
    public BarkPushClient(PublicHttpsUrlPolicy policy, PinnedHttpsTransport transport, ObjectMapper mapper) {
        this(policy, (PublicHttpsTransport) transport, mapper);
    }

    BarkPushClient(PublicHttpsUrlPolicy policy, PublicHttpsTransport transport, ObjectMapper mapper) {
        this.policy = policy;
        this.transport = transport;
        this.mapper = mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public DeliveryOutcome send(String serverUrl, String deviceKey, String title, String body) {
        URI endpoint;
        byte[] payload;
        try {
            URI server = policy.validateSyntax(serverUrl);
            if (server.getRawQuery() != null || deviceKey == null
                    || !deviceKey.matches("[A-Za-z0-9_-]{1,512}") || body == null || body.isBlank()
                    || body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) return DeliveryOutcome.FAILED;
            String base = server.toString().replaceAll("/+$", "");
            endpoint = URI.create(base + "/push");
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("device_key", deviceKey);
            fields.put("title", RssAtomPublicFeedClient.plainText(title, 80));
            fields.put("body", body);
            fields.put("group", "StackChan");
            fields.put("isArchive", "1");
            fields.put("level", "passive");
            payload = mapper.writeValueAsBytes(fields);
            // APNs reserves additional bytes for aps and provider metadata.
            if (payload.length > MAX_PAYLOAD_BYTES) return DeliveryOutcome.FAILED;
        } catch (Exception exception) {
            return DeliveryOutcome.FAILED;
        }
        try {
            PublicHttpsTransport.Response response = transport.exchange(endpoint, "POST", payload,
                    Duration.ofSeconds(8), 8192);
            if (response.status() < 200 || response.status() >= 300) return DeliveryOutcome.FAILED;
            var result = mapper.readTree(response.body());
            if (response.body().length > 8192 || result == null || !result.isObject()
                    || !result.path("code").isIntegralNumber() || !result.path("code").canConvertToInt())
                return DeliveryOutcome.UNKNOWN;
            return result.path("code").intValue() == 200 ? DeliveryOutcome.SENT : DeliveryOutcome.FAILED;
        } catch (Exception exception) {
            // A timeout/disconnect or ambiguous response cannot prove non-delivery.
            return DeliveryOutcome.UNKNOWN;
        }
    }

    /** A compact phone edition; the console retains full text and every citation is preserved. */
    public static String renderBody(List<BriefItem> items) {
        if (items == null || items.isEmpty() || items.size() > 10) throw tooLarge();
        List<String> summaries = new ArrayList<>();
        for (BriefItem item : items) {
            if (item == null || item.title() == null || item.impact() == null || item.url() == null
                    || item.sourceName() == null || item.summary() == null) throw tooLarge();
            summaries.add("");
        }
        items = items.stream().map(item -> new BriefItem(shorten(item.title(), 50), item.summary(),
                shorten(item.impact(), 40), shorten(item.sourceName(), 30), item.url(), item.publishedAt())).toList();
        String skeleton = render(items, summaries);
        int available = MAX_BODY_BYTES - utf8Length(skeleton);
        if (available < items.size() * 7) throw tooLarge();
        int remaining = items.size();
        for (int i = 0; i < items.size(); i++) {
            String summary = truncateUtf8(items.get(i).summary(), available / remaining);
            summaries.set(i, summary);
            available -= utf8Length(summary);
            remaining--;
        }
        String body = render(items, summaries);
        if (utf8Length(body) > MAX_BODY_BYTES) throw tooLarge();
        return body;
    }

    /** General task completion text; citations remain complete when the phone edition is shortened. */
    public static String renderBody(String output,List<DelegationTaskExecutor.ExecutionEvidence> evidence) {
        if(output==null || output.isBlank() || evidence==null || evidence.size()>20) throw tooLarge();
        StringBuilder citations=new StringBuilder();
        var links=evidence.stream().filter(item->item!=null && item.url()!=null && !item.url().isBlank())
                .map(DelegationTaskExecutor.ExecutionEvidence::url).distinct().toList();
        for(String link:links) citations.append('\n').append(link);
        int budget=MAX_BODY_BYTES-utf8Length(citations.toString());
        if(budget<32) throw tooLarge();
        String body=truncateUtf8(output,budget)+citations;
        if(utf8Length(body)>MAX_BODY_BYTES) throw tooLarge();
        return body;
    }

    private static String render(List<BriefItem> items, List<String> summaries) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            BriefItem item = items.get(i);
            if (i > 0) result.append("\n\n");
            result.append(i + 1).append(". ").append(item.title()).append('\n')
                    .append(summaries.get(i)).append('\n').append("影响：").append(item.impact())
                    .append('\n').append("来源：").append(item.sourceName()).append('\n').append(item.url());
        }
        return result.toString();
    }

    private static String truncateUtf8(String value, int budget) {
        boolean truncated = utf8Length(value) > budget;
        if (truncated) budget -= 3;
        int end = 0, bytes = 0;
        while (end < value.length()) {
            int point = value.codePointAt(end);
            int count = point <= 0x7f ? 1 : point <= 0x7ff ? 2 : point <= 0xffff ? 3 : 4;
            if (bytes + count > budget) break;
            bytes += count;
            end += Character.charCount(point);
        }
        return value.substring(0, end) + (truncated ? "…" : "");
    }

    private static int utf8Length(String value) { return value.getBytes(StandardCharsets.UTF_8).length; }
    private static String shorten(String value, int maxPoints) {
        return value.codePointCount(0, value.length()) <= maxPoints ? value
                : value.substring(0, value.offsetByCodePoints(0, maxPoints - 1)) + "…";
    }
    private static IllegalArgumentException tooLarge() { return new IllegalArgumentException("PUSH_CONTENT_TOO_LARGE"); }

    /** SENT proves channel acceptance, never phone receipt or reading. */
    public enum DeliveryOutcome { SENT, FAILED, UNKNOWN }
}
