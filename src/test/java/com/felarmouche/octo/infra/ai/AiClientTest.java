package com.felarmouche.octo.infra.ai;

import com.google.genai.errors.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientTest {

    private static final AiRequest<String> REQUEST =
            new AiRequest<>("system", "user {x}", Map.of("x", 1), List.of(), String.class);

    private final FakeModel model = new FakeModel();

    @Test
    void fallsThroughToTheNextModelOnFailure() {
        model.answer("first", () -> { throw new RuntimeException("boom"); });
        model.answer("second", () -> "ok");

        assertThat(client("first", "second").call(REQUEST)).isEqualTo("ok");
        assertThat(model.calls).containsExactly("first", "second");
    }

    @Test
    void skipsAModelThatIsCoolingDownAfterACapacityProblem() {
        AiClient client = client("first", "second");
        model.answer("first", () -> { throw apiError(429, "Resource exhausted"); });
        model.answer("second", () -> "ok");
        client.call(REQUEST);
        model.calls.clear();

        assertThat(client.call(REQUEST)).isEqualTo("ok");
        assertThat(model.calls).containsExactly("second");
    }

    @Test
    void triesAllModelsWhenAllAreCoolingDown() {
        AiClient client = client("first", "second");
        model.answer("first", () -> { throw apiError(503, "Unavailable"); });
        model.answer("second", () -> { throw apiError(504, "Deadline exceeded"); });
        assertThatThrownBy(() -> client.call(REQUEST)).isInstanceOf(IllegalStateException.class);
        model.calls.clear();
        model.answer("second", () -> "ok");

        assertThat(client.call(REQUEST)).isEqualTo("ok");
        assertThat(model.calls).containsExactly("first", "second");
    }

    @Test
    void stopsOnAnInvalidApiKey() {
        ApiException invalidKey = apiError(400, "API key not valid. reason: API_KEY_INVALID");
        model.answer("first", () -> { throw new RuntimeException("Failed to generate content", invalidKey); });
        model.answer("second", () -> "ok");

        assertThatThrownBy(() -> client("first", "second").call(REQUEST)).hasRootCause(invalidKey);
        assertThat(model.calls).containsExactly("first");
    }

    @Test
    void stopsOnUnauthorized() {
        model.answer("first", () -> { throw apiError(401, "Unauthorized"); });
        model.answer("second", () -> "ok");

        assertThatThrownBy(() -> client("first", "second").call(REQUEST)).isInstanceOf(ApiException.class);
        assertThat(model.calls).containsExactly("first");
    }

    @Test
    void coolsDownAModelThatTimedOut() {
        AiClient client = client("first", "second");
        model.answer("first", () -> { throw new RuntimeException(new java.net.SocketTimeoutException("timeout")); });
        model.answer("second", () -> "ok");
        client.call(REQUEST);
        model.calls.clear();

        client.call(REQUEST);
        assertThat(model.calls).containsExactly("second");
    }

    @Test
    void collectsTheRootCauseOfEveryFailedModel() {
        ApiException overloaded = apiError(503, "The model is overloaded.\n{\"details\": \"...\"}");
        model.answer("first", () -> { throw new RuntimeException("Failed to generate content", overloaded); });
        model.answer("second", () -> { throw new IllegalArgumentException("thinking level not supported"); });

        assertThatThrownBy(() -> client("first", "second").call(REQUEST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Alle Gemini-Modelle fehlgeschlagen: "
                        + "first: ApiException: 503 STATUS. The model is overloaded. | "
                        + "second: IllegalArgumentException: thinking level not supported");
    }

    @Test
    void treatsAnEmptyOrIncompleteResponseAsAFailure() {
        AiRequest<String> needsNonBlank =
                new AiRequest<>("system", "user", Map.of(), List.of(), String.class, s -> !s.isBlank());
        model.answer("first", () -> null);
        model.answer("second", () -> " ");
        model.answer("third", () -> "ok");

        assertThat(client("first", "second", "third").call(needsNonBlank)).isEqualTo("ok");
        assertThat(model.calls).containsExactly("first", "second", "third");
    }

    private AiClient client(String... names) {
        List<GeminiFallbackProperties.Model> models = new ArrayList<>();
        for (String name : names) {
            models.add(new GeminiFallbackProperties.Model(name, GoogleGenAiThinkingLevel.MINIMAL));
        }
        return new AiClient(model, new GeminiFallbackProperties(models, Duration.ofSeconds(1), Duration.ofMinutes(5)));
    }

    private static ApiException apiError(int code, String message) {
        return new ApiException(code, "STATUS", message);
    }

    /** Antwortet je Modellname mit dem hinterlegten Verhalten und merkt sich die Aufrufreihenfolge. */
    private static final class FakeModel implements AiModel {
        final List<String> calls = new ArrayList<>();
        private final Map<String, Supplier<Object>> answers = new HashMap<>();

        void answer(String modelName, Supplier<Object> answer) {
            answers.put(modelName, answer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(GeminiFallbackProperties.Model model, AiRequest<T> request) {
            calls.add(model.name());
            return (T) answers.get(model.name()).get();
        }
    }
}
