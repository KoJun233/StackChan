package com.kj.stackchan.notification;

import java.time.Duration;
import java.util.Map;

import com.kj.stackchan.device.DeviceEntity;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.device.DeviceTokenService;
import com.kj.stackchan.reminder.ReminderRepository;
import com.kj.stackchan.reminder.ReminderSource;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "companion.device-transport-enabled=false",
                "spring.ai.mcp.server.protocol=STREAMABLE",
                "spring.ai.mcp.server.capabilities.resource=false",
                "spring.ai.mcp.server.capabilities.prompt=false",
                "spring.ai.mcp.server.capabilities.completion=false",
                "spring.ai.mcp.server.streamable-http.mcp-endpoint=/mcp/notifications"
        }
)
@Testcontainers
class NotificationMcpTransportTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("postgres@sha256:c2d42a104eb6b37b286a2d9c5cf83f349de4d6516d513d00a2bd9610e2c2e5e4")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private NotificationIntegrationRepository integrationRepository;

    @Autowired
    private NotificationIntegrationTokenRepository tokenRepository;

    @Autowired
    private NotificationIntegrationService integrationService;

    @Autowired
    private ReminderRepository reminderRepository;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private DeviceCommandGateway deviceCommandGateway;

    @BeforeEach
    void clearNotificationData() {
        reminderRepository.deleteAllInBatch();
        tokenRepository.deleteAllInBatch();
        integrationRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void exposesOnlyNotificationToolsAndQueuesThroughTheAuthenticatedStreamableEndpoint() {
        DeviceEntity device = deviceRepository.save(new DeviceEntity("mcp-transport-device", "1.0.0"));
        var integration = integrationService.create(new NotificationIntegrationService.IntegrationCommand(
                "Codex", device.getId(), true
        ));
        String token = integrationService.issueToken(integration.id(), null).token();

        var transport = HttpClientStreamableHttpTransport.builder("http://127.0.0.1:" + port)
                .endpoint("/mcp/notifications")
                .customizeRequest(request -> request.header("Authorization", "Bearer " + token))
                .build();

        try (var client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .initializationTimeout(Duration.ofSeconds(10))
                .build()) {
            client.initialize();

            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("push_notification", "get_notification_status");

            var result = client.callTool(new McpSchema.CallToolRequest("push_notification", Map.of(
                    "content", "MCP transport verification",
                    "idempotencyKey", "transport-check-1",
                    "expiresInSeconds", 3600
            )));

            assertThat(Boolean.TRUE.equals(result.isError())).isFalse();
            var queued = reminderRepository.findAll();
            assertThat(queued).singleElement().satisfies(reminder -> {
                assertThat(reminder.getSource()).isEqualTo(ReminderSource.EXTERNAL);
                assertThat(reminder.getNotificationIntegrationId()).isEqualTo(integration.id());
                assertThat(reminder.getContent()).isEqualTo("MCP transport verification");
            });

            var statusResult = client.callTool(new McpSchema.CallToolRequest("get_notification_status", Map.of(
                    "notificationId", queued.getFirst().getId().toString()
            )));
            assertThat(Boolean.TRUE.equals(statusResult.isError())).isFalse();
        }
    }
}
