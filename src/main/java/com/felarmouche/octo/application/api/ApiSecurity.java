package com.felarmouche.octo.application.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * A session login with the one user from the environment ({@code spring.security.user.*}), see ADR-0002.
 * {@link SessionController} opens the session; everything else needs it. Without one, the answer is a plain
 * 401 without {@code WWW-Authenticate} (so a browser shows no login dialog), and no session is created.
 * CSRF protection is off because the session cookie is SameSite=Strict and the frontend is same-origin.
 */
@Configuration
class ApiSecurity {

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityContextRepository sessions) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/login").permitAll()
                        .anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(sessions))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
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
