package com.kj.stackchan.delegation;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicHttpsUrlPolicyTest {
    private final PublicHttpsUrlPolicy policy = new PublicHttpsUrlPolicy();

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/rss", "https://user:secret@example.com/rss", "https://localhost/rss",
            "https://device.local/rss", "https://example.com./rss", "https://127.0.0.1/rss", "https://10.2.3.4/",
            "https://172.31.1.1/", "https://192.168.1.1/", "https://169.254.169.254/latest", "https://100.64.0.1/",
            "https://0.0.0.0/", "https://224.0.0.1/", "https://2130706433/", "https://[::1]/", "https://[fc00::1]/",
            "https://[fe80::1]/", "https://[::ffff:127.0.0.1]/", "https://[::ffff:8.8.8.8]/",
            "https://[2002:7f00:0001::1]/", "https://[2001:db8::1]/", "https://example.com/#fragment"})
    void rejectsNonPublicOrAmbiguousUrlsWithoutFetching(String url) {
        assertThatThrownBy(() -> policy.validateSyntax(url)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PUBLIC_HTTPS_URL_REQUIRED").hasNoCause();
    }

    @Test
    void acceptsOrdinaryPublicHttpsSyntaxAndGlobalAddresses() throws Exception {
        assertThat(policy.validateSyntax("https://huggingface.co/blog/feed.xml").getHost()).isEqualTo("huggingface.co");
        assertThat(PublicHttpsUrlPolicy.isPublic(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}))).isTrue();
        assertThat(PublicHttpsUrlPolicy.isPublic(InetAddress.getByName("2001:4860:4860::8888"))).isTrue();
    }

    @Test
    void rejectsMixedPublicAndPrivateDnsAnswersAndSanitizesResolutionErrors() throws Exception {
        var mixed = new PublicHttpsUrlPolicy(host -> new InetAddress[]{
                InetAddress.getByAddress(new byte[]{8, 8, 8, 8}), InetAddress.getByAddress(new byte[]{127, 0, 0, 1})});
        assertThatThrownBy(() -> mixed.validate("https://example.com/feed"))
                .hasMessage("PUBLIC_HTTPS_URL_REQUIRED").hasNoCause();
        var failed = new PublicHttpsUrlPolicy(host -> { throw new IllegalStateException("unsafe DNS contents"); });
        assertThatThrownBy(() -> failed.validate("https://example.com/feed"))
                .hasMessage("PUBLIC_HTTPS_URL_REQUIRED").hasNoCause();
        var publicDns = new PublicHttpsUrlPolicy(host -> new InetAddress[]{InetAddress.getByAddress(new byte[]{8, 8, 8, 8})});
        assertThat(publicDns.validate("https://example.com/feed").getHost()).isEqualTo("example.com");
    }
}
