package com.kj.stackchan.delegation;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

/** A single HTTP/1.1 request over TLS to a validated, pinned IP. No proxy or redirects. */
@Component
class PinnedHttpsTransport implements PublicHttpsTransport {
    private final PublicHttpsUrlPolicy policy;
    private final SSLSocketFactory socketFactory;
    private final Supplier<Socket> sockets;

    @Autowired
    PinnedHttpsTransport(PublicHttpsUrlPolicy policy) {
        this(policy, (SSLSocketFactory) SSLSocketFactory.getDefault(), Socket::new);
    }

    PinnedHttpsTransport(PublicHttpsUrlPolicy policy, SSLSocketFactory socketFactory, Supplier<Socket> sockets) {
        this.policy = policy;
        this.socketFactory = socketFactory;
        this.sockets = sockets;
    }

    @Override
    public Response exchange(URI uri, String method, byte[] body, Duration timeout, int maxResponseBytes)
            throws IOException {
        return exchange(uri, method, body, timeout, maxResponseBytes, null);
    }

    @Override
    public Response exchange(URI uri, String method, byte[] body, Duration timeout, int maxResponseBytes,
            String accept) throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            if (accept != null && !accept.equals("application/dns-json")) throw failure();
            uri = policy.validateSyntax(uri.toString());
            String host = PublicHttpsUrlPolicy.unbracket(uri.getHost());
            var addresses = policy.resolve(host);
            int port = uri.getPort() < 0 ? 443 : uri.getPort();
            try (Socket raw = sockets.get()) {
                raw.connect(new InetSocketAddress(addresses.getFirst(), port), remainingMillis(deadline));
                raw.setSoTimeout(remainingMillis(deadline));
                try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(raw, host, port, true)) {
                    SSLParameters parameters = socket.getSSLParameters();
                    parameters.setEndpointIdentificationAlgorithm("HTTPS");
                    if (!host.contains(":") && !host.matches("[0-9.]+"))
                        parameters.setServerNames(List.of(new SNIHostName(host)));
                    socket.setSSLParameters(parameters);
                    socket.setSoTimeout(remainingMillis(deadline));
                    socket.startHandshake();
                    String path = uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
                    String header = method + " " + path + " HTTP/1.1\r\nHost: " + uri.getRawAuthority()
                            + "\r\nUser-Agent: StackChan/1.0\r\nAccept-Encoding: identity\r\nConnection: close\r\n"
                            + (accept == null ? "" : "Accept: " + accept + "\r\n")
                            + (body.length > 0 ? "Content-Type: application/json; charset=utf-8\r\nContent-Length: "
                                    + body.length + "\r\n" : "") + "\r\n";
                    socket.getOutputStream().write(header.getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(body);
                    socket.getOutputStream().flush();
                    InputStream input = socket.getInputStream();
                    String statusLine = line(input, socket, deadline);
                    if (!statusLine.matches("HTTP/1\\.[01] [0-9]{3}.*")) throw failure();
                    int status = Integer.parseInt(statusLine.substring(9, 12));
                    Map<String, String> headers = new HashMap<>();
                    int headerBytes = statusLine.length(), headerCount = 0;
                    for (String line; !(line = line(input, socket, deadline)).isEmpty();) {
                        headerBytes += line.length();
                        if (++headerCount > 64 || headerBytes > 16384) throw failure();
                        int separator = line.indexOf(':');
                        if (separator < 1) throw failure();
                        String key = line.substring(0, separator).toLowerCase(Locale.ROOT);
                        if (headers.putIfAbsent(key, line.substring(separator + 1).trim()) != null
                                && (key.equals("content-length") || key.equals("transfer-encoding")
                                    || key.equals("content-encoding"))) throw failure();
                    }
                    // A known HTTP rejection is enough; untrusted error bodies are never needed.
                    if (status < 200 || status >= 300) return new Response(status, new byte[0]);
                    String encoding = headers.getOrDefault("content-encoding", "identity");
                    if (!encoding.equalsIgnoreCase("identity")) throw failure();
                    String transfer = headers.get("transfer-encoding");
                    if (transfer != null && (!transfer.equalsIgnoreCase("chunked")
                            || headers.containsKey("content-length"))) throw failure();
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (transfer != null) {
                        while (true) {
                            String sizeLine = line(input, socket, deadline);
                            int extension = sizeLine.indexOf(';');
                            long size = Long.parseLong(extension < 0 ? sizeLine : sizeLine.substring(0, extension), 16);
                            if (size < 0 || size > maxResponseBytes - output.size()) throw failure();
                            if (size == 0) break;
                            copy(input, output, size, socket, deadline, maxResponseBytes);
                            if (!line(input, socket, deadline).isEmpty()) throw failure();
                        }
                    } else if (headers.containsKey("content-length")) {
                        long size = Long.parseLong(headers.get("content-length"));
                        if (size < 0 || size > maxResponseBytes) throw failure();
                        copy(input, output, size, socket, deadline, maxResponseBytes);
                    } else {
                        copy(input, output, -1, socket, deadline, maxResponseBytes);
                    }
                    return new Response(status, output.toByteArray());
                }
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure();
        }
    }

    private static void copy(InputStream input, ByteArrayOutputStream output, long size, Socket socket,
            long deadline, int max) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        while (size < 0 || copied < size) {
            socket.setSoTimeout(remainingMillis(deadline));
            int count = input.read(buffer, 0, size < 0 ? buffer.length : (int) Math.min(buffer.length, size - copied));
            if (count < 0) {
                if (size >= 0) throw new EOFException("HTTPS_RESPONSE_INCOMPLETE");
                return;
            }
            if (output.size() + count > max) throw failure();
            output.write(buffer, 0, count);
            copied += count;
        }
    }

    private static String line(InputStream input, Socket socket, long deadline) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (output.size() <= 8192) {
            socket.setSoTimeout(remainingMillis(deadline));
            int value = input.read();
            if (value < 0) throw failure();
            if (value == '\n') {
                byte[] bytes = output.toByteArray();
                if (bytes.length == 0 || bytes[bytes.length - 1] != '\r') throw failure();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            output.write(value);
        }
        throw failure();
    }

    private static int remainingMillis(long deadline) throws IOException {
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw failure();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, remaining));
    }

    private static IOException failure() { return new IOException("HTTPS_REQUEST_FAILED"); }
}
