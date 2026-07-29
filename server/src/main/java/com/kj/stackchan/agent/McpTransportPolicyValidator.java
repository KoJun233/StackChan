package com.kj.stackchan.agent;

import java.net.URI;
import java.util.Locale;

import com.kj.stackchan.config.AppProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStreamableHttpClientProperties;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import io.modelcontextprotocol.client.McpClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class McpTransportPolicyValidator implements ApplicationRunner, McpSyncClientCustomizer {

    private final McpStreamableHttpClientProperties mcpProperties;
    private final AppProperties appProperties;

    public McpTransportPolicyValidator(
            McpStreamableHttpClientProperties mcpProperties,
            AppProperties appProperties
    ) {
        this.mcpProperties = mcpProperties;
        this.appProperties = appProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        mcpProperties.getConnections().forEach(this::validateConnection);
    }

    @Override
    public void customize(String name, McpClient.SyncSpec spec) {
        McpStreamableHttpClientProperties.ConnectionParameters parameters =
                mcpProperties.getConnections().get(name);
        if (parameters == null) {
            throw new IllegalStateException("MCP connection '" + name + "' is not a Streamable HTTP connection");
        }
        validateConnection(name, parameters);
    }

    private void validateConnection(
            String name,
            McpStreamableHttpClientProperties.ConnectionParameters parameters
    ) {
        validateConnection(name, parameters.url(), parameters.endpoint());
    }

    public void validateConnection(String name, String url, String endpoint) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("MCP connection '" + name + "' has an invalid URL", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean secure = "https".equals(scheme);
        boolean allowedLanHttp = "http".equals(scheme)
                && appProperties.isLanDevelopment()
                && isPrivateLanHost(uri.getHost());
        if (!secure && !allowedLanHttp) {
            throw new IllegalStateException(
                    "MCP connection '" + name + "' must use HTTPS, except private HTTP in LAN development"
            );
        }
        if (appProperties.isProduction() && !secure) {
            throw new IllegalStateException("MCP connection '" + name + "' must use HTTPS in production");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("MCP connection '" + name + "' URL must not contain credentials or query data");
        }
        if (!StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException("MCP connection '" + name + "' must include a host");
        }
        if (endpoint != null && (!endpoint.startsWith("/") || endpoint.contains("://"))) {
            throw new IllegalStateException("MCP connection '" + name + "' endpoint must be an absolute path");
        }
    }

    private boolean isPrivateLanHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized) || "::1".equals(normalized)) {
            return true;
        }
        if (normalized.startsWith("10.") || normalized.startsWith("192.168.")) {
            return true;
        }
        if (normalized.startsWith("172.")) {
            String[] parts = normalized.split("\\.");
            if (parts.length == 4) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return normalized.endsWith(".local");
    }
}
