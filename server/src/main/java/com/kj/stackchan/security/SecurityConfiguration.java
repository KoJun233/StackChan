package com.kj.stackchan.security;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kj.stackchan.api.DeviceApiExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import static com.kj.stackchan.api.DeviceApiExceptionHandler.AUTHENTICATION_FAILED;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final String CURRENT_PASSWORD_ID = "pbkdf2@SpringSecurity_v5_8";
    private static final String CURRENT_PASSWORD_PREFIX = "{" + CURRENT_PASSWORD_ID + "}";

    @Bean
    PasswordEncoder passwordEncoder() {
        return new CompatiblePasswordEncoder();
    }

    private static final class CompatiblePasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder current = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        private final PasswordEncoder verifier = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        @Override
        public String encode(CharSequence rawPassword) {
            return CURRENT_PASSWORD_PREFIX + current.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return verifier.matches(rawPassword, encodedPassword);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            if (!encodedPassword.startsWith(CURRENT_PASSWORD_PREFIX)) {
                return true;
            }
            return current.upgradeEncoding(encodedPassword.substring(CURRENT_PASSWORD_PREFIX.length()));
        }
    }

    @Bean
    UserDetailsService adminUserDetailsService(AdminUserRepository adminUserRepository) {
        return username -> adminUserRepository.findByUsername(username)
                .map(admin -> User.withUsername(admin.getUsername())
                        .password(admin.getPasswordHash())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown administrator"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService adminUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(Environment environment) {
        boolean production = environment.getProperty("companion.production", Boolean.class, false);
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .httpOnly(false)
                .sameSite("Lax")
                .secure(production));
        return repository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper
    ) throws Exception {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers(
                                new AntPathRequestMatcher("/api/v1/pairing/claim", HttpMethod.POST.name()),
                                new AntPathRequestMatcher("/api/v1/devices/token:refresh", HttpMethod.POST.name()),
                                new AntPathRequestMatcher("/api/v1/device/**")
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.svg", "/assets/**", "/browser_upgrade/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/pairing/claim",
                                "/api/v1/devices/token:refresh"
                        ).permitAll()
                        .requestMatchers("/api/v1/ws/device").permitAll()
                        .requestMatchers("/api/v1/device/**").permitAll()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authenticationException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                            response.setContentType(DeviceApiExceptionHandler.JSON_UTF8.toString());
                            objectMapper.writeValue(response.getOutputStream(), AUTHENTICATION_FAILED);
                        })
                )
                .authenticationManager(authenticationManager)
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .build();
    }
}
