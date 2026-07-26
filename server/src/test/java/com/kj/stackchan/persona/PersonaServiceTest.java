package com.kj.stackchan.persona;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonaServiceTest {

    @Mock
    private PersonaSettingsRepository repository;

    @Test
    void savesStructuredPersonaWithNormalizedText() {
        Instant now = Instant.parse("2026-07-26T12:00:00Z");
        when(repository.findById(PersonaSettingsEntity.CURRENT_SETTINGS_ID)).thenReturn(Optional.empty());
        when(repository.save(any(PersonaSettingsEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PersonaService service = new PersonaService(repository, Clock.fixed(now, ZoneOffset.UTC));

        PersonaService.PersonaSnapshot result = service.save(new PersonaService.PersonaCommand(
                "  小栈  ",
                PersonaTone.CALM,
                PersonaReplyLength.SHORT,
                PersonaProactivity.RESERVED,
                "  不主动讨论工作  ",
                "  不使用挖苦语气  "
        ));

        assertThat(result.displayName()).isEqualTo("小栈");
        assertThat(result.topicBoundaries()).isEqualTo("不主动讨论工作");
        assertThat(result.taboos()).isEqualTo("不使用挖苦语气");
        assertThat(result.updatedAt()).isEqualTo(now);
    }
}
