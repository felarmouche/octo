package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.api.ApiErrors.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * A session login with the one user from the environment ({@code spring.security.user.*}), see ADR-0002.
 * {@link SessionController} opens the session; everything else needs it. Without one, the answer is a 401 with
 * the uniform error body and without {@code WWW-Authenticate} (so a browser shows no login dialog), and no session is created.
 * CSRF protection is off because the session cookie is SameSite=Strict and the frontend is same-origin.
 */
@Configuration
class ApiSecurity {

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityContextRepository sessions, JsonMapper json) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/login").permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(sessions))
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    json.writeValue(response.getOutputStream(), ErrorResponse.of("not logged in"));
                }))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    /** Shared with {@link SessionController}, which stores the logged-in user in the session. */
    @Bean
    SecurityContextRepository sessions() {
        return new HttpSessionSecurityContextRepository();
    }
}
