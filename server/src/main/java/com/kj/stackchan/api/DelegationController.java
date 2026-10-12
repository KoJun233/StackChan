package com.kj.stackchan.api;

import java.util.UUID;
import com.kj.stackchan.delegation.DelegationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path="/api/v1/delegations",produces=MediaType.APPLICATION_JSON_VALUE)
public class DelegationController {
    private final DelegationService service;
    public DelegationController(DelegationService service) { this.service=service; }
    @GetMapping public DelegationService.TaskPage list(@RequestParam(required=false) UUID deviceId,
            @RequestParam(required=false) UUID roleId,@RequestParam(defaultValue="0") int from,@RequestParam(defaultValue="20") int limit) {
        return service.list(deviceId,roleId,from,limit);
    }
    @GetMapping("/{id}") public DelegationService.TaskSnapshot get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping(consumes=MediaType.APPLICATION_JSON_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public DelegationService.TaskSnapshot create(@RequestBody DelegationService.TaskInput input) { return service.create(input); }
    @PutMapping(path="/{id}",consumes=MediaType.APPLICATION_JSON_VALUE)
    public DelegationService.TaskSnapshot update(@PathVariable UUID id,@RequestBody DelegationService.TaskInput input) { return service.update(id,input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id) { service.delete(id); }
    @PostMapping("/{id}:pause") public DelegationService.TaskSnapshot pause(@PathVariable UUID id) { return service.setEnabled(id,false); }
    @PostMapping("/{id}:resume") public DelegationService.TaskSnapshot resume(@PathVariable UUID id) { return service.setEnabled(id,true); }
    @PostMapping("/{id}:run") public DelegationService.RunSnapshot run(@PathVariable UUID id) { return service.run(id); }
    @GetMapping("/{id}/runs") public DelegationService.RunPage runs(@PathVariable UUID id,
            @RequestParam(defaultValue="0") int from,@RequestParam(defaultValue="20") int limit) { return service.runs(id,from,limit); }
}
