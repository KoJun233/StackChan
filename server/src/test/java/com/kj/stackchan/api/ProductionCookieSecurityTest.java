package com.kj.stackchan.api;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.security.AdminPasswordService;
import com.kj.stackchan.security.AdminUserEntity;
import com.kj.stackchan.security.AdminUserRepository;
import com.kj.stackchan.security.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AuthController.class, properties = {
        "companion.production=true",
        "server.servlet.session.cookie.secure=true",
        "server.servlet.session.cookie.http-only=true",
        "server.servlet.session.cookie.same-site=lax"
})
@Import(SecurityConfiguration.class)
class ProductionCookieSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ServerProperties serverProperties;

    @MockitoBean
    private AdminUserRepository adminUserRepository;

    @MockitoBean
    private AdminPasswordService adminPasswordService;

    @MockitoBean
    private Clock clock;

    @Test
    void csrfCookieIsSecureAndSameSiteLax() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("XSRF-TOKEN="),
                        containsString("Secure"),
                        containsString("SameSite=Lax")
                )));
    }

    @Test
    void sessionCookieIsSecureHttpOnlyAndSameSiteLax() throws Exception {
        when(adminUserRepository.findByUsername("admin")).thenReturn(Optional.of(new AdminUserEntity(
                "admin",
                "{noop}safe-long-password",
                Instant.parse("2026-07-17T00:00:00Z")
        )));
        MvcResult csrfResult = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        String csrfToken = objectMapper.readTree(csrfResult.getResponse().getContentAsString())
                .get("token")
                .asText();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"safe-long-password\"}"))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(loginResult.getRequest().getSession(false)).isNotNull();
        org.springframework.boot.web.servlet.server.Session.Cookie sessionCookie =
                serverProperties.getServlet().getSession().getCookie();
        assertThat(sessionCookie.getSecure()).isTrue();
        assertThat(sessionCookie.getHttpOnly()).isTrue();
        assertThat(sessionCookie.getSameSite()).isEqualTo(SameSite.LAX);
    }
}
