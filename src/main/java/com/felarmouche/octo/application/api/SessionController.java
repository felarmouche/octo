package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.api.ApiErrors.ErrorResponse;
import com.felarmouche.octo.application.settings.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Opens, shows and ends the session of the one user (ADR-0002). */
@RestController
@RequestMapping("/api")
class SessionController {

    /** How long a failed login waits before it answers, which makes guessing the password slow. */
    private static final Duration FAILED_LOGIN_DELAY = Duration.ofSeconds(1);

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository sessions;

    /** The manager Spring Boot builds for the user from the environment; exposing it as a bean would recurse. */
    SessionController(AuthenticationConfiguration configuration, SecurityContextRepository sessions) throws Exception {
        this.authenticationManager = configuration.getAuthenticationManager();
        this.sessions = sessions;
    }

    record Credentials(String username, String password) {
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@RequestBody Credentials credentials, HttpServletRequest request, HttpServletResponse response)
            throws InterruptedException {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        try {
            context.setAuthentication(authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(), credentials.password())));
        } catch (AuthenticationException e) {
            Thread.sleep(FAILED_LOGIN_DELAY);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(List.of(new FieldError("", "wrong username or password"))));
        }
        // a session that existed before the login gets a new id, so nobody can plant one beforehand
        if (request.getSession(false) != null) request.changeSessionId();
        sessions.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }

    /** 200 while the session is open; without one, {@link ApiSecurity} answers 401. */
    @GetMapping("/session")
    ResponseEntity<Void> session() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return ResponseEntity.noContent().build();
    }
}
