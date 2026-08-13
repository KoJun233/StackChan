package com.kj.stackchan.api;

import java.util.List;
import java.util.UUID;
import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaTone;
import com.kj.stackchan.role.CompanionRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/roles", produces = MediaType.APPLICATION_JSON_VALUE)
public class RoleController {
    private final CompanionRoleService roleService;
    public RoleController(CompanionRoleService roleService) { this.roleService = roleService; }
    @GetMapping public List<CompanionRoleService.RoleSnapshot> list() { return roleService.list(); }
    @GetMapping("/{id}") public CompanionRoleService.RoleSnapshot get(@PathVariable UUID id) { return roleService.get(id); }
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public CompanionRoleService.RoleSnapshot create(@Valid @RequestBody RoleRequest request) {
        return roleService.create(request.toCommand());
    }
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CompanionRoleService.RoleSnapshot update(@PathVariable UUID id, @Valid @RequestBody RoleRequest request) {
        return roleService.update(id, request.toCommand());
    }
    @PostMapping("/{id}:archive") public CompanionRoleService.RoleSnapshot archive(@PathVariable UUID id) {
        return roleService.archive(id);
    }
    @PostMapping("/{id}:restore") public CompanionRoleService.RoleSnapshot restore(@PathVariable UUID id) {
        return roleService.restore(id);
    }
    public record RoleRequest(@NotBlank @Size(max = 80) String name, @NotNull PersonaTone tone,
                              @NotNull PersonaReplyLength replyLength, @NotNull PersonaProactivity proactivity,
                              @Size(max = 4000) String backgroundInstructions,
                              @Size(max = 2000) String topicBoundaries, @Size(max = 2000) String taboos) {
        CompanionRoleService.RoleCommand toCommand() {
            return new CompanionRoleService.RoleCommand(name, tone, replyLength, proactivity,
                    backgroundInstructions, topicBoundaries, taboos);
        }
    }
}
