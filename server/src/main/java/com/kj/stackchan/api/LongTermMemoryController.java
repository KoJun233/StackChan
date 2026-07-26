package com.kj.stackchan.api;

import java.util.UUID;

import com.kj.stackchan.memory.LongTermMemoryService;
import com.kj.stackchan.memory.MemoryCategory;
import com.kj.stackchan.memory.MemoryConfirmationStatus;
import com.kj.stackchan.memory.MemoryScopeType;
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
@RequestMapping(path = "/api/v1/memories", produces = MediaType.APPLICATION_JSON_VALUE)
public class LongTermMemoryController {

    private final LongTermMemoryService memoryService;

    public LongTermMemoryController(LongTermMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public LongTermMemoryService.MemoryPage list(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) MemoryCategory category,
            @RequestParam(required = false) MemoryConfirmationStatus confirmationStatus,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) MemoryScopeType scopeType,
            @RequestParam(required = false) UUID deviceId,
            @RequestParam(defaultValue = "0") int from,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return memoryService.list(
                query, category, confirmationStatus, enabled, scopeType, deviceId, from, limit
        );
    }

    @GetMapping("/{id}")
    public LongTermMemoryService.MemorySnapshot get(@PathVariable UUID id) {
        return memoryService.get(id);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LongTermMemoryService.MemorySnapshot create(@Valid @RequestBody MemoryRequest request) {
        return memoryService.create(request.toCommand());
    }

    @PostMapping(path = "/suggestions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public LongTermMemoryService.MemorySnapshot suggest(@Valid @RequestBody MemorySuggestionRequest request) {
        return memoryService.suggest(new LongTermMemoryService.MemorySuggestionCommand(
                request.memory().toCommand(), request.sourceDetail()
        ));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LongTermMemoryService.MemorySnapshot update(
            @PathVariable UUID id,
            @Valid @RequestBody MemoryRequest request
    ) {
        return memoryService.update(id, request.toCommand());
    }

    @PostMapping("/{id}:confirm")
    public LongTermMemoryService.MemorySnapshot confirm(@PathVariable UUID id) {
        return memoryService.confirm(id);
    }

    @PostMapping("/{id}:reject")
    public LongTermMemoryService.MemorySnapshot reject(@PathVariable UUID id) {
        return memoryService.reject(id);
    }

    @PutMapping(path = "/{id}/enabled", consumes = MediaType.APPLICATION_JSON_VALUE)
    public LongTermMemoryService.MemorySnapshot setEnabled(
            @PathVariable UUID id,
            @Valid @RequestBody MemoryEnabledRequest request
    ) {
        return memoryService.setEnabled(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        memoryService.delete(id);
    }

    @PostMapping(path = ":clear", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ClearMemoryResponse clear(@Valid @RequestBody ClearMemoryRequest request) {
        return new ClearMemoryResponse(memoryService.clear(request.scopeType(), request.deviceId()));
    }

    public record MemoryRequest(
            @NotNull MemoryScopeType scopeType,
            UUID deviceId,
            @NotNull MemoryCategory category,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String content
    ) {
        LongTermMemoryService.MemoryCommand toCommand() {
            return new LongTermMemoryService.MemoryCommand(scopeType, deviceId, category, title, content);
        }
    }

    public record MemorySuggestionRequest(
            @NotNull @Valid MemoryRequest memory,
            @NotBlank @Size(max = 500) String sourceDetail
    ) {
    }

    public record MemoryEnabledRequest(@NotNull Boolean enabled) {
    }

    public record ClearMemoryRequest(MemoryScopeType scopeType, UUID deviceId) {
    }

    public record ClearMemoryResponse(long deletedCount) {
    }
}
