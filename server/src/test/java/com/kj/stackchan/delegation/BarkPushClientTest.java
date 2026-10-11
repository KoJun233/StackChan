package com.kj.stackchan.delegation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.delegation.BarkPushClient.DeliveryOutcome;
import com.kj.stackchan.delegation.ResearchBriefGenerator.BriefItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarkPushClientTest {
    private static final String SERVER = "https://api.day.app", KEY = "test_key";

    @Test
    void sendsSecretOnlyInJsonBodyWithArchiveAndBudgetAndAcceptsCode200() {
        var mapper = new ObjectMapper();
        var client = client((uri, method, body, timeout, max) -> {
            assertThat(uri.toString()).isEqualTo("https://api.day.app/push").doesNotContain(KEY);
            assertThat(method).isEqualTo("POST");
            assertThat(timeout.getSeconds()).isEqualTo(8);
            var json = mapper.readTree(body);
            assertThat(json.path("device_key").textValue()).isEqualTo(KEY);
            assertThat(json.path("isArchive").asText()).isEqualTo("1");
            assertThat(body.length).isLessThanOrEqualTo(BarkPushClient.MAX_PAYLOAD_BYTES);
            return new PublicHttpsTransport.Response(200, bytes("{\"code\":200,\"message\":\"success\"}"));
        });
        assertThat(client.send(SERVER, KEY, "每日进展", "有来源的正文")).isEqualTo(DeliveryOutcome.SENT);
    }

    @Test
    void classifiesKnownRejectionAndUnknownDeliveryWithoutRetries() {
        for (int status : new int[]{302, 400, 401, 429, 500})
            assertThat(client((uri, method, body, timeout, max) -> new PublicHttpsTransport.Response(status, new byte[0]))
                    .send(SERVER, KEY, "t", "b")).isEqualTo(DeliveryOutcome.FAILED);
        assertThat(client((uri, method, body, timeout, max) ->
                new PublicHttpsTransport.Response(200, bytes("{\"code\":400}"))).send(SERVER, KEY, "t", "b"))
                .isEqualTo(DeliveryOutcome.FAILED);
        for (String ambiguous : List.of("", "not json", "{}", "{\"code\":200,\"code\":400}", "{\"code\":200} {}"))
            assertThat(client((uri, method, body, timeout, max) ->
                    new PublicHttpsTransport.Response(200, bytes(ambiguous))).send(SERVER, KEY, "t", "b"))
                    .isEqualTo(DeliveryOutcome.UNKNOWN);
        AtomicInteger attempts = new AtomicInteger();
        assertThat(client((uri, method, body, timeout, max) -> {
            attempts.incrementAndGet();
            throw new IOException("secret key and unsafe error response");
        }).send(SERVER, KEY, "t", "b")).isEqualTo(DeliveryOutcome.UNKNOWN);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void preservesEverySourceAndImpactWithinUtf8BudgetWithoutSplittingEmoji() {
        List<BriefItem> items = IntStream.range(0, 5).mapToObj(i -> new BriefItem("进展 " + i,
                "中文😀摘要".repeat(100), "影响说明", "官方来源", "https://example.com/article/" + i,
                RssAtomPublicFeedClientTest.TO)).toList();
        String body = BarkPushClient.renderBody(items);
        assertThat(bytes(body).length).isLessThanOrEqualTo(BarkPushClient.MAX_BODY_BYTES);
        items.forEach(item -> assertThat(body).contains(item.title(), item.impact(), item.url(), item.sourceName()));
        assertThat(new String(bytes(body), StandardCharsets.UTF_8)).isEqualTo(body).doesNotContain("�");
        assertThat(body.length()).isLessThan(items.stream().mapToInt(item -> item.summary().length()).sum());
    }

    @Test
    void rejectsImpossibleCitationSizePrivateServerAndOversizedBodyBeforeSending() {
        var transport = (PublicHttpsTransport) (uri, method, body, timeout, max) -> { throw new AssertionError("must not send"); };
        assertThat(client(transport).send("https://127.0.0.1", KEY, "t", "b")).isEqualTo(DeliveryOutcome.FAILED);
        assertThat(client(transport).send(SERVER, KEY, "t", "字".repeat(1000))).isEqualTo(DeliveryOutcome.FAILED);
        assertThatThrownBy(() -> BarkPushClient.renderBody(List.of(new BriefItem("t", "s", "i", "source",
                "https://example.com/" + "a".repeat(2800), RssAtomPublicFeedClientTest.TO))))
                .hasMessage("PUSH_CONTENT_TOO_LARGE").hasNoCause();
    }

    @Test
    void fiveMaximumChineseEntriesWithRealisticUrlsRemainDeliverableAndKeepCitations() {
        List<BriefItem> items = IntStream.range(0, 5).mapToObj(i -> new BriefItem("题".repeat(200),
                "摘".repeat(220), "影".repeat(120), "源".repeat(120),
                "https://openai.com/index/" + "official-research-update-".repeat(4) + i + "/",
                RssAtomPublicFeedClientTest.TO)).toList();
        String body = BarkPushClient.renderBody(items);
        items.forEach(item -> assertThat(body).contains(item.url()));
        assertThat(bytes(body).length).isLessThanOrEqualTo(BarkPushClient.MAX_BODY_BYTES);
        assertThat(body).contains("摘", "影", "源", "…");
        assertThat(client((uri, method, payload, timeout, max) ->
                new PublicHttpsTransport.Response(200, bytes("{\"code\":200}"))).send(SERVER, KEY, "AI 日报", body))
                .isEqualTo(DeliveryOutcome.SENT);
    }

    private static BarkPushClient client(PublicHttpsTransport transport) {
        return new BarkPushClient(new PublicHttpsUrlPolicy(), transport, new ObjectMapper());
    }
    @Test void rendersGeneralTaskContentWithoutRequiringResearchItemsOrSources() {
        String plain=BarkPushClient.renderBody("今日练习：给朋友写一句问候。",List.of());
        assertThat(plain).isEqualTo("今日练习：给朋友写一句问候。");
        String link="https://example.com/verified-evidence";
        String compact=BarkPushClient.renderBody("一个😀练习。".repeat(1000),
                List.of(new DelegationTaskExecutor.ExecutionEvidence("实际来源",link,"已读取")));
        assertThat(bytes(compact).length).isLessThanOrEqualTo(BarkPushClient.MAX_BODY_BYTES);
        assertThat(compact).endsWith(link).contains("…").doesNotContain("�");
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
