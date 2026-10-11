package com.kj.stackchan.delegation;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudflarePublicDnsResolverTest {
    @Test
    void usesFixedDohProviderAndAcceptHeaderWithBothPublicAAndAaaaWithoutSystemDns() {
        AtomicInteger queries = new AtomicInteger();
        PublicHttpsTransport transport = new PublicHttpsTransport() {
            @Override public Response exchange(URI uri, String method, byte[] body, Duration timeout, int max)
                    throws IOException { throw new AssertionError("DNS JSON Accept header required"); }
            @Override public Response exchange(URI uri, String method, byte[] body, Duration timeout, int max, String accept) {
                queries.incrementAndGet();
                assertThat(uri.getHost()).isEqualTo("cloudflare-dns.com");
                assertThat(uri.getPath()).isEqualTo("/dns-query");
                assertThat(uri.getQuery()).startsWith("name=example.com&type=");
                assertThat(method).isEqualTo("GET");
                assertThat(body).isEmpty();
                assertThat(accept).isEqualTo("application/dns-json");
                assertThat(timeout).isEqualTo(Duration.ofSeconds(3));
                assertThat(max).isEqualTo(32768);
                int type = uri.getQuery().endsWith("28") ? 28 : 1;
                String json = answer(type, type == 1 ? "8.8.8.8" : "2001:4860:4860::8888");
                return new Response(200, bytes(type == 1 ? json : json.replace("example.com.", "example.com")));
            }
        };
        var result = new CloudflarePublicDnsResolver(new ObjectMapper(), transport).resolve("example.com");
        assertThat(result).hasSize(2);
        assertThat(Arrays.stream(result).allMatch(PublicHttpsUrlPolicy::isPublic)).isTrue();
        assertThat(queries).hasValue(2);
    }

    @Test
    void literalPublicIpNeedsNoDohAndPrivateOrUnsafeDomainCannotBecomeAQuery() {
        var resolver = new CloudflarePublicDnsResolver(new ObjectMapper(),
                (uri, method, body, timeout, max) -> { throw new AssertionError("No DNS query expected"); });
        assertThat(resolver.resolve("8.8.8.8")[0].getHostAddress()).isEqualTo("8.8.8.8");
        for (String host : new String[]{"example.com&name=localhost", "localhost", "127.0.0.1", "a..example.com",
                "-bad.example.com", "example.com.", "::ffff:8.8.8.8"})
            assertThatThrownBy(() -> resolver.resolve(host)).hasMessage("PUBLIC_DNS_UNAVAILABLE").hasNoCause();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "198.18.0.40", "10.2.3.4", "169.254.169.254", "100.64.0.1"})
    void refusesPrivateOrProxyFakeAddressEvenWhenOtherAnswerIsPublic(String privateAddress) {
        var resolver = new CloudflarePublicDnsResolver(new ObjectMapper(), (uri, method, body, timeout, max) -> {
            int type = uri.getQuery().endsWith("28") ? 28 : 1;
            return new PublicHttpsTransport.Response(200,
                    bytes(answer(type, type == 1 ? privateAddress : "2001:4860:4860::8888")));
        });
        assertThatThrownBy(() -> resolver.resolve("example.com")).hasMessage("PUBLIC_DNS_UNAVAILABLE").hasNoCause();
    }

    @Test
    void rejectsRedirectOversizeTruncationMismatchedQuestionsAndProviderErrorsSafely() {
        for (PublicHttpsTransport.Response response : new PublicHttpsTransport.Response[]{
                new PublicHttpsTransport.Response(302, new byte[0]),
                new PublicHttpsTransport.Response(200, new byte[32769]),
                new PublicHttpsTransport.Response(200, bytes(answer(1, "8.8.8.8").replace("\"TC\":false", "\"TC\":true"))),
                new PublicHttpsTransport.Response(200, bytes(answer(1, "8.8.8.8").replace("example.com.", "attacker.com."))),
                new PublicHttpsTransport.Response(200, bytes("{\"Status\":3}")),
                new PublicHttpsTransport.Response(200, bytes("unsafe response"))}) {
            var resolver = new CloudflarePublicDnsResolver(new ObjectMapper(),
                    (uri, method, body, timeout, max) -> response);
            assertThatThrownBy(() -> resolver.resolve("example.com")).hasMessage("PUBLIC_DNS_UNAVAILABLE").hasNoCause();
        }
        var failed = new CloudflarePublicDnsResolver(new ObjectMapper(), (uri, method, body, timeout, max) -> {
            throw new IOException("provider untrusted body");
        });
        assertThatThrownBy(() -> failed.resolve("example.com")).hasMessage("PUBLIC_DNS_UNAVAILABLE").hasNoCause();
    }

    private static String answer(int type, String address) {
        return "{\"Status\":0,\"TC\":false,\"Question\":[{\"name\":\"example.com.\",\"type\":" + type
                + "}],\"Answer\":[{\"type\":" + type + ",\"data\":\"" + address + "\"}]}";
    }
    private static byte[] bytes(String value) { return RssAtomPublicFeedClientTest.bytes(value); }
}
