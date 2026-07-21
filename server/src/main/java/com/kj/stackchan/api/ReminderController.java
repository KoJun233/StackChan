package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.reminder.ReminderService;
import com.kj.stackchan.reminder.ReminderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/reminders", produces = MediaType.APPLICATION_JSON_VALUE)
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public ReminderService.ReminderPage list(
            @RequestParam(defaultValue = "") String content,
            @RequestParam(required = false) ReminderStatus status,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return reminderService.list(content, status, from, limit);
    }

    @GetMapping("/{id}")
    public ReminderService.ReminderSnapshot get(@PathVariable UUID id) {
        return reminderService.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderService.ReminderSnapshot create(@Valid @RequestBody ReminderRequest request) {
        return reminderService.create(request.toCommand());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReminderService.ReminderSnapshot update(@PathVariable UUID id, @Valid @RequestBody ReminderRequest request) {
        return reminderService.update(id, request.toCommand());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        reminderService.delete(id);
    }

    public record ReminderRequest(
            @NotNull UUID deviceId,
            @NotBlank @Size(max = 1000) String content,
            @NotNull Instant scheduledAt,
            @NotBlank @Size(max = 80) String zoneId
    ) {
        ReminderService.ReminderCommand toCommand() {
            return new ReminderService.ReminderCommand(deviceId, content, scheduledAt, zoneId);
        }
    }
}
