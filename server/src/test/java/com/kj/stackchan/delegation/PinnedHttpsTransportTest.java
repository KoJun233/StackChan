package com.kj.stackchan.delegation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PinnedHttpsTransportTest {
    private final URI uri = URI.create("https://example.com/feed.xml");
    private final PublicHttpsUrlPolicy policy = mock(PublicHttpsUrlPolicy.class);
    private final SSLSocketFactory factory = mock(SSLSocketFactory.class);
    private final Socket raw = mock(Socket.class);
    private final SSLSocket tls = mock(SSLSocket.class);

    @Test
    void connectsOnlyToResolvedPublicIpAndValidatesOriginalTlsHostname() throws Exception {
        var transport = setup("HTTP/1.1 200 OK\r\nContent-Length: 4\r\nSet-Cookie: first=1\r\nSet-Cookie: second=2\r\n\r\nfeed");
        var result = transport.exchange(uri, "GET", new byte[0], Duration.ofSeconds(2), 1024);
        assertThat(result.status()).isEqualTo(200);
        assertThat(new String(result.body(), StandardCharsets.UTF_8)).isEqualTo("feed");
        var target = ArgumentCaptor.forClass(java.net.SocketAddress.class);
        verify(raw).connect(target.capture(), anyInt());
        assertThat(((InetSocketAddress) target.getValue()).getAddress().getHostAddress()).isEqualTo("8.8.8.8");
        verify(factory).createSocket(raw, "example.com", 443, true);
        var parameters = ArgumentCaptor.forClass(SSLParameters.class);
        verify(tls).setSSLParameters(parameters.capture());
        assertThat(parameters.getValue().getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        assertThat(parameters.getValue().getServerNames()).hasSize(1);
        verify(policy, times(1)).resolve("example.com");
    }

    @Test
    void returnsRedirectWithoutFollowingItAndBoundsChunkedOrDeclaredBodies() throws Exception {
        var transport = setup("HTTP/1.1 302 Found\r\nLocation: https://127.0.0.1/secret\r\n\r\n");
        assertThat(transport.exchange(uri, "GET", new byte[0], Duration.ofSeconds(2), 4).status()).isEqualTo(302);
        verify(policy, times(1)).resolve("example.com");
        transport = setup("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n4\r\nfeed\r\n0\r\n\r\n");
        assertThat(new String(transport.exchange(uri, "GET", new byte[0], Duration.ofSeconds(2), 4).body(),
                StandardCharsets.UTF_8)).isEqualTo("feed");
        var oversized = setup("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n");
        assertThatThrownBy(() -> oversized.exchange(uri, "GET", new byte[0], Duration.ofSeconds(2), 4))
                .hasMessage("HTTPS_REQUEST_FAILED").hasNoCause();
        var incomplete = setup("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nx");
        assertThatThrownBy(() -> incomplete.exchange(uri, "GET", new byte[0], Duration.ofSeconds(2), 4))
                .hasMessage("HTTPS_REQUEST_FAILED").hasNoCause();
    }

    private PinnedHttpsTransport setup(String response) throws Exception {
        when(policy.validateSyntax(uri.toString())).thenReturn(uri);
        when(policy.resolve("example.com")).thenReturn(List.of(InetAddress.getByAddress(new byte[]{8, 8, 8, 8})));
        when(factory.createSocket(raw, "example.com", 443, true)).thenReturn(tls);
        when(tls.getSSLParameters()).thenReturn(new SSLParameters());
        when(tls.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(tls.getInputStream()).thenReturn(new ByteArrayInputStream(response.getBytes(StandardCharsets.US_ASCII)));
        return new PinnedHttpsTransport(policy, factory, () -> raw);
    }
}
