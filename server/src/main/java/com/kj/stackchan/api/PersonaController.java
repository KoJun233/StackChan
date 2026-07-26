package com.kj.stackchan.api;

import com.kj.stackchan.persona.PersonaProactivity;
import com.kj.stackchan.persona.PersonaReplyLength;
import com.kj.stackchan.persona.PersonaService;
import com.kj.stackchan.persona.PersonaTone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/persona", produces = MediaType.APPLICATION_JSON_VALUE)
public class PersonaController {

    private final PersonaService personaService;

    public PersonaController(PersonaService personaService) {
        this.personaService = personaService;
    }

    @GetMapping
    public PersonaService.PersonaSnapshot get() {
        return personaService.get();
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public PersonaService.PersonaSnapshot save(@Valid @RequestBody PersonaRequest request) {
        return personaService.save(request.toCommand());
    }

    public record PersonaRequest(
            @NotBlank @Size(max = 80) String displayName,
            @NotNull PersonaTone tone,
            @NotNull PersonaReplyLength replyLength,
            @NotNull PersonaProactivity proactivity,
            @Size(max = 2000) String topicBoundaries,
            @Size(max = 2000) String taboos
    ) {
        PersonaService.PersonaCommand toCommand() {
            return new PersonaService.PersonaCommand(
                    displayName, tone, replyLength, proactivity, topicBoundaries, taboos
            );
        }
    }
}
