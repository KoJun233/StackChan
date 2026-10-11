package com.kj.stackchan.delegation;

import java.net.InetAddress;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Component
public class PublicHttpsUrlPolicy {
    private final AddressResolver resolver;

    public PublicHttpsUrlPolicy() { this(InetAddress::getAllByName); }

    @Autowired
    public PublicHttpsUrlPolicy(@Value("${app.delegation.public-dns-over-https:false}") boolean usePublicDoh,
            ObjectMapper mapper) {
        this(usePublicDoh ? new CloudflarePublicDnsResolver(mapper) : InetAddress::getAllByName);
    }

    PublicHttpsUrlPolicy(AddressResolver resolver) { this.resolver = resolver; }

    public URI validate(String value) {
        URI uri = validateSyntax(value);
        resolve(uri.getHost());
        return uri;
    }

    public URI validateSyntax(String value) {
        try {
            if (value == null || value.length() > 2048 || !value.equals(value.trim())
                    || value.chars().anyMatch(c -> c < 32 || c == 127)) throw invalid();
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || host == null || uri.getFragment() != null || uri.getPort() == 0
                    || uri.getPort() > 65535) throw invalid();
            host = unbracket(host).toLowerCase(Locale.ROOT);
            if (host.contains("%") || host.endsWith(".") || host.equals("localhost") || host.endsWith(".localhost")
                    || host.endsWith(".local") || host.endsWith(".internal")
                    || host.endsWith(".lan") || (!host.contains(".") && !host.contains(":"))) throw invalid();
            if (host.contains(":") || host.matches("[0-9.]+")) {
                InetAddress literal = InetAddress.getByName(host);
                if ((host.contains(":") && literal.getAddress().length == 4) || !isPublic(literal)) throw invalid();
            } else if (!validDomain(host)) throw invalid();
            return uri;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    /** Validates every answer, returning the exact addresses that the transport must connect to. */
    public List<InetAddress> resolve(String host) {
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var future = executor.submit(() -> resolver.resolve(unbracket(host)));
        try {
            List<InetAddress> addresses = Arrays.asList(future.get(3, TimeUnit.SECONDS));
            if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !isPublic(address))) throw invalid();
            return addresses;
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw invalid();
        } finally {
            future.cancel(true);
            executor.shutdownNow();
        }
    }

    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = Byte.toUnsignedInt(bytes[0]);
        int b = Byte.toUnsignedInt(bytes[1]);
        if (bytes.length == 4) {
            int c = Byte.toUnsignedInt(bytes[2]);
            return !(a == 0 || a == 10 || a == 127 || a >= 224
                    || (a == 100 && b >= 64 && b <= 127)
                    || (a == 169 && b == 254) || (a == 172 && b >= 16 && b <= 31)
                    || (a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2))))
                    || (a == 192 && b == 88 && c == 99)
                    || (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100)))
                    || (a == 203 && b == 0 && c == 113));
        }
        // Only currently allocated global unicast space; exclude transition/documentation ranges.
        if ((a & 0xe0) != 0x20 || (a == 0x20 && b == 0x02)) return false;
        if (a == 0x20 && b == 0x01) {
            int c = Byte.toUnsignedInt(bytes[2]), d = Byte.toUnsignedInt(bytes[3]);
            if (c <= 1 || (c == 0x0d && d == 0xb8)) return false;
        }
        return true;
    }

    static String unbracket(String host) {
        return host != null && host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
    }

    static boolean validDomain(String host) {
        return host != null && host.length() <= 253
                && host.matches("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)+");
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("PUBLIC_HTTPS_URL_REQUIRED");
    }

    @FunctionalInterface
    interface AddressResolver { InetAddress[] resolve(String host) throws Exception; }
}
