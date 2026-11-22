package com.felarmouche.octo.infra.ai;

import com.google.genai.errors.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AiClient {

    // Überlastung/Kontingent: Modell für eine Weile überspringen, statt es bei jedem Aufruf erneut zu probieren
    private static final Set<Integer> CAPACITY_STATUS_CODES = Set.of(429, 503, 504);
    // Falscher API-Key betrifft alle Modelle gleich -> Ausweichen sinnlos
    private static final Set<Integer> FATAL_STATUS_CODES = Set.of(401);

    private final AiModel model;
    private final List<GeminiFallbackProperties.Model> models;
    private final Duration cooldown;
    private final Map<String, Instant> coolingDownUntil = new ConcurrentHashMap<>();

    public AiClient(AiModel model, GeminiFallbackProperties props) {
        this.model = model;
        this.models = List.copyOf(props.models());
        this.cooldown = props.cooldown();
    }

    /**
     * Probiert die Modelle der Ausweichkette in Prioritätsreihenfolge, bis eines antwortet.
     *
     * @throws IllegalStateException wenn alle Modelle fehlschlagen; die Meldung nennt die Ursache je Modell
     * @throws RuntimeException      unverändert bei ungültigem API-Key, ohne weitere Modelle zu probieren
     */
    public <T> T call(AiRequest<T> request) {
        List<String> failures = new ArrayList<>();
        for (GeminiFallbackProperties.Model candidate : modelsToTry()) {
            try {
                long start = System.currentTimeMillis();
                T result = model.call(candidate, request);
                // null = leere/abgeschnittene/blockierte Antwort
                if (result == null || !request.isComplete().test(result)) {
                    throw new EmptyResponseException("Leere oder unvollständige Antwort: " + result);
                }
                log.debug("Modell {} antwortet in {} Sekunden: {}", candidate.name(),
                        (System.currentTimeMillis() - start) / 1000, result);
                return result;
            } catch (RuntimeException e) {
                if (isFatal(e)) {
                    throw e;
                }
                String cause = rootCauseMessage(e);
                failures.add(candidate.name() + ": " + cause);
                if (isCapacityProblem(e)) {
                    coolingDownUntil.put(candidate.name(), Instant.now().plus(cooldown));
                    log.warn("Modell {} überlastet ({}), pausiert für {}s, versuche nächstes",
                            candidate.name(), cause, cooldown.toSeconds());
                } else {
                    // z.B. 400 wegen nicht unterstützter Option -> Konfigurationsfehler, laut loggen
                    log.error("Modell {} fehlgeschlagen ({}), versuche nächstes", candidate.name(), cause, e);
                }
            }
        }
        throw new IllegalStateException("Alle Gemini-Modelle fehlgeschlagen: " + String.join(" | ", failures));
    }

    // Spring AI wickelt alles in "Failed to generate content" ein; die echte Ursache steckt in der Cause-Kette
    public static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        // nur erste Zeile: ApiException hängt mehrzeilige JSON-Details an
        String msg = Objects.toString(root.getMessage(), "").lines().findFirst().orElse("");
        return root.getClass().getSimpleName() + ": " + msg;
    }

    /** Modelle in Prioritätsreihenfolge ohne die pausierten; sind alle pausiert, trotzdem alle probieren. */
    private List<GeminiFallbackProperties.Model> modelsToTry() {
        Instant now = Instant.now();
        List<GeminiFallbackProperties.Model> available = models.stream()
                .filter(m -> coolingDownUntil.getOrDefault(m.name(), Instant.MIN).isBefore(now))
                .toList();
        return available.isEmpty() ? models : available;
    }

    private static boolean isFatal(Throwable e) {
        ApiException api = findCause(e, ApiException.class);
        if (api == null) {
            return false;
        }
        // Gemini meldet einen ungültigen Key als 400 mit reason API_KEY_INVALID, nicht als 401
        String msg = Objects.toString(api.getMessage(), "");
        return FATAL_STATUS_CODES.contains(api.code()) || msg.contains("API_KEY_INVALID");
    }

    private static boolean isCapacityProblem(Throwable e) {
        ApiException api = findCause(e, ApiException.class);
        if (api != null) {
            return CAPACITY_STATUS_CODES.contains(api.code());
        }
        return findCause(e, IOException.class) != null; // Timeout (gemini.request-timeout)/Verbindung
    }

    private static <T extends Throwable> T findCause(Throwable e, Class<T> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    private static final class EmptyResponseException extends RuntimeException {
        EmptyResponseException(String message) {
            super(message);
        }
    }
}
