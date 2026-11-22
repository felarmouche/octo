package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import com.felarmouche.octo.core.shared.listing.PhoneListing;
import com.felarmouche.octo.infra.ListingImageLoader;
import com.felarmouche.octo.infra.ai.AiClient;
import com.felarmouche.octo.infra.ai.AiRequest;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class GeminiRebuyIphoneAssessment {

    public static final String UNKNOWN_COLOR = "UNKNOWN_COLOR";
    // Schema erzwingt integer, daher Zahl statt String als "nicht bestimmbar"
    public static final int UNKNOWN_BATTERY_CAPACITY = -1;

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

    private final AiClient ai;
    private final String systemPrompt;
    private final ListingImageLoader imageLoader;

    public GeminiRebuyIphoneAssessment(AiClient ai,
                                       @Value("classpath:prompts/condition-inspector.st") Resource systemPrompt,
                                       ListingImageLoader imageLoader) {
        this.ai = ai;
        this.systemPrompt = read(systemPrompt);
        this.imageLoader = imageLoader;
    }

    /**
     * @throws IllegalStateException wenn keine Bilder ladbar sind oder alle Modelle fehlschlagen
     */
    public ConditionAssessment assessCondition(PhoneListing phone, RebuyIPhoneCatalog catalog) {
        Objects.requireNonNull(phone, "phone must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");

        List<Media> images = imageLoader.load(phone.getImages(), MAX_IMAGES);
        if (images.isEmpty()) {
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

        // condition ist Pflicht für die Preisberechnung
        return ai.call(new AiRequest<>(systemPrompt, USER_TEMPLATE, params, images,
                ConditionAssessment.class, a -> a.condition() != null));
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt " + resource + " nicht lesbar", e);
        }
    }

    private static String join(Collection<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
