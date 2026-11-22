package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.api.ApiErrors.ErrorResponse;
import com.felarmouche.octo.application.settings.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import java.time.Duration;
import java.util.List;

/** Opens and ends the session of the one user (ADR-0002). */
@RestController
@RequestMapping("/api")
class LoginController {

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final Duration failureDelay;

    /** The manager Spring Boot builds for the user from the environment; exposing it as a bean would recurse. */
    LoginController(AuthenticationConfiguration configuration, SecurityContextRepository contexts,
                    @Value("${octo.login.failure-delay:PT1S}") Duration failureDelay) throws Exception {
        this.authentication = configuration.getAuthenticationManager();
        this.contexts = contexts;
        this.failureDelay = failureDelay;
    }

    record Credentials(String username, String password) {
    }

    /** A failed attempt answers only after {@code octo.login.failure-delay}, which makes guessing the password slow. */
    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Credentials credentials, HttpServletRequest request, HttpServletResponse response)
            throws InterruptedException {
        Authentication user;
        try {
            user = authentication.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(), credentials.password()));
        } catch (AuthenticationException e) {
            Thread.sleep(failureDelay);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(List.of(new FieldError("", "wrong username or password"))));
        }
        // a session that existed before the login gets a new id, so nobody can plant one beforehand
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(user);
        contexts.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }

    /** 200 while the session is open; without one, the security setup answers 401. */
    @GetMapping("/session")
    ResponseEntity<Void> session() {
        return ResponseEntity.ok().build();
    }

    /** Ends the session, if there is one. */
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
