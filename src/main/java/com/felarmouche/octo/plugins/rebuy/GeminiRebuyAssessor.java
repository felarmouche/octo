package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.infra.ListingImageLoader;
import com.felarmouche.octo.infra.ai.AiClient;
import com.felarmouche.octo.infra.ai.AiRequest;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Asks the AI fallback chain for a {@link RebuyAssessment} of a phone's photos and text. */
@Component
class GeminiRebuyAssessor implements RebuyAssessor {

    private static final String USER_TEMPLATE = """
            Bewerte den Zustand des Smartphones.

            <verkaeuferangaben>
            Titel: {title}
            Beschreibung: {description}
            Preis: {price}
            Produktdaten: {marketplaceDetails}
            </verkaeuferangaben>

            Farbe: Wähle genau eine aus [{colours}]. Falls nicht bestimmbar: {unknown}
            Speicher: Wähle genau einen aus [{storages}]. Falls nicht bestimmbar: {unknown}
            Akkukapazität: Bestimme sofern ersichtlich die Akkukapazität. Falls nicht bestimmbar: {unknownBattery}
            """;

    private final AiClient ai;
    private final ListingImageLoader images;
    private final String systemPrompt;

    GeminiRebuyAssessor(AiClient ai, ListingImageLoader images,
                        @Value("classpath:prompts/condition-inspector.st") Resource systemPrompt) {
        this.ai = ai;
        this.images = images;
        this.systemPrompt = read(systemPrompt);
    }

    @Override
    public RebuyAssessment assess(Listing listing, RebuyCatalog catalog, int maxPhotos) {
        List<Media> photos = images.load(listing.images(), maxPhotos);
        if (photos.isEmpty()) throw new IllegalStateException("Keine Fotos ladbar");

        Map<String, Object> params = Map.of(
                "title", listing.title(),
                "description", listing.description(),
                "price", listing.priceInCents().map(c -> RebuyEvaluator.euros(c)).orElse("keine Angabe"),
                "marketplaceDetails", listing.marketplaceDetails().toString(),
                "colours", join(catalog.colours()),
                "storages", join(catalog.storages().stream().map(s -> s.label()).toList()),
                "unknown", RebuyAssessment.UNKNOWN,
                "unknownBattery", RebuyAssessment.UNKNOWN_BATTERY);

        // the grade is needed to look up the price
        return ai.call(new AiRequest<>(systemPrompt, USER_TEMPLATE, params, photos,
                RebuyAssessment.class, a -> a.grade() != null));
    }

    private static String join(Collection<?> values) {
        return values.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String read(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt " + resource + " nicht lesbar", e);
        }
    }
}
