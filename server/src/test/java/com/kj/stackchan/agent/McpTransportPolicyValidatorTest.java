package com.kj.stackchan.agent;

import com.kj.stackchan.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpTransportPolicyValidatorTest {

    @Test
    void allowsPrivateHttpOnlyInExplicitLanDevelopmentMode() {
        AppProperties appProperties = new AppProperties();
        appProperties.setLanDevelopment(true);
        McpStreamableHttpClientProperties properties = properties("http://192.168.1.30:3000", "/mcp");

        assertThatCode(() -> new McpTransportPolicyValidator(properties, appProperties).run(null))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsHttpMcpConnectionsInProduction() {
        AppProperties appProperties = new AppProperties();
        appProperties.setProduction(true);
        McpStreamableHttpClientProperties properties = properties("http://192.168.1.30:3000", "/mcp");

        assertThatThrownBy(() -> new McpTransportPolicyValidator(properties, appProperties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must use HTTPS");
    }

    @Test
    void rejectsCredentialsEmbeddedInAnHttpsUrl() {
        AppProperties appProperties = new AppProperties();
        McpStreamableHttpClientProperties properties = properties("https://token@example.com", "/mcp");

        assertThatThrownBy(() -> new McpTransportPolicyValidator(properties, appProperties).run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not contain credentials");
    }

    private McpStreamableHttpClientProperties properties(String url, String endpoint) {
        McpStreamableHttpClientProperties properties = new McpStreamableHttpClientProperties();
        properties.getConnections().put(
                "test",
                new McpStreamableHttpClientProperties.ConnectionParameters(url, endpoint)
        );
        return properties;
    }
}
