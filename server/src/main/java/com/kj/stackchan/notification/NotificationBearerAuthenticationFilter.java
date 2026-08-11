package com.kj.stackchan.notification;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class NotificationBearerAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORITY = "ROLE_NOTIFICATION_INTEGRATION";

    private final ObjectProvider<NotificationIntegrationService> integrationServiceProvider;
    private final ObjectMapper objectMapper;

    public NotificationBearerAuthenticationFilter(
            ObjectProvider<NotificationIntegrationService> integrationServiceProvider,
            ObjectMapper objectMapper
    ) {
        this.integrationServiceProvider = integrationServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/external/notifications")
                || path.equals("/mcp/notifications")
                || path.startsWith("/mcp/notifications/"));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw authenticationFailed();
            }
            String rawToken = header.substring(7).trim();
            NotificationIntegrationService integrationService = integrationServiceProvider.getIfAvailable();
            if (integrationService == null) {
                throw authenticationFailed();
            }
            NotificationIntegrationPrincipal principal = integrationService.authenticate(rawToken);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(AUTHORITY))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (NotificationApiException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(exception.getStatus().value());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ErrorResponse(exception.getCode(), exception.getMessage()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private NotificationApiException authenticationFailed() {
        return new NotificationApiException(
                org.springframework.http.HttpStatus.UNAUTHORIZED,
                "notification_authentication_failed",
                "通知令牌无效或已过期。"
        );
    }

    private record ErrorResponse(String code, String message) { }
}
