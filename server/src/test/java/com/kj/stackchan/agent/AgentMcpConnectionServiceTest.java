package com.kj.stackchan.agent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.kj.stackchan.agent.AgentMcpConnectionEntity.AuthType;
import com.kj.stackchan.llm.SecretCipher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMcpConnectionServiceTest {

    private final AgentMcpConnectionRepository repository = mock(AgentMcpConnectionRepository.class);
    private final SecretCipher secretCipher = mock(SecretCipher.class);
    private final McpTransportPolicyValidator policy = mock(McpTransportPolicyValidator.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC);
    private final AgentMcpConnectionService service = new AgentMcpConnectionService(
            repository, secretCipher, policy, clock
    );

    @Test
    void encryptsBearerTokensAndNeverReturnsThem() {
        when(secretCipher.encrypt("private-token"))
                .thenReturn(new SecretCipher.EncryptedSecret("ciphertext", "iv"));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AgentMcpConnectionService.ConnectionSnapshot created = service.create(
                new AgentMcpConnectionService.ConnectionCommand(
                        "my-coffee",
                        "https://mcp.example.com",
                        "/mcp",
                        AuthType.BEARER,
                        "Bearer private-token"
                )
        );

        verify(secretCipher).encrypt("private-token");
        assertThat(created.connectionName()).isEqualTo("my-coffee");
        assertThat(created.authenticationConfigured()).isTrue();
        assertThat(created.toString()).doesNotContain("private-token", "ciphertext", "iv");
    }

    @Test
    void blankTokenKeepsTheExistingEncryptedBearerToken() {
        AgentMcpConnectionEntity existing = new AgentMcpConnectionEntity(
                "my-coffee", "https://old.example.com", "/mcp", AuthType.BEARER,
                "existing-ciphertext", "existing-iv", clock.instant()
        );
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        AgentMcpConnectionService.ConnectionSnapshot updated = service.update(
                existing.getId(),
                new AgentMcpConnectionService.ConnectionCommand(
                        "my-coffee", "https://new.example.com", "/mcp", AuthType.BEARER, ""
                )
        );

        assertThat(updated.url()).isEqualTo("https://new.example.com");
        assertThat(existing.getBearerTokenCiphertext()).isEqualTo("existing-ciphertext");
        assertThat(existing.getBearerTokenIv()).isEqualTo("existing-iv");
    }
}
