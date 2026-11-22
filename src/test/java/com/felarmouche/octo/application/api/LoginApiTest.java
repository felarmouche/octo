package com.felarmouche.octo.application.api;

import com.felarmouche.octo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Logging in with the one user from the environment, over real HTTP so the session cookie is the real one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.user.name=octo",
        "spring.security.user.password=secret",
        "octo.scheduler.first-tick-after=P1D"})
@Import(TestcontainersConfiguration.class)
class LoginApiTest {

    private static final int OK = HttpStatus.OK.value();
    private static final int NO_CONTENT = HttpStatus.NO_CONTENT.value();
    private static final int UNAUTHORIZED = HttpStatus.UNAUTHORIZED.value();

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void afterLoggingInTheSessionCookieOpensTheApi() throws Exception {
        HttpResponse<String> login = login("octo", "secret", null);
        assertThat(login.statusCode()).isEqualTo(NO_CONTENT);

        assertThat(send(get("/api/searches"), sessionCookie(login)).statusCode()).isEqualTo(OK);
    }

    @Test
    void aWrongPasswordOrUserIsRefusedSlowlyWithoutSayingWhichWasWrong() throws Exception {
        HttpResponse<String> wrongPassword = timedLogin("octo", "wrong");
        HttpResponse<String> wrongUser = timedLogin("someone", "secret");

        assertThat(wrongPassword.statusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(wrongUser.statusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(wrongPassword.body()).isEqualTo(wrongUser.body()).contains("\"errors\"");
        assertThat(wrongPassword.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(wrongPassword.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void theSessionEndpointTellsWhetherTheUserIsLoggedInUntilLogout() throws Exception {
        assertThat(send(get("/api/session"), null).statusCode()).isEqualTo(UNAUTHORIZED);
        String cookie = sessionCookie(login("octo", "secret", null));
        assertThat(send(get("/api/session"), cookie).statusCode()).isEqualTo(OK);

        HttpResponse<String> logout = send(post("/api/logout"), cookie);

        assertThat(logout.statusCode()).isEqualTo(NO_CONTENT);
        assertThat(send(get("/api/session"), cookie).statusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(send(get("/api/searches"), cookie).statusCode()).isEqualTo(UNAUTHORIZED);
    }

    @Test
    void theSessionCookieIsHiddenFromScriptsSameSiteOnlyAndLastsThirtyDays() throws Exception {
        String cookie = setCookie(login("octo", "secret", null));

        assertThat(cookie).containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Strict")
                .contains("Max-Age=" + Duration.ofDays(30).toSeconds())
                .contains("Path=/");
    }

    @Test
    void loggingInWithAnExistingSessionGivesANewSessionId() throws Exception {
        String first = sessionCookie(login("octo", "secret", null));

        String second = sessionCookie(login("octo", "secret", first));

        assertThat(second).isNotEqualTo(first);
        assertThat(send(get("/api/session"), first).statusCode()).isEqualTo(UNAUTHORIZED);
        assertThat(send(get("/api/session"), second).statusCode()).isEqualTo(OK);
    }

    @Test
    void withoutASessionEverythingButLoginIsAPlain401WithoutCreatingASession() throws Exception {
        String basic = "Basic " + Base64.getEncoder().encodeToString("octo:secret".getBytes(StandardCharsets.UTF_8));
        for (HttpRequest.Builder request : new HttpRequest.Builder[]{
                get("/api/searches"), post("/api/logout"), get("/api/no-such-endpoint"),
                get("/api/searches").header("Authorization", basic)}) {
            HttpResponse<String> response = send(request, null);

            assertThat(response.statusCode()).isEqualTo(UNAUTHORIZED);
            assertThat(response.headers().firstValue("WWW-Authenticate")).isEmpty();
            assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                    type -> assertThat(type).startsWith("application/json"));
            assertThat(response.body()).isEqualTo("{\"errors\":[{\"message\":\"not logged in\"}]}");
        }
    }

    @Test
    void aWrongLoginBelongsToNoField() throws Exception {
        assertThat(login("octo", "wrong", null).body())
                .isEqualTo("{\"errors\":[{\"message\":\"wrong username or password\"}]}");
    }

    private HttpResponse<String> timedLogin(String user, String password) throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> response = login(user, password, null);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
        return response;
    }

    private HttpResponse<String> login(String user, String password, String cookie) throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, password);
        return send(HttpRequest.newBuilder(uri("/api/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)), cookie);
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private HttpRequest.Builder post(String path) {
        return HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody());
    }

    private HttpResponse<String> send(HttpRequest.Builder request, String cookie) throws Exception {
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The {@code name=value} part of the session's Set-Cookie header. */
    private static String sessionCookie(HttpResponse<?> response) {
        return setCookie(response).split(";")[0];
    }

    private static String setCookie(HttpResponse<?> response) {
        return response.headers().firstValue("Set-Cookie").orElseThrow(() -> new AssertionError("no Set-Cookie"));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
