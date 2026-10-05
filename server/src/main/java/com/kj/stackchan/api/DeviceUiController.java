package com.kj.stackchan.api;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.device.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/device/ui")
public class DeviceUiController {
    private final DeviceHttpAuthenticator authenticator;
    private final DeviceUiService ui;
    private final ObjectMapper mapper;
    public DeviceUiController(DeviceHttpAuthenticator authenticator, DeviceUiService ui, ObjectMapper mapper) {
        this.authenticator = authenticator; this.ui = ui; this.mapper = mapper;
    }

    @ModelAttribute
    public void noStore(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

    @GetMapping("/state")
    public ResponseEntity<byte[]> state(HttpServletRequest request) {
        return json(ui.state(authenticator.authenticate(request).deviceId()));
    }
    @GetMapping("/confirmations/{id}")
    public ResponseEntity<byte[]> card(HttpServletRequest request, @PathVariable UUID id) {
        return json(ui.card(authenticator.authenticate(request).deviceId(), id));
    }
    @PostMapping(value = "/confirmations/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> act(HttpServletRequest request, @PathVariable UUID id, @RequestBody String body) {
        UUID deviceId = authenticator.authenticate(request).deviceId();
        JsonNode value = parse(body);
        if (value.size() != 1 || !value.path("action").isTextual()
                || !Set.of("CONFIRM", "CANCEL").contains(value.path("action").asText())) throw invalid();
        return json(ui.act(deviceId, id, value.path("action").asText()));
    }
    @PostMapping("/confirmations/{id}/shown")
    public ResponseEntity<byte[]> shown(HttpServletRequest request, @PathVariable UUID id,
                                        @RequestBody(required = false) String body) {
        UUID deviceId = authenticator.authenticate(request).deviceId();
        if (body != null && !body.isBlank()) throw invalid();
        ui.shown(deviceId, id);
        return json(Map.of("proposal_id", id, "status", "SHOWN"));
    }
    @PostMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> setting(HttpServletRequest request, @RequestBody String body) {
        UUID deviceId = authenticator.authenticate(request).deviceId();
        JsonNode root = parse(body);
        if (root.size() != 1) throw invalid();
        String field = root.fieldNames().next();
        JsonNode value = root.get(field);
        Object parsed = switch (field) {
            case "volume_percent" -> {
                if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0 || value.intValue() > 100)
                    throw invalid();
                yield value.intValue();
            }
            case "night_mode", "quiet_today" -> { if (!value.isBoolean()) throw invalid(); yield value.booleanValue(); }
            case "workday_action" -> {
                if (!value.isTextual() || !Set.of("START", "STOP").contains(value.asText())) throw invalid();
                yield value.asText();
            }
            case "rest_action" -> {
                if (!value.isTextual() || !Set.of("START_REST", "SNOOZE", "SKIP_FOR_DAY").contains(value.asText())) throw invalid();
                yield value.asText();
            }
            case "role_id" -> {
                if (!value.isTextual()) throw invalid();
                try {
                    UUID id = UUID.fromString(value.asText());
                    if (!id.toString().equals(value.asText())) throw invalid();
                    yield id;
                } catch (IllegalArgumentException exception) { throw invalid(); }
            }
            default -> throw invalid();
        };
        return json(Map.of("result", "SAVED", "state", ui.setting(deviceId, field, parsed)));
    }

    private JsonNode parse(String body) {
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > 512) throw invalid();
        try {
            JsonNode value = mapper.reader().with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
            if (value == null || !value.isObject()) throw invalid();
            return value;
        } catch (java.io.IOException exception) { throw invalid(); }
    }
    private ResponseEntity<byte[]> json(Object value) {
        try {
            byte[] encoded = mapper.writeValueAsBytes(value);
            if (encoded.length > 8192) throw new DeviceUiException(HttpStatus.CONFLICT, "ui_response_too_large");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(DeviceApiExceptionHandler.JSON_UTF8)
                    .contentLength(encoded.length).body(encoded);
        } catch (java.io.IOException exception) {
            throw new DeviceUiException(HttpStatus.SERVICE_UNAVAILABLE, "device_ui_unavailable");
        }
    }
    private static DeviceUiException invalid() { return new DeviceUiException(HttpStatus.BAD_REQUEST, "invalid_ui_request"); }
}
