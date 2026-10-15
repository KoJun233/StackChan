package com.kj.stackchan.delegation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PublicSourceScopeTest {
    @Test void canonicalizesHostSchemeDefaultPortAndDotSegmentsWithoutExpandingPath() {
        assertThat(PublicSourceScope.canonical("HTTPS://Example.COM:443/a/../b?q=desk")).isEqualTo("https://example.com/b?q=desk");
        assertThat(PublicSourceScope.canonical("https://example.com")).isEqualTo("https://example.com/");
        assertThat(PublicSourceScope.canonical("https://example.com/b?item=2")).isNotEqualTo(PublicSourceScope.canonical("https://example.com/b?item=3"));
    }
    @Test void rejectsPrivateHttpCredentialsAndAuthenticationQueryParameters() {
        for(String value:java.util.List.of("http://example.com/","https://127.0.0.1/","https://user:password@example.com/",
                "https://example.com/?token=private","https://example.com/?%61pi_key=private","https://example.com/#private",
                "https://example.com/?X-Amz-Signature=synthetic&X-Amz-Credential=fixture",
                "https://example.com/?X-Goog-Signature=synthetic","https://example.com/?client_secret=fixture"))
            assertThatThrownBy(()->PublicSourceScope.canonical(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
