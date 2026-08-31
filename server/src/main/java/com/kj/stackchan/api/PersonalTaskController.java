package com.kj.stackchan.api;

import java.time.Instant;
import java.util.UUID;

import com.kj.stackchan.task.PersonalTaskPriority;
import com.kj.stackchan.task.PersonalTaskService;
import com.kj.stackchan.task.PersonalTaskStatus;
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
@RequestMapping(path = "/api/v1/personal-tasks", produces = MediaType.APPLICATION_JSON_VALUE)
public class PersonalTaskController {

    private final PersonalTaskService taskService;

    public PersonalTaskController(PersonalTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public PersonalTaskService.TaskPage list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) PersonalTaskStatus status,
            @RequestParam(required = false) PersonalTaskPriority priority,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return taskService.list(query, status, priority, roleId, from, limit);
    }

    @GetMapping("/{id}")
    public PersonalTaskService.TaskSnapshot get(@PathVariable UUID id) {
        return taskService.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PersonalTaskService.TaskSnapshot create(@Valid @RequestBody PersonalTaskRequest request) {
        return request.roleId() == null ? taskService.create(request.toCommand())
                : taskService.create(request.roleId(), request.toCommand());
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PersonalTaskService.TaskSnapshot update(
            @PathVariable UUID id,
            @Valid @RequestBody PersonalTaskRequest request
    ) {
        return taskService.update(id, request.roleId(), request.toCommand());
    }

    @PostMapping("/{id}:complete")
    public PersonalTaskService.TaskSnapshot complete(@PathVariable UUID id) {
        return taskService.complete(id);
    }

    @PostMapping("/{id}:reopen")
    public PersonalTaskService.TaskSnapshot reopen(@PathVariable UUID id) {
        return taskService.reopen(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        taskService.delete(id);
    }

    public record PersonalTaskRequest(
            @NotNull UUID deviceId,
            UUID roleId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String notes,
            PersonalTaskPriority priority,
            Instant dueAt,
            @NotBlank @Size(max = 80) String zoneId
    ) {
        PersonalTaskService.TaskCommand toCommand() {
            return new PersonalTaskService.TaskCommand(deviceId, title, notes, priority, dueAt, zoneId);
        }
    }
}
