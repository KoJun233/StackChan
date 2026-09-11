package com.kj.stackchan.interaction;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HackerNewsBriefClient {

    static final URI API_ROOT = URI.create("https://hacker-news.firebaseio.com/v0/");
    private static final String SOURCE_NAME = "Hacker News";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);
    private static final Duration MAX_STORY_AGE = Duration.ofDays(4);
    private static final int MAX_INDEX_BYTES = 64 * 1024;
    private static final int MAX_ITEM_BYTES = 32 * 1024;
    private static final int MAX_ITEM_REQUESTS = 24;
    private static final int MAX_CANDIDATES = 12;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI apiRoot;

    @Autowired
    public HackerNewsBriefClient(ObjectMapper objectMapper) {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(), objectMapper, API_ROOT);
    }

    HackerNewsBriefClient(HttpClient httpClient, ObjectMapper objectMapper, URI apiRoot) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiRoot = apiRoot;
    }

    public List<InterestBrief> fetch(Instant now) {
        List<Long> ids = parseIds(request(apiRoot.resolve("beststories.json"), MAX_INDEX_BYTES));
        List<CompletableFuture<InterestBrief>> futures = ids.stream()
                .limit(MAX_ITEM_REQUESTS)
                .map(id -> requestAsync(apiRoot.resolve("item/" + id + ".json"), MAX_ITEM_BYTES)
                        .thenApply(body -> parseStory(body, now))
                        .exceptionally(ignored -> null))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .exceptionally(ignored -> null)
                .join();
        Instant retrievedAt = now;
        List<InterestBrief> result = new ArrayList<>();
        for (CompletableFuture<InterestBrief> future : futures) {
            if (!future.isDone()) continue;
            InterestBrief story = future.getNow(null);
            if (story != null) {
                result.add(new InterestBrief(
                        story.sourceName(), story.title(), story.url(), story.publishedAt(), retrievedAt
                ));
                if (result.size() == MAX_CANDIDATES) break;
            }
        }
        return List.copyOf(result);
    }

    List<Long> parseIds(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) throw new IllegalStateException("Hacker News story index is invalid");
            List<Long> ids = new ArrayList<>();
            for (JsonNode value : root) {
                if (value.canConvertToLong() && value.asLong() > 0) ids.add(value.asLong());
            }
            if (ids.isEmpty()) throw new IllegalStateException("Hacker News story index is empty");
            return List.copyOf(ids);
        } catch (IOException exception) {
            throw new IllegalStateException("Hacker News story index is invalid", exception);
        }
    }

    InterestBrief parseStory(byte[] body, Instant now) {
        try {
            JsonNode story = objectMapper.readTree(body);
            if (!"story".equals(story.path("type").asText())
                    || story.path("deleted").asBoolean(false)
                    || story.path("dead").asBoolean(false)) return null;
            String title = normalizedTitle(story.path("title").asText(null));
            String url = validExternalUrl(story.path("url").asText(null));
            long epochSecond = story.path("time").asLong(0);
            if (title == null || url == null || epochSecond <= 0) return null;
            Instant publishedAt = Instant.ofEpochSecond(epochSecond);
            if (publishedAt.isAfter(now.plus(Duration.ofMinutes(5)))
                    || publishedAt.isBefore(now.minus(MAX_STORY_AGE))) return null;
            return new InterestBrief(SOURCE_NAME, title, url, publishedAt, now);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private String normalizedTitle(String value) {
        if (value == null) return null;
        String normalized = value.replaceAll("[\\p{Cntrl}\\r\\n]+", " ").replaceAll("\\s+", " ").trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() < 4 || normalized.length() > 240
                || lower.contains("ignore previous") || lower.contains("system prompt")
                || lower.contains("忽略指令") || lower.contains("系统提示")) return null;
        return normalized;
    }

    private String validExternalUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            String lower = host.toLowerCase(Locale.ROOT);
            if (lower.equals("localhost") || lower.endsWith(".local") || lower.endsWith(".internal")
                    || lower.matches("\\d{1,3}(?:\\.\\d{1,3}){3}") || !lower.contains(".")) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private byte[] request(URI uri, int maximumBytes) {
        try {
            HttpRequest request = requestFor(uri);
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Hacker News request failed");
                }
                return bounded(body, maximumBytes);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Hacker News request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Hacker News request interrupted", exception);
        }
    }

    private CompletableFuture<byte[]> requestAsync(URI uri, int maximumBytes) {
        return httpClient.sendAsync(requestFor(uri), HttpResponse.BodyHandlers.ofInputStream())
                .thenApply(response -> {
                    try (InputStream body = response.body()) {
                        if (response.statusCode() < 200 || response.statusCode() >= 300) {
                            throw new IllegalStateException("Hacker News request failed");
                        }
                        return bounded(body, maximumBytes);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Hacker News response invalid", exception);
                    }
                });
    }

    private HttpRequest requestFor(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Accept-Charset", StandardCharsets.UTF_8.name())
                .header("User-Agent", "StackChan-Companion/1")
                .GET()
                .build();
    }

    private byte[] bounded(InputStream input, int maximumBytes) throws IOException {
        byte[] bytes = input.readNBytes(maximumBytes + 1);
        if (bytes.length > maximumBytes) throw new IllegalStateException("Hacker News response too large");
        return bytes;
    }
}
