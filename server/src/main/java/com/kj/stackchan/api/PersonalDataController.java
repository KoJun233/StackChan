package com.kj.stackchan.api;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.conversation.ConversationMessageSnapshot;
import com.kj.stackchan.conversation.PersonalDataService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/personal-data", produces = MediaType.APPLICATION_JSON_VALUE)
public class PersonalDataController {

    private static final DateTimeFormatter EXPORT_FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC);

    private final PersonalDataService personalDataService;
    private final ObjectMapper objectMapper;

    public PersonalDataController(PersonalDataService personalDataService, ObjectMapper objectMapper) {
        this.personalDataService = personalDataService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/conversations")
    public PersonalDataService.ConversationPage list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) Instant fromTime,
            @RequestParam(required = false) Instant toTime,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return personalDataService.list(filter(query, deviceId, roleId, fromTime, toTime), from, limit);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ConversationMessageSnapshot> messages(@PathVariable UUID conversationId) {
        return personalDataService.messages(conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(@PathVariable UUID conversationId, @PathVariable UUID messageId) {
        personalDataService.deleteMessage(conversationId, messageId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable UUID conversationId) {
        personalDataService.deleteConversation(conversationId);
    }

    @GetMapping("/conversations:export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) Instant fromTime,
            @RequestParam(required = false) Instant toTime,
            @RequestParam(required = false) UUID conversationId
    ) throws JsonProcessingException {
        PersonalDataService.ConversationExport export = personalDataService.export(
                filter(query, deviceId, roleId, fromTime, toTime), conversationId
        );
        byte[] body = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export);
        String fileName = "stackchan-conversations-" + EXPORT_FILE_TIME.format(export.exportedAt()) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(body);
    }

    private PersonalDataService.ConversationFilter filter(
            String query,
            UUID deviceId,
            UUID roleId,
            Instant fromTime,
            Instant toTime
    ) {
        return new PersonalDataService.ConversationFilter(query, deviceId, roleId, fromTime, toTime, null);
    }
}
