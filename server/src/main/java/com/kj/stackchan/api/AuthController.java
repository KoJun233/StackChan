package com.kj.stackchan.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.kj.stackchan.security.AdminPasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AdminPasswordService adminPasswordService;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            AdminPasswordService adminPasswordService,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.adminPasswordService = adminPasswordService;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        CsrfToken token = csrfTokenRepository.loadDeferredToken(servletRequest, servletResponse).get();
        ensureCsrfSameSiteLax(servletResponse);
        return token;
    }

    private void ensureCsrfSameSiteLax(HttpServletResponse servletResponse) {
        List<String> setCookies = new ArrayList<>(servletResponse.getHeaders(HttpHeaders.SET_COOKIE));
        boolean changed = false;
        for (int index = 0; index < setCookies.size(); index++) {
            String setCookie = setCookies.get(index);
            if (setCookie.startsWith("XSRF-TOKEN=")
                    && !setCookie.toLowerCase(Locale.ROOT).contains("samesite=")) {
                setCookies.set(index, setCookie + "; SameSite=Lax");
                changed = true;
            }
        }
        if (!changed) {
            return;
        }
        servletResponse.setHeader(HttpHeaders.SET_COOKIE, setCookies.getFirst());
        for (int index = 1; index < setCookies.size(); index++) {
            servletResponse.addHeader(HttpHeaders.SET_COOKIE, setCookies.get(index));
        }
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        Authentication authenticated = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authenticated);
        securityContextRepository.saveContext(securityContext, servletRequest, servletResponse);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    @PostMapping(path = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody PasswordChangeRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        adminPasswordService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        );
        HttpSession session = servletRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 4096) String password
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {
    }
}
