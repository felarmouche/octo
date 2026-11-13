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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeminiRebuyIphoneAssessment {

    public static final String UNKNOWN_COLOR = "UNKNOWN_COLOR";
    // Schema erzwingt integer, daher Zahl statt String als "nicht bestimmbar"
    public static final int UNKNOWN_BATTERY_CAPACITY = -1;

    private static final Set<Integer> FALLBACK_STATUS_CODES = Set.of(403, 404, 429, 503);
    private static final List<String> FALLBACK_MESSAGES =
            List.of("RESOURCE_EXHAUSTED", "NOT_FOUND", "PERMISSION_DENIED", "UNAVAILABLE");
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
    private final String[] models;
    private final ListingImageLoader imageLoader;
    private int currentModelIndex = 0;

    public GeminiRebuyIphoneAssessment(ChatClient.Builder builder,
                                       @Value("classpath:prompts/condition-inspector.st") Resource systemPrompt,
                                       GeminiFallbackProperties props,
                                       ListingImageLoader imageLoader) {
        this.chatClient = builder.defaultSystem(systemPrompt).build();
        this.models = props.fallbackModels().toArray(new String[0]);
        this.imageLoader = imageLoader;
        if (models.length < 1) {
            throw new IllegalStateException("Keine Gemini-Modelle konfiguriert");
        }
    }

    public ConditionAssessment assessCondition(PhoneListing phone, RebuyIPhoneCatalog catalog) {
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");

        List<Media> images = imageLoader.load(phone.getImages(), MAX_IMAGES);

        Media[] imageArray = images.toArray(Media[]::new);

        Map<String, Object> params = Map.of(
                "title", nullToEmpty(phone.getTitle()),
                "description", nullToEmpty(phone.getDescription()),
                "price", phone.getPriceInEuro(),
                "colors", join(catalog.getAllColors()),
                "unknownColor", UNKNOWN_COLOR,
                "memorySizes", join(catalog.getAllMemorySizes()),
                "unknownBatteryCapacity", UNKNOWN_BATTERY_CAPACITY,
                "unknownMemory", PhoneListing.Memory.UNKNOWN.name(),
                "productSpecificFields", phone.getProductSpecificFields()
        );

        RuntimeException lastError = null;

        boolean done = false;
        ConditionAssessment result = null;
        int maxIterations = models.length;
        while (!done) {
            String model = models[currentModelIndex];
            try {
                long startTime = System.currentTimeMillis();
                result = chatClient.prompt()
                        .user(u -> u.text(USER_TEMPLATE).params(params).media(imageArray))
                        .options(GoogleGenAiChatOptions.builder()
                                .model(model))
                        .call()
                        .entity(ConditionAssessment.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);

                long endTime = System.currentTimeMillis();
                long seconds = (endTime - startTime) / 1000;
                if (result == null) {
                    throw new IllegalStateException("Leere Antwort von Modell " + model);
                }
                log.debug("Modell {} bewertet in {} Sekunden '{}': {}", model, seconds, phone.getTitle(), result);
                done = true;
            } catch (RuntimeException e) {
                if (!shouldTryNextModel(e)) {
                    throw e;
                }
                log.warn("Modell {} nicht verfügbar ({}), versuche nächstes", model, rootCauseMessage(e));
                lastError = e;
                updateModelIndex();
                if (--maxIterations < 1) {
                    throw new IllegalStateException("Alle Gemini-Modelle nicht verfügbar/Kontingente aufgebraucht", lastError);
                }
            }

        }
        return result;
    }

    private boolean shouldTryNextModel(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ApiException api && FALLBACK_STATUS_CODES.contains(api.code())) {
                return true;
            }
            if (t instanceof java.io.IOException) {
                return true;   // Timeout (gemini.request-timeout) oder Verbindungsfehler -> nächstes Modell
            }
            if (t instanceof tools.jackson.core.JacksonException) {
                return true;   // abgeschnittene/ungültige Antwort -> nächstes Modell probieren
            }
            String msg = t.getMessage();
            if (msg != null && FALLBACK_MESSAGES.stream().anyMatch(msg::contains)) {
                return true;
            }
        }
        return false;
    }

    // Spring AI wickelt alles in "Failed to generate content" ein; die echte Ursache steckt in der Cause-Kette
    private static String rootCauseMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static String join(Collection<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }


    private synchronized void updateModelIndex() {
        currentModelIndex = (currentModelIndex + 1) % models.length;
    }
}