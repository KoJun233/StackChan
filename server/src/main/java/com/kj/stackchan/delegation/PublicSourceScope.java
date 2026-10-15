package com.kj.stackchan.delegation;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Exact public source scope; page links and model proposals cannot enlarge it. */
public final class PublicSourceScope {
    private PublicSourceScope() {}

    public static String canonical(String value) {
        URI uri = new PublicHttpsUrlPolicy().validateSyntax(value);
        String query = uri.getRawQuery();
        if (query != null) for (String part : query.split("&")) {
            String key = java.net.URLDecoder.decode(part.split("=", 2)[0], java.nio.charset.StandardCharsets.UTF_8);
            if (key.matches("(?i)(?:api[_-]?key|access[_-]?token|token|auth|authorization|password|passwd|secret|client[_-]?secret|signature|sig|session|sessionid|key|credential|x-amz-.*|x-goog-.*)"))
                throw new IllegalArgumentException("PUBLIC_SOURCE_AUTHENTICATION_NOT_ALLOWED");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains(":")) host = host.startsWith("[") ? host : "[" + host + "]";
        String path = uri.normalize().getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        return "https://" + host + (uri.getPort() < 0 || uri.getPort() == 443 ? "" : ":" + uri.getPort())
                + path + (query == null ? "" : "?" + query);
    }

    public static List<String> canonical(List<String> values) {
        return values == null ? List.of() : values.stream().map(PublicSourceScope::canonical).distinct().toList();
    }
}
