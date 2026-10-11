package com.kj.stackchan.delegation;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Optional resolver for explicitly configured public delegation destinations only. */
final class CloudflarePublicDnsResolver implements PublicHttpsUrlPolicy.AddressResolver {
    static final int MAX_RESPONSE_BYTES = 32768; // A + AAAA together are at most 64 KiB.
    private static final String RESOLVER_HOST = "cloudflare-dns.com";
    private final ObjectMapper mapper;
    private final PublicHttpsTransport transport;

    CloudflarePublicDnsResolver(ObjectMapper mapper) {
        this(mapper, bootstrapTransport());
    }

    CloudflarePublicDnsResolver(ObjectMapper mapper, PublicHttpsTransport transport) {
        this.mapper = mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.transport = transport;
    }

    @Override
    public InetAddress[] resolve(String rawHost) {
        String host = PublicHttpsUrlPolicy.unbracket(rawHost).toLowerCase(Locale.ROOT);
        if (host.contains(":") || host.matches("[0-9.]+")) {
            try {
                InetAddress literal = InetAddress.getByName(host);
                if ((host.contains(":") && literal.getAddress().length == 4) || !PublicHttpsUrlPolicy.isPublic(literal))
                    throw failure();
                return new InetAddress[]{literal};
            } catch (Exception exception) { throw failure(); }
        }
        if (!PublicHttpsUrlPolicy.validDomain(host)) throw failure();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<List<InetAddress>>> requests = new ArrayList<>();
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        try {
            for (int type : List.of(1, 28)) requests.add(executor.submit(() -> query(host, type)));
            List<InetAddress> addresses = new ArrayList<>();
            for (var request : requests)
                addresses.addAll(request.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS));
            if (addresses.isEmpty() || addresses.stream().anyMatch(address -> !PublicHttpsUrlPolicy.isPublic(address)))
                throw failure();
            return addresses.stream().distinct().toArray(InetAddress[]::new);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw failure();
        } finally {
            requests.forEach(request -> request.cancel(true));
            executor.shutdownNow();
        }
    }

    private List<InetAddress> query(String host, int type) throws Exception {
        // A validated DNS name has no URL metacharacters; provider and path are fixed.
        URI uri = URI.create("https://" + RESOLVER_HOST + "/dns-query?name=" + host + "&type=" + type);
        var response = transport.exchange(uri, "GET", new byte[0], Duration.ofSeconds(3), MAX_RESPONSE_BYTES,
                "application/dns-json");
        if (response.status() != 200 || response.body().length > MAX_RESPONSE_BYTES) throw failure();
        JsonNode root = mapper.readTree(response.body());
        if (root == null || !root.isObject() || !root.path("Status").isIntegralNumber()
                || root.path("Status").intValue() != 0 || !root.path("TC").isBoolean() || root.path("TC").booleanValue()
                || !root.path("Question").isArray() || root.path("Question").size() != 1) throw failure();
        var question = root.path("Question").get(0);
        if (!question.path("name").isTextual()
                || (!question.path("name").textValue().equalsIgnoreCase(host + ".")
                    && !question.path("name").textValue().equalsIgnoreCase(host))
                || !question.path("type").isIntegralNumber() || question.path("type").intValue() != type) throw failure();
        JsonNode answers = root.get("Answer");
        if (answers == null) return List.of();
        if (!answers.isArray() || answers.size() > 64) throw failure();
        List<InetAddress> result = new ArrayList<>();
        for (var record : answers) {
            if (!record.path("type").isIntegralNumber()) throw failure();
            int recordType = record.path("type").intValue();
            if (recordType != 1 && recordType != 28) continue;
            if (!record.path("data").isTextual()) throw failure();
            String data = record.path("data").textValue();
            if (data.length() > 64 || (recordType == 1 && !data.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}"))
                    || (recordType == 28 && (!data.contains(":") || !data.matches("[0-9a-fA-F:]+")))) throw failure();
            InetAddress address = InetAddress.getByName(data); // Numeric literals only; no recursive system DNS.
            if ((recordType == 1 && address.getAddress().length != 4)
                    || (recordType == 28 && address.getAddress().length != 16)
                    || !PublicHttpsUrlPolicy.isPublic(address)) throw failure();
            result.add(address);
        }
        return result;
    }

    private static PublicHttpsTransport bootstrapTransport() {
        return new PinnedHttpsTransport(new PublicHttpsUrlPolicy(host -> {
            if (!RESOLVER_HOST.equals(host)) throw failure();
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1}),
                    InetAddress.getByAddress(new byte[]{1, 0, 0, 1})};
        }));
    }

    private static IllegalArgumentException failure() { return new IllegalArgumentException("PUBLIC_DNS_UNAVAILABLE"); }
}
