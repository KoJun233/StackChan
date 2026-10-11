package com.kj.stackchan.delegation;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

interface PublicHttpsTransport {
    Response exchange(URI uri, String method, byte[] body, Duration timeout, int maxResponseBytes) throws IOException;
    default Response exchange(URI uri, String method, byte[] body, Duration timeout, int maxResponseBytes,
            String accept) throws IOException {
        return exchange(uri, method, body, timeout, maxResponseBytes);
    }
    record Response(int status, byte[] body) {}
}
