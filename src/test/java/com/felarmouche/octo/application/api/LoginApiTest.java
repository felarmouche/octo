package com.felarmouche.octo.application.api;

import com.felarmouche.octo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Logging in with the one user from the environment, over real HTTP so the session cookie is the real one. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.security.user.name=octo",
        "spring.security.user.password=secret",
        "octo.scheduler.first-tick-after=P1D"})
@Import(TestcontainersConfiguration.class)
class LoginApiTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void afterLoggingInTheSessionCookieOpensTheApi() throws Exception {
        HttpResponse<String> login = login("octo", "secret");
        assertThat(login.statusCode()).isEqualTo(204);

        assertThat(get("/api/searches", sessionCookie(login)).statusCode()).isEqualTo(200);
    }

    @Test
    void aWrongPasswordOrUserIsRefusedSlowlyWithoutSayingWhichWasWrong() throws Exception {
        long start = System.nanoTime();
        HttpResponse<String> wrongPassword = login("octo", "wrong");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(900));
        HttpResponse<String> wrongUser = login("someone", "secret");

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(wrongUser.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(wrongUser.body()).contains("\"errors\"");
        assertThat(wrongPassword.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(wrongPassword.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void theSessionEndpointTellsWhetherTheUserIsLoggedInUntilLogout() throws Exception {
        assertThat(get("/api/session", null).statusCode()).isEqualTo(401);
        String cookie = sessionCookie(login("octo", "secret"));
        assertThat(get("/api/session", cookie).statusCode()).isEqualTo(200);

        HttpResponse<String> logout = post("/api/logout", cookie);

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(get("/api/session", cookie).statusCode()).isEqualTo(401);
        assertThat(get("/api/searches", cookie).statusCode()).isEqualTo(401);
    }

    @Test
    void theSessionCookieIsHiddenFromScriptsSameSiteOnlyAndLastsThirtyDays() throws Exception {
        String cookie = setCookie(login("octo", "secret"));

        assertThat(cookie).containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Strict")
                .contains("Max-Age=" + Duration.ofDays(30).toSeconds())
                .contains("Path=/");
    }

    @Test
    void loggingInWithAnExistingSessionGivesANewSessionId() throws Exception {
        String first = sessionCookie(login("octo", "secret"));

        String second = sessionCookie(login("octo", "secret", first));

        assertThat(second).isNotEqualTo(first);
        assertThat(get("/api/session", first).statusCode()).isEqualTo(401);
        assertThat(get("/api/session", second).statusCode()).isEqualTo(200);
    }

    @Test
    void withoutASessionTheApiAnswersAPlain401AndBasicNoLongerWorks() throws Exception {
        HttpResponse<String> anonymous = get("/api/searches", null);
        HttpResponse<String> basic = http.send(HttpRequest.newBuilder(uri("/api/searches"))
                .header("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("octo:secret".getBytes()))
                .GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().firstValue("WWW-Authenticate")).isEmpty();
        assertThat(basic.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> post(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String user, String password) throws Exception {
        return login(user, password, null);
    }

    private HttpResponse<String> login(String user, String password, String cookie) throws Exception {
        String body = "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, password);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) request.header("Cookie", cookie);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
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
