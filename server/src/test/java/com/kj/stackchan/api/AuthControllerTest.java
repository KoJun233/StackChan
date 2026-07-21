package com.kj.stackchan.api;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.device.DeviceCommandGateway;
import com.kj.stackchan.device.DeviceRepository;
import com.kj.stackchan.security.AdminUserEntity;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.AdminPasswordService;
import com.kj.stackchan.security.SecurityConfiguration;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.json.JsonCompareMode.STRICT;

@WebMvcTest({AuthController.class, DeviceController.class})
@Import(SecurityConfiguration.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private AdminPasswordService adminPasswordService;

    @MockitoBean
    private DeviceRepository deviceRepository;

    @MockitoBean
    private DeviceCommandGateway deviceCommandGateway;

    @MockitoBean
    private Clock clock;

    @Test
    void requiresAnAdminSessionForBrowserDeviceRequests() throws Exception {
        mockMvc.perform(get("/api/v1/devices"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", "application/json;charset=UTF-8"))
                .andExpect(content().json("""
                        {"code":"authentication_failed","message":"用户名或密码不正确。"}
                        """, STRICT));
    }

    @Test
    void allowsUnauthenticatedStaticConsoleAssets() throws Exception {
        mockMvc.perform(get("/assets/console.js"))
                .andExpect(status().isNotFound());
    }

    @Test
    void materializesACsrfTokenForTheBrowser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("XSRF-TOKEN="),
                        containsString("SameSite=Lax")
                )))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void csrfSameSiteFallbackPreservesSiblingSetCookieHeaders() {
        String sibling = "SESSION=sibling; Path=/; Secure; HttpOnly";
        String xsrf = "XSRF-TOKEN=csrf-token; Path=/";
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthController controller = controllerWithCsrfCookies(sibling, xsrf);

        controller.csrf(new MockHttpServletRequest(), response);

        assertThat(response.getHeaders("Set-Cookie")).containsExactly(
                sibling,
                xsrf + "; SameSite=Lax"
        );
    }

    @Test
    void csrfSameSiteFallbackIsIdempotentWhenTheAttributeAlreadyExists() {
        String xsrf = "XSRF-TOKEN=csrf-token; Path=/; samesite=Strict";
        String sibling = "SESSION=sibling; Path=/; HttpOnly";
        List<String> setCookies = new ArrayList<>();
        HttpServletResponse response = rawCookieResponse(setCookies);
        AuthController controller = controllerWithCsrfCookies(xsrf, sibling);

        controller.csrf(new MockHttpServletRequest(), response);

        assertThat(setCookies).containsExactly(xsrf, sibling);
    }

    @Test
    void logsInWithTheRawCsrfTokenReturnedToTheBrowserAndCreatesAnAdminSession() throws Exception {
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(new AdminUserEntity(
                "admin",
                "{noop}safe-long-password",
                Instant.parse("2026-07-17T00:00:00Z")
        )));

        BrowserCsrfToken csrf = browserCsrfToken();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"safe-long-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
    }

    @Test
    void loginChangesThePreAuthenticationSessionId() throws Exception {
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(new AdminUserEntity(
                "admin",
                "{noop}safe-long-password",
                Instant.parse("2026-07-17T00:00:00Z")
        )));
        MockHttpSession session = new MockHttpSession();
        String originalId = session.getId();

        MvcResult result = login(session, "safe-long-password");

        assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(originalId);
    }

    @Test
    void returnsTheSameSafeErrorForAnUnknownAdministratorAndAWrongPassword() throws Exception {
        BrowserCsrfToken unknownUserCsrf = browserCsrfToken();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(unknownUserCsrf.cookie())
                        .header("X-XSRF-TOKEN", unknownUserCsrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"unknown\",\"password\":\"invalid-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"authentication_failed","message":"用户名或密码不正确。"}
                        """, STRICT));

        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(new AdminUserEntity(
                "admin",
                "{noop}expected-password",
                Instant.parse("2026-07-17T00:00:00Z")
        )));
        BrowserCsrfToken wrongPasswordCsrf = browserCsrfToken();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(wrongPasswordCsrf.cookie())
                        .header("X-XSRF-TOKEN", wrongPasswordCsrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"invalid-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"authentication_failed","message":"用户名或密码不正确。"}
                        """, STRICT));
    }

    @Test
    void mapsAuthenticationRequestValidationToTheGenericUnauthorizedResponse() throws Exception {
        BrowserCsrfToken loginCsrf = browserCsrfToken();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header("X-XSRF-TOKEN", loginCsrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"authentication_failed","message":"用户名或密码不正确。"}
                        """, STRICT));

        MockHttpSession session = authenticatedSession();
        BrowserCsrfToken passwordCsrf = browserCsrfToken();
        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .cookie(passwordCsrf.cookie())
                        .header("X-XSRF-TOKEN", passwordCsrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"\",\"newPassword\":\"short\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"authentication_failed","message":"用户名或密码不正确。"}
                        """, STRICT));
    }

    @Test
    void rejectsLoginWhenTheCsrfHeaderIsMissing() throws Exception {
        BrowserCsrfToken csrf = browserCsrfToken();

        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"safe-long-password\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doesNotAcceptHttpBasicCredentialsForBrowserDeviceRequests() throws Exception {
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(new AdminUserEntity(
                "admin",
                "{noop}safe-long-password",
                Instant.parse("2026-07-17T00:00:00Z")
        )));
        when(deviceRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/devices").with(httpBasic("admin", "safe-long-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsOutAndInvalidatesTheAdministratorSession() throws Exception {
        MockHttpSession session = authenticatedSession();

        BrowserCsrfToken csrf = browserCsrfToken();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void passwordChangeUpdatesTheHashAndInvalidatesTheCurrentSession() throws Exception {
        MockHttpSession session = authenticatedSession();
        BrowserCsrfToken csrf = browserCsrfToken();

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isNoContent());

        verify(adminPasswordService).changePassword("admin", "old-password", "new-password-123");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void returnsTheSafeAuthenticationErrorWhenTheCurrentPasswordIsWrong() throws Exception {
        MockHttpSession session = authenticatedSession();
        BrowserCsrfToken csrf = browserCsrfToken();
        doThrow(new BadCredentialsException("specific password failure"))
                .when(adminPasswordService)
                .changePassword("admin", "invalid-current-password", "new-password-123");

        mockMvc.perform(post("/api/v1/auth/password")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"invalid-current-password\",\"newPassword\":\"new-password-123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"code":"authentication_failed","message":"用户名或密码不正确。"}
                        """, STRICT));
    }

    private MvcResult login(MockHttpSession session, String password) throws Exception {
        BrowserCsrfToken csrf = browserCsrfToken();
        return mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value())
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isNoContent())
                .andReturn();
    }

    private MockHttpSession authenticatedSession() {
        MockHttpSession session = new MockHttpSession();
        org.springframework.security.core.context.SecurityContext securityContext =
                org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                "admin", "N/A", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        return session;
    }

    private BrowserCsrfToken browserCsrfToken() throws Exception {
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .get("token")
                .asText();
        return new BrowserCsrfToken(csrfCookie, csrfToken);
    }

    private AuthController controllerWithCsrfCookies(String... setCookies) {
        CsrfTokenRepository repository = mock(CsrfTokenRepository.class);
        DeferredCsrfToken deferredToken = mock(DeferredCsrfToken.class);
        CsrfToken csrfToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "csrf-token");
        when(repository.loadDeferredToken(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    HttpServletResponse response = invocation.getArgument(1);
                    for (String setCookie : setCookies) {
                        response.addHeader("Set-Cookie", setCookie);
                    }
                    return deferredToken;
                });
        when(deferredToken.get()).thenReturn(csrfToken);
        return new AuthController(
                mock(AuthenticationManager.class),
                mock(AdminPasswordService.class),
                mock(SecurityContextRepository.class),
                repository
        );
    }

    private HttpServletResponse rawCookieResponse(List<String> setCookies) {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getHeaders("Set-Cookie")).thenAnswer(invocation -> List.copyOf(setCookies));
        doAnswer(invocation -> {
            setCookies.clear();
            setCookies.add(invocation.getArgument(1));
            return null;
        }).when(response).setHeader(org.mockito.ArgumentMatchers.eq("Set-Cookie"), org.mockito.ArgumentMatchers.anyString());
        doAnswer(invocation -> {
            setCookies.add(invocation.getArgument(1));
            return null;
        }).when(response).addHeader(org.mockito.ArgumentMatchers.eq("Set-Cookie"), org.mockito.ArgumentMatchers.anyString());
        return response;
    }

    private record BrowserCsrfToken(Cookie cookie, String value) {
    }
}
