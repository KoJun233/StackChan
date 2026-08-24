package com.kj.stackchan.calendar;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ICloudCalDavClientTest {

    private HttpServer server;
    private HttpServer fallbackServer;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (fallbackServer != null) {
            fallbackServer.stop(0);
        }
    }

    @Test
    void fallsBackToChinaMainlandEndpointAfterAuthenticationFailure() throws Exception {
        List<String> primaryMethods = new ArrayList<>();
        List<String> fallbackMethods = new ArrayList<>();
        List<String> authorization = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            primaryMethods.add(exchange.getRequestMethod());
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        fallbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fallbackServer.createContext("/", exchange -> respond(exchange, fallbackMethods, authorization));
        fallbackServer.start();
        URI globalBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        URI chinaBaseUri = URI.create("http://127.0.0.1:" + fallbackServer.getAddress().getPort() + "/");
        var client = new ICloudCalDavClient(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                List.of(globalBaseUri, chinaBaseUri)
        );

        var discovery = client.discover("me@icloud.com", "app-password");
        var events = client.queryEvents(
                discovery.calendars().getFirst().href(), "me@icloud.com", "app-password",
                Instant.parse("2026-08-25T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertThat(primaryMethods).containsExactly("PROPFIND");
        assertThat(fallbackMethods).containsExactly("PROPFIND", "PROPFIND", "PROPFIND", "REPORT");
        assertThat(authorization).allMatch(value -> value.startsWith("Basic "));
        assertThat(discovery.calendars()).hasSize(1);
        assertThat(events).hasSize(2);
    }

    @Test
    void doesNotTryAnotherRegionAfterNonAuthenticationFailure() throws Exception {
        List<String> fallbackMethods = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        fallbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fallbackServer.createContext("/", exchange -> {
            fallbackMethods.add(exchange.getRequestMethod());
            exchange.sendResponseHeaders(207, -1);
            exchange.close();
        });
        fallbackServer.start();
        URI globalBaseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        URI chinaBaseUri = URI.create("http://127.0.0.1:" + fallbackServer.getAddress().getPort() + "/");
        var client = new ICloudCalDavClient(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                List.of(globalBaseUri, chinaBaseUri)
        );

        assertThatThrownBy(() -> client.discover("me@icloud.com", "app-password"))
                .isInstanceOfSatisfying(ICloudCalendarUnavailableException.class, exception ->
                        assertThat(exception.getFailureCode()).isEqualTo(ICloudCalendarFailureCode.DISCOVERY_FAILED)
                );
        assertThat(fallbackMethods).isEmpty();
    }

    @Test
    void allowsOnlyAppleCalDavEntryAndNumberedShardHosts() {
        assertThat(ICloudCalDavClient.isAppleCalDavHost("caldav.icloud.com")).isTrue();
        assertThat(ICloudCalDavClient.isAppleCalDavHost("caldav.icloud.com.cn")).isTrue();
        assertThat(ICloudCalDavClient.isAppleCalDavHost("p01-caldav.icloud.com")).isTrue();
        assertThat(ICloudCalDavClient.isAppleCalDavHost("p220-caldav.icloud.com.cn")).isTrue();

        assertThat(ICloudCalDavClient.isAppleCalDavHost("caldav.icloud.com.evil.example")).isFalse();
        assertThat(ICloudCalDavClient.isAppleCalDavHost("p220-caldav.icloud.com.cn.evil.example")).isFalse();
        assertThat(ICloudCalDavClient.isAppleCalDavHost("calendarws.icloud.com")).isFalse();
        assertThat(ICloudCalDavClient.isAppleCalDavHost("p-caldav.icloud.com.cn")).isFalse();
    }

    @Test
    void discoversAndReadsExpandedEventsWithoutWriteMethods() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> authorization = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> respond(exchange, methods, authorization));
        server.start();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        var client = new ICloudCalDavClient(
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(), baseUri
        );

        var discovery = client.discover("me@icloud.com", "app-password");
        var events = client.queryEvents(
                discovery.calendars().getFirst().href(), "me@icloud.com", "app-password",
                Instant.parse("2026-08-25T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        assertThat(discovery.calendars()).singleElement().satisfies(calendar -> {
            assertThat(calendar.displayName()).isEqualTo("工作");
            assertThat(calendar.href()).endsWith("/calendars/work/");
        });
        assertThat(methods).containsExactly("PROPFIND", "PROPFIND", "PROPFIND", "REPORT");
        assertThat(methods).doesNotContain("PUT", "POST", "DELETE", "MKCALENDAR");
        assertThat(authorization).allMatch(value -> value.startsWith("Basic "));
        assertThat(events).hasSize(2);
        assertThat(events.get(0).title()).isEqualTo("公开会议");
        assertThat(events.get(0).location()).isEqualTo("会议室 A");
        assertThat(events.get(1).privateEvent()).isTrue();
        assertThat(events.get(1).title()).isEqualTo("私人日程");
        assertThat(events.get(1).location()).isNull();
    }

    private void respond(HttpExchange exchange, List<String> methods, List<String> authorization) throws IOException {
        methods.add(exchange.getRequestMethod());
        authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath();
        String body;
        if ("/".equals(path)) {
            body = """
                    <?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
                    <d:response><d:propstat><d:prop><d:current-user-principal><d:href>/principal/</d:href></d:current-user-principal></d:prop></d:propstat></d:response>
                    </d:multistatus>
                    """;
        } else if ("/principal/".equals(path)) {
            body = """
                    <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                    <d:response><d:propstat><d:prop><c:calendar-home-set><d:href>/calendars/</d:href></c:calendar-home-set></d:prop></d:propstat></d:response>
                    </d:multistatus>
                    """;
        } else if ("/calendars/".equals(path)) {
            body = """
                    <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                    <d:response><d:href>/calendars/work/</d:href><d:propstat><d:prop><d:displayname>工作</d:displayname><d:resourcetype><d:collection/><c:calendar/></d:resourcetype></d:prop></d:propstat></d:response>
                    </d:multistatus>
                    """;
        } else {
            body = """
                    <?xml version="1.0"?><d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                    <d:response><d:propstat><d:prop><c:calendar-data>BEGIN:VCALENDAR
                    BEGIN:VEVENT
                    UID:public-1
                    DTSTART:20260825T090000Z
                    DTEND:20260825T100000Z
                    SUMMARY:公开会议
                    LOCATION:会议室 A
                    END:VEVENT
                    BEGIN:VEVENT
                    UID:private-1
                    DTSTART:20260826T090000Z
                    DTEND:20260826T100000Z
                    SUMMARY:不可泄露
                    LOCATION:秘密地点
                    CLASS:PRIVATE
                    END:VEVENT
                    END:VCALENDAR</c:calendar-data></d:prop></d:propstat></d:response>
                    </d:multistatus>
                    """;
        }
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/xml; charset=utf-8");
        exchange.sendResponseHeaders(207, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
