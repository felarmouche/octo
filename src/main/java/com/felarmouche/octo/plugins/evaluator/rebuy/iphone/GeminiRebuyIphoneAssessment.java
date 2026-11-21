package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import com.felarmouche.octo.core.shared.listing.PhoneListing;
import com.felarmouche.octo.infra.ListingImageLoader;
import com.google.genai.errors.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeminiRebuyIphoneAssessment {

    public static final String UNKNOWN_COLOR = "UNKNOWN_COLOR";
    // Schema erzwingt integer, daher Zahl statt String als "nicht bestimmbar"
    public static final int UNKNOWN_BATTERY_CAPACITY = -1;

    // Überlastung/Kontingent: Modell für eine Weile überspringen, statt es bei jedem Listing erneut zu probieren
    private static final Set<Integer> CAPACITY_STATUS_CODES = Set.of(429, 503, 504);
    // Falscher API-Key betrifft alle Modelle gleich -> Ausweichen sinnlos
    private static final Set<Integer> FATAL_STATUS_CODES = Set.of(401);
    private static final int MAX_IMAGES = 4;

    private static final String USER_TEMPLATE = """
            Bewerte den Zustand des Smartphones.

            <verkaeuferangaben>
            Titel: {title}
            Beschreibung: {description}
            Preis: {price} Euro
            Produktdaten: {productSpecificFields}
            </verkaeuferangaben>

            Farbe: Wähle genau eine aus [{colors}]. Falls nicht bestimmbar: {unknownColor}
            Speicher: Wähle genau einen aus [{memorySizes}]. Falls nicht bestimmbar: {unknownMemory}
            Akkukapazität: Bestimme sofern ersichtlich die Akkukapazität. Falls nicht bestimmbar: {unknownBatteryCapacity}
            """;

    private final ChatClient chatClient;
    private final List<GeminiFallbackProperties.Model> models;
    private final Duration cooldown;
    private final ListingImageLoader imageLoader;
    private final Map<String, Instant> coolingDownUntil = new ConcurrentHashMap<>();

    public GeminiRebuyIphoneAssessment(ChatClient.Builder builder,
                                       @Value("classpath:prompts/condition-inspector.st") Resource systemPrompt,
                                       GeminiFallbackProperties props,
                                       ListingImageLoader imageLoader) {
        this.chatClient = builder.defaultSystem(systemPrompt).build();
        this.models = List.copyOf(props.models());
        this.cooldown = props.cooldown();
        this.imageLoader = imageLoader;
    }

    /**
     * @throws IllegalStateException wenn keine Bilder ladbar sind oder alle Modelle fehlschlagen
     */
    public ConditionAssessment assessCondition(PhoneListing phone, RebuyIPhoneCatalog catalog) {
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");

        Media[] images = imageLoader.load(phone.getImages(), MAX_IMAGES).toArray(Media[]::new);
        if (images.length == 0) {
            throw new IllegalStateException("Keine Fotos ladbar");
        }

        Map<String, Object> params = Map.of(
                "title", nullToEmpty(phone.getTitle()),
                "description", nullToEmpty(phone.getDescription()),
                "price", phone.getPriceInEuro(),
                "colors", join(catalog.getAllColors()),
                "unknownColor", UNKNOWN_COLOR,
                "memorySizes", join(catalog.getAllMemorySizes()),
                "unknownBatteryCapacity", UNKNOWN_BATTERY_CAPACITY,
                "unknownMemory", PhoneListing.Memory.UNKNOWN.name(),
                "productSpecificFields", Objects.toString(phone.getProductSpecificFields(), "")
        );

        List<String> failures = new ArrayList<>();
        for (GeminiFallbackProperties.Model model : modelsToTry()) {
            try {
                long start = System.currentTimeMillis();
                ConditionAssessment result = call(model, params, images);
                log.debug("Modell {} bewertet in {} Sekunden '{}': {}", model.name(),
                        (System.currentTimeMillis() - start) / 1000, phone.getTitle(), result);
                return result;
            } catch (RuntimeException e) {
                if (isFatal(e)) {
                    throw e;
                }
                String cause = rootCauseMessage(e);
                failures.add(model.name() + ": " + cause);
                if (isCapacityProblem(e)) {
                    coolingDownUntil.put(model.name(), Instant.now().plus(cooldown));
                    log.warn("Modell {} überlastet ({}), pausiert für {}s, versuche nächstes",
                            model.name(), cause, cooldown.toSeconds());
                } else {
                    // z.B. 400 wegen nicht unterstützter Option -> Konfigurationsfehler, laut loggen
                    log.error("Modell {} fehlgeschlagen ({}), versuche nächstes", model.name(), cause, e);
                }
            }
        }
        throw new IllegalStateException("Alle Gemini-Modelle fehlgeschlagen: " + String.join(" | ", failures));
    }

    private ConditionAssessment call(GeminiFallbackProperties.Model model, Map<String, Object> params, Media[] images) {
        ConditionAssessment result = chatClient.prompt()
                .user(u -> u.text(USER_TEMPLATE).params(params).media(images))
                .options(GoogleGenAiChatOptions.builder()
                        .model(model.name())
                        .thinkingLevel(model.thinkingLevel()))
                .call()
                .entity(ConditionAssessment.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);
        // null = leere/abgeschnittene/blockierte Antwort; condition ist Pflicht für die Preisberechnung
        if (result == null || result.condition() == null) {
            throw new EmptyResponseException("Leere oder unvollständige Antwort: " + result);
        }
        return result;
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
        return findCause(e, java.io.IOException.class) != null; // Timeout (gemini.request-timeout)/Verbindung
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

    private static String join(Collection<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static final class EmptyResponseException extends RuntimeException {
        EmptyResponseException(String message) {
            super(message);
        }
    }
}
