package com.felarmouche.octo.application.api;

import com.felarmouche.octo.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The errors no real endpoint can be made to raise on purpose, through endpoints of this test that raise them.
 * The other statuses are covered where they arise: {@link LoginApiTest} (401), {@link SearchApiTest} (400, 404).
 */
@SpringBootTest(properties = {
        "spring.security.user.name=octo",
        "spring.security.user.password=secret",
        "octo.scheduler.first-tick-after=P1D"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, ApiErrorsTest.Failing.class})
class ApiErrorsTest {

    @Autowired
    private MockMvcTester mvc;
    private MockHttpSession session;

    @BeforeEach
    void login() {
        session = (MockHttpSession) mvc.post().uri("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"octo\",\"password\":\"secret\"}")
                .exchange().getRequest().getSession(false);
    }

    @Test
    void aFailureIsA500ThatShowsNothingOfItsCause() {
        MvcTestResult result = get("/api/test/fails");

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR).bodyJson()
                .isLenientlyEqualTo("{\"errors\":[{\"message\":\"internal error\"}]}");
        assertThat(result).bodyText().doesNotContain("database password").doesNotContain("Exception");
    }

    @Test
    void invalidInputThatSlipsPastValidationIsA400() {
        assertThat(get("/api/test/invalid-input")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .isLenientlyEqualTo("{\"errors\":[{\"message\":\"window must end after it starts\"}]}")
                .doesNotHavePath("$.errors[0].field");
    }

    @Test
    void aForbiddenRequestIsA403() {
        assertThat(get("/api/test/forbidden")).hasStatus(HttpStatus.FORBIDDEN).bodyJson()
                .doesNotHavePath("$.errors[0].field")
                .hasPathSatisfying("$.errors[0].message", v -> v.assertThat().asString().isNotBlank());
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).session(session).exchange();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(FailingController.class)
    static class Failing {
    }

    @RestController
    static class FailingController {
        @GetMapping("/api/test/fails")
        String fails() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/api/test/invalid-input")
        String invalidInput() {
            throw new IllegalArgumentException("window must end after it starts");
        }

        @GetMapping("/api/test/forbidden")
        String forbidden() {
            throw new AccessDeniedException("not yours");
        }
    }
}
