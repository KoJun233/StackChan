package com.kj.stackchan.calendar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Component
public class ICloudCalDavClient {

    static final URI DEFAULT_BASE_URI = URI.create("https://caldav.icloud.com/");
    static final URI CHINA_MAINLAND_BASE_URI = URI.create("https://caldav.icloud.com.cn/");
    private static final String DAV_NAMESPACE = "DAV:";
    private static final String CALDAV_NAMESPACE = "urn:ietf:params:xml:ns:caldav";
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_CALENDARS = 64;
    private static final int MAX_EVENTS = 500;
    private static final Pattern APPLE_CALDAV_SHARD_HOST =
            Pattern.compile("^p\\d+-caldav\\.icloud\\.com(?:\\.cn)?$");
    private static final DateTimeFormatter CALDAV_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter ICAL_LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter ICAL_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final HttpClient httpClient;
    private final List<URI> discoveryBaseUris;
    private final boolean productionTargets;

    public ICloudCalDavClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build(),
                List.of(DEFAULT_BASE_URI, CHINA_MAINLAND_BASE_URI),
                true
        );
    }

    ICloudCalDavClient(HttpClient httpClient, URI baseUri) {
        this(httpClient, List.of(baseUri), false);
    }

    ICloudCalDavClient(HttpClient httpClient, List<URI> discoveryBaseUris) {
        this(httpClient, discoveryBaseUris, false);
    }

    private ICloudCalDavClient(HttpClient httpClient, List<URI> discoveryBaseUris, boolean productionTargets) {
        this.httpClient = httpClient;
        if (discoveryBaseUris == null || discoveryBaseUris.isEmpty()
                || discoveryBaseUris.stream().anyMatch(this::invalidBaseUri)) {
            throw new IllegalArgumentException("CalDAV base URI is invalid");
        }
        this.discoveryBaseUris = discoveryBaseUris.stream().map(URI::normalize).toList();
        this.productionTargets = productionTargets;
    }

    public DiscoveryResult discover(String accountEmail, String appSpecificPassword) {
        ICloudCalendarUnavailableException authenticationFailure = null;
        for (URI baseUri : discoveryBaseUris) {
            try {
                return discover(baseUri, accountEmail, appSpecificPassword);
            } catch (ICloudCalendarUnavailableException exception) {
                if (exception.getFailureCode() != ICloudCalendarFailureCode.AUTHENTICATION_FAILED) {
                    throw exception;
                }
                authenticationFailure = exception;
            }
        }
        throw authenticationFailure == null
                ? unavailable(ICloudCalendarFailureCode.DISCOVERY_FAILED, "iCloud CalDAV endpoint is unavailable")
                : authenticationFailure;
    }

    private DiscoveryResult discover(URI baseUri, String accountEmail, String appSpecificPassword) {
        String principalBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:"><d:prop><d:current-user-principal/></d:prop></d:propfind>
                """;
        Response principalResponse = request("PROPFIND", baseUri, principalBody, accountEmail, appSpecificPassword, "0");
        URI principalUri = resolveHref(principalResponse.uri(), requiredHref(
                parseXml(principalResponse.body()), DAV_NAMESPACE, "current-user-principal"
        ));

        String homeBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:prop><c:calendar-home-set/></d:prop>
                </d:propfind>
                """;
        Response homeResponse = request("PROPFIND", principalUri, homeBody, accountEmail, appSpecificPassword, "0");
        URI calendarHomeUri = resolveHref(homeResponse.uri(), requiredHref(
                parseXml(homeResponse.body()), CALDAV_NAMESPACE, "calendar-home-set"
        ));

        String calendarsBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:prop><d:displayname/><d:resourcetype/><c:supported-calendar-component-set/></d:prop>
                </d:propfind>
                """;
        Response calendarsResponse = request(
                "PROPFIND", calendarHomeUri, calendarsBody, accountEmail, appSpecificPassword, "1"
        );
        List<DiscoveredCalendar> calendars = parseCalendars(
                parseXml(calendarsResponse.body()), calendarsResponse.uri()
        );
        return new DiscoveryResult(principalUri.toString(), calendarHomeUri.toString(), calendars);
    }

    public List<CalendarEvent> queryEvents(
            String calendarHref,
            String accountEmail,
            String appSpecificPassword,
            Instant from,
            Instant to,
            ZoneId fallbackZone
    ) {
        URI calendarUri = normalizeTarget(URI.create(calendarHref));
        String start = CALDAV_TIMESTAMP.format(from);
        String end = CALDAV_TIMESTAMP.format(to);
        String reportBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <c:calendar-query xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
                  <d:prop><d:getetag/><c:calendar-data><c:expand start="%s" end="%s"/></c:calendar-data></d:prop>
                  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">
                    <c:time-range start="%s" end="%s"/>
                  </c:comp-filter></c:comp-filter></c:filter>
                </c:calendar-query>
                """.formatted(start, end, start, end);
        Response response = request(
                "REPORT", calendarUri, reportBody, accountEmail, appSpecificPassword, "1"
        );
        List<CalendarEvent> events = new ArrayList<>();
        NodeList calendarData = parseXml(response.body()).getElementsByTagNameNS(CALDAV_NAMESPACE, "calendar-data");
        for (int index = 0; index < calendarData.getLength(); index++) {
            events.addAll(parseCalendarData(calendarData.item(index).getTextContent(), from, to, fallbackZone));
            if (events.size() > MAX_EVENTS) {
                throw unavailable(ICloudCalendarFailureCode.RESPONSE_TOO_LARGE, "Too many iCloud calendar events");
            }
        }
        return List.copyOf(events);
    }

    private Response request(
            String method,
            URI initialUri,
            String body,
            String accountEmail,
            String appSpecificPassword,
            String depth
    ) {
        URI uri = normalizeTarget(initialUri);
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (accountEmail + ":" + appSpecificPassword).getBytes(StandardCharsets.UTF_8)
        );
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", authorization)
                    .header("Accept", "application/xml, text/xml")
                    .header("Content-Type", "application/xml; charset=utf-8")
                    .header("Depth", depth)
                    .header("User-Agent", "StackChan-Calendar/1")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            try {
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status == 301 || status == 302 || status == 307 || status == 308) {
                    closeQuietly(response.body());
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> unavailable(
                                    ICloudCalendarFailureCode.INVALID_RESPONSE,
                                    "iCloud CalDAV redirect is missing a target"
                            ));
                    uri = normalizeTarget(uri.resolve(location));
                    continue;
                }
                byte[] responseBody = readBounded(response.body());
                if (status == 401 || status == 403) {
                    throw unavailable(
                            ICloudCalendarFailureCode.AUTHENTICATION_FAILED,
                            "iCloud calendar authentication failed"
                    );
                }
                if (status < 200 || status >= 300) {
                    throw unavailable(
                            ICloudCalendarFailureCode.DISCOVERY_FAILED,
                            "iCloud CalDAV returned an unexpected status"
                    );
                }
                return new Response(uri, responseBody);
            } catch (ICloudCalendarUnavailableException exception) {
                throw exception;
            } catch (IOException exception) {
                throw unavailable(ICloudCalendarFailureCode.DISCOVERY_FAILED, "iCloud CalDAV request failed", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw unavailable(ICloudCalendarFailureCode.DISCOVERY_FAILED, "iCloud CalDAV request interrupted", exception);
            }
        }
        throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "Too many iCloud CalDAV redirects");
    }

    private byte[] readBounded(InputStream inputStream) throws IOException {
        try (InputStream input = inputStream) {
            byte[] bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (bytes.length > MAX_RESPONSE_BYTES) {
                throw unavailable(ICloudCalendarFailureCode.RESPONSE_TOO_LARGE, "iCloud CalDAV response is too large");
            }
            return bytes;
        }
    }

    private Document parseXml(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        } catch (SAXException | IOException | RuntimeException | javax.xml.parsers.ParserConfigurationException exception) {
            throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "iCloud CalDAV returned invalid XML", exception);
        }
    }

    private String requiredHref(Document document, String namespace, String propertyName) {
        NodeList properties = document.getElementsByTagNameNS(namespace, propertyName);
        for (int index = 0; index < properties.getLength(); index++) {
            Element property = (Element) properties.item(index);
            NodeList hrefs = property.getElementsByTagNameNS(DAV_NAMESPACE, "href");
            if (hrefs.getLength() > 0 && !hrefs.item(0).getTextContent().isBlank()) {
                return hrefs.item(0).getTextContent().trim();
            }
        }
        throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "iCloud CalDAV discovery property is missing");
    }

    private List<DiscoveredCalendar> parseCalendars(Document document, URI responseUri) {
        List<DiscoveredCalendar> calendars = new ArrayList<>();
        NodeList responses = document.getElementsByTagNameNS(DAV_NAMESPACE, "response");
        for (int index = 0; index < responses.getLength(); index++) {
            Element response = (Element) responses.item(index);
            if (response.getElementsByTagNameNS(CALDAV_NAMESPACE, "calendar").getLength() == 0) {
                continue;
            }
            NodeList hrefs = response.getElementsByTagNameNS(DAV_NAMESPACE, "href");
            if (hrefs.getLength() == 0) {
                continue;
            }
            URI href = resolveHref(responseUri, hrefs.item(0).getTextContent().trim());
            NodeList displayNames = response.getElementsByTagNameNS(DAV_NAMESPACE, "displayname");
            String displayName = displayNames.getLength() == 0
                    ? "未命名日历"
                    : normalizeText(displayNames.item(0).getTextContent(), 255, "未命名日历");
            calendars.add(new DiscoveredCalendar(href.toString(), displayName));
            if (calendars.size() > MAX_CALENDARS) {
                throw unavailable(ICloudCalendarFailureCode.RESPONSE_TOO_LARGE, "Too many iCloud calendars");
            }
        }
        return List.copyOf(calendars);
    }

    private List<CalendarEvent> parseCalendarData(String calendarData, Instant from, Instant to, ZoneId fallbackZone) {
        String unfolded = calendarData.replaceAll("\\r?\\n[ \\t]", "");
        String[] lines = unfolded.split("\\r?\\n");
        List<CalendarEvent> events = new ArrayList<>();
        Map<String, PropertyValue> properties = null;
        for (String line : lines) {
            if ("BEGIN:VEVENT".equalsIgnoreCase(line.trim())) {
                properties = new HashMap<>();
                continue;
            }
            if ("END:VEVENT".equalsIgnoreCase(line.trim())) {
                if (properties != null) {
                    parseEvent(properties, from, to, fallbackZone).ifPresent(events::add);
                }
                properties = null;
                continue;
            }
            if (properties == null) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String descriptor = line.substring(0, separator);
            String[] descriptorParts = descriptor.split(";");
            String name = descriptorParts[0].toUpperCase(Locale.ROOT);
            Map<String, String> parameters = new HashMap<>();
            for (int part = 1; part < descriptorParts.length; part++) {
                int equals = descriptorParts[part].indexOf('=');
                if (equals > 0) {
                    parameters.put(
                            descriptorParts[part].substring(0, equals).toUpperCase(Locale.ROOT),
                            descriptorParts[part].substring(equals + 1).replace("\"", "")
                    );
                }
            }
            properties.putIfAbsent(name, new PropertyValue(line.substring(separator + 1), parameters));
        }
        return events;
    }

    private Optional<CalendarEvent> parseEvent(
            Map<String, PropertyValue> properties,
            Instant from,
            Instant to,
            ZoneId fallbackZone
    ) {
        if ("CANCELLED".equalsIgnoreCase(value(properties, "STATUS"))) {
            return Optional.empty();
        }
        PropertyValue startProperty = properties.get("DTSTART");
        if (startProperty == null) {
            return Optional.empty();
        }
        ParsedDate start = parseDate(startProperty, fallbackZone);
        PropertyValue endProperty = properties.get("DTEND");
        ParsedDate end = endProperty == null
                ? new ParsedDate(start.instant().plus(start.allDay() ? Duration.ofDays(1) : Duration.ofHours(1)), start.allDay())
                : parseDate(endProperty, fallbackZone);
        if (!end.instant().isAfter(from) || !start.instant().isBefore(to)) {
            return Optional.empty();
        }
        String uid = normalizeText(value(properties, "UID"), 512, start.instant().toString());
        boolean privateEvent = "PRIVATE".equalsIgnoreCase(value(properties, "CLASS"))
                || "CONFIDENTIAL".equalsIgnoreCase(value(properties, "CLASS"));
        String title = privateEvent
                ? "私人日程"
                : normalizeText(unescape(value(properties, "SUMMARY")), 512, "未命名日程");
        String location = privateEvent
                ? null
                : normalizeNullableText(unescape(value(properties, "LOCATION")), 512);
        boolean busy = !"TRANSPARENT".equalsIgnoreCase(value(properties, "TRANSP"));
        return Optional.of(new CalendarEvent(
                uid, start.instant(), end.instant(), start.allDay(), busy, privateEvent, title, location
        ));
    }

    private ParsedDate parseDate(PropertyValue property, ZoneId fallbackZone) {
        String value = property.value().trim();
        try {
            if ("DATE".equalsIgnoreCase(property.parameters().get("VALUE")) || value.length() == 8) {
                return new ParsedDate(LocalDate.parse(value, ICAL_DATE).atStartOfDay(fallbackZone).toInstant(), true);
            }
            if (value.endsWith("Z")) {
                return new ParsedDate(
                        LocalDateTime.parse(value.substring(0, value.length() - 1), ICAL_LOCAL_DATE_TIME)
                                .toInstant(ZoneOffset.UTC),
                        false
                );
            }
            ZoneId zone = fallbackZone;
            String timezone = property.parameters().get("TZID");
            if (timezone != null) {
                try {
                    zone = ZoneId.of(timezone);
                } catch (RuntimeException ignored) {
                    zone = fallbackZone;
                }
            }
            return new ParsedDate(LocalDateTime.parse(value, ICAL_LOCAL_DATE_TIME).atZone(zone).toInstant(), false);
        } catch (DateTimeParseException exception) {
            throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "iCloud calendar event time is invalid", exception);
        }
    }

    private String value(Map<String, PropertyValue> properties, String name) {
        PropertyValue value = properties.get(name);
        return value == null ? "" : value.value();
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n")
                .replace("\\N", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    private String normalizeText(String value, int maximumLength, String fallback) {
        String normalized = value == null ? "" : value.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }

    private String normalizeNullableText(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return normalizeText(value, maximumLength, "");
    }

    private URI resolveHref(URI base, String href) {
        return normalizeTarget(base.resolve(href));
    }

    private URI normalizeTarget(URI uri) {
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "iCloud CalDAV target is invalid");
        }
        if (productionTargets) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !isAppleCalDavHost(host)) {
                throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "iCloud CalDAV redirected outside Apple");
            }
        } else if (discoveryBaseUris.stream().noneMatch(baseUri -> sameOrigin(uri, baseUri))) {
            throw unavailable(ICloudCalendarFailureCode.INVALID_RESPONSE, "CalDAV test target changed origin");
        }
        return uri.normalize();
    }

    private boolean invalidBaseUri(URI baseUri) {
        return baseUri == null || !baseUri.isAbsolute() || baseUri.getHost() == null
                || baseUri.getUserInfo() != null || baseUri.getFragment() != null;
    }

    static boolean isAppleCalDavHost(String host) {
        return host.equals("caldav.icloud.com")
                || host.equals("caldav.icloud.com.cn")
                || APPLE_CALDAV_SHARD_HOST.matcher(host).matches();
    }

    private boolean sameOrigin(URI uri, URI baseUri) {
        return uri.getScheme().equalsIgnoreCase(baseUri.getScheme())
                && uri.getHost().equalsIgnoreCase(baseUri.getHost())
                && effectivePort(uri) == effectivePort(baseUri);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // Redirect bodies do not contain application data.
        }
    }

    private ICloudCalendarUnavailableException unavailable(ICloudCalendarFailureCode code, String message) {
        return new ICloudCalendarUnavailableException(code, message);
    }

    private ICloudCalendarUnavailableException unavailable(
            ICloudCalendarFailureCode code,
            String message,
            Throwable cause
    ) {
        return new ICloudCalendarUnavailableException(code, message, cause);
    }

    public record DiscoveryResult(
            String principalUrl,
            String calendarHomeUrl,
            List<DiscoveredCalendar> calendars
    ) { }

    public record DiscoveredCalendar(String href, String displayName) { }

    public record CalendarEvent(
            String uid,
            Instant startsAt,
            Instant endsAt,
            boolean allDay,
            boolean busy,
            boolean privateEvent,
            String title,
            String location
    ) { }

    private record Response(URI uri, byte[] body) { }
    private record PropertyValue(String value, Map<String, String> parameters) { }
    private record ParsedDate(Instant instant, boolean allDay) { }
}
