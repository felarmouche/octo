package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import ch.qos.logback.core.boolex.EvaluationException;
import com.felarmouche.octo.application.WebScraper;
import com.felarmouche.octo.core.evaluator.EvaluatorException;
import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.listing.PhoneListing.Memory;
import com.felarmouche.octo.plugins.evaluator.rebuy.RebuyEvaluator;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.Properties.RebuyGrade;
import com.felarmouche.octo.plugins.evaluator.rebuy.iphone.Properties.RebuyIPhoneModel;
import lombok.Getter;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Getter
public final class RebuyIPhoneCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DTO_KEY = "\"productListViewDto\"";

    private final String modelUrl;
    private final Instant loadedAt;
    private final Map<Memory, Map<String, RebuyIPhoneModel>> catalog;
    private final Map<Memory, Integer> maxPurchasePriceByMemory;
    private final Map<String, Integer> maxPurchasePriceByColor;
    private final Set<String> allColors;
    private final Set<Memory> allMemorySizes;
    private final Map<String, Pattern> colorPatterns;

    private RebuyIPhoneCatalog(String modelUrl, Map<Memory, Map<String, RebuyIPhoneModel>> catalog) {
        this.modelUrl = modelUrl;
        this.loadedAt = Instant.now();

        Map<Memory, Map<String, RebuyIPhoneModel>> copy = new EnumMap<>(Memory.class);
        catalog.forEach((memory, byColor) -> copy.put(memory, Map.copyOf(byColor)));
        this.catalog = Collections.unmodifiableMap(copy);

        Map<Memory, Integer> maxByMemory = new EnumMap<>(Memory.class);
        Map<String, Integer> maxByColor = new HashMap<>();
        Map<String, Pattern> patterns = new HashMap<>();
        Set<String> colors = new TreeSet<>();

        copy.forEach((memory, byColor) -> byColor.forEach((color, variant) -> {
            if (!color.isEmpty()) {
                colors.add(variant.color());
                patterns.computeIfAbsent(color, RebuyIPhoneCatalog::colorPattern);
            }
            if (memory == Memory.UNKNOWN || !variant.sellable()) return;
            variant.highestPurchasePriceInCent().ifPresent(price -> {
                maxByMemory.merge(memory, price, Math::max);
                if (!color.isEmpty()) maxByColor.merge(color, price, Math::max);
            });
        }));

        if (maxByMemory.isEmpty()) {
            throw new IllegalStateException("Keine ankaufbaren Produkte gefunden: " + modelUrl);
        }
        this.maxPurchasePriceByMemory = Collections.unmodifiableMap(maxByMemory);
        this.maxPurchasePriceByColor = Collections.unmodifiableMap(maxByColor);
        this.allColors = Collections.unmodifiableSet(colors);
        this.colorPatterns = Map.copyOf(patterns);
        this.allMemorySizes = catalog.keySet();
    }

    public static RebuyIPhoneCatalog load(String modelUrl) {
        if(modelUrl == null || modelUrl.isBlank()) throw new IllegalArgumentException("Rebuy Product Url must not be null or empty");
        Document doc = WebScraper.load(Objects.requireNonNull(modelUrl, "modelUrl"));
        if (doc == null) {
            throw new IllegalStateException("Rebuy-Seite nicht ladbar: " + modelUrl);
        }
        return new RebuyIPhoneCatalog(modelUrl, parse(doc));
    }

    // ------------------------------------------------------------------ Vorfilter

    /** Höchster Ankaufspreis, wenn das Gerät maximal `bestGrade` erreicht (A0 = bester). */
    public OptionalInt bestCasePurchasePriceInCent(Memory memory, String color, RebuyGrade bestGrade) {
        return candidates(memory, colorKey(color)).stream()
                .filter(RebuyIPhoneModel::sellable)
                .flatMap(v -> v.purchasePriceInCent().entrySet().stream())
                .filter(e -> e.getKey().ordinal() >= bestGrade.ordinal())
                .mapToInt(Map.Entry::getValue)
                .max();
    }

    // Farbe bekannt -> nur diese Farbe; nicht im Katalog -> alle Farben (optimistisch)
    private List<RebuyIPhoneModel> candidates(Memory memory, String key) {
        boolean memoryKnown = memory != null && memory != Memory.UNKNOWN;
        List<Map<String, RebuyIPhoneModel>> byColor = memoryKnown
                ? List.of(catalog.getOrDefault(memory, Map.of()))
                : catalog.entrySet().stream()
                .filter(e -> e.getKey() != Memory.UNKNOWN)
                .map(Map.Entry::getValue)
                .toList();

        if (!key.isEmpty()) {
            List<RebuyIPhoneModel> exact = byColor.stream()
                    .map(m -> m.get(key)).filter(Objects::nonNull).toList();
            if (!exact.isEmpty()) return exact;
        }
        return byColor.stream().flatMap(m -> m.values().stream()).toList();
    }

    public boolean offersStorage(Memory memory) {
        return catalog.containsKey(memory);
    }

    public OptionalInt highestPurchasePriceInCent(Listing listing) {
        return highestPurchasePriceInCent(
                Memory.fromListing(listing),
                detectColor(listing.getTitle(), listing.getDescription()).orElse(null));
    }

    public OptionalInt highestPurchasePriceInCent(Memory memory, String color) {
        boolean memoryKnown = memory != null && memory != Memory.UNKNOWN;
        String key = colorKey(color);
        boolean colorKnown = !key.isEmpty();

        if (memoryKnown && colorKnown) {
            Optional<RebuyIPhoneModel> variant = variant(memory, key);
            if (variant.isPresent()) {
                RebuyIPhoneModel v = variant.get();
                return v.sellable() ? v.highestPurchasePriceInCent() : OptionalInt.empty();
            }
        }
        if (memoryKnown) return toOptionalInt(maxPurchasePriceByMemory.get(memory));
        if (colorKnown) return toOptionalInt(maxPurchasePriceByColor.get(key));
        return maxPurchasePriceByMemory.values().stream().mapToInt(Integer::intValue).max();
    }

    // Titel vor Beschreibung; liefert den normalisierten Farbschlüssel
    public Optional<String> detectColor(String title, String description) {
        Optional<String> fromTitle = uniqueColor(title);
        return fromTitle.isPresent() ? fromTitle : uniqueColor(description);
    }

    private Optional<String> uniqueColor(String text) {
        if (text == null || text.isBlank()) return Optional.empty();

        Set<String> found = colorPatterns.entrySet().stream()
                .filter(e -> e.getValue().matcher(text).find())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // "space grau" schlägt "grau"
        List<String> mostSpecific = found.stream()
                .filter(c -> found.stream().noneMatch(o -> !o.equals(c) && o.contains(c)))
                .toList();

        return mostSpecific.size() == 1 ? Optional.of(mostSpecific.get(0)) : Optional.empty();
    }

    // ------------------------------------------------------------------ Nach KI-Einschätzung

    public Optional<RebuyIPhoneModel> variant(Memory memory, String color) {
        return Optional.ofNullable(catalog.getOrDefault(memory, Map.of()).get(colorKey(color)));
    }

    public OptionalInt purchasePriceInCent(Memory memory, String color, RebuyGrade grade) {
        return variant(memory, color)
                .filter(RebuyIPhoneModel::sellable)
                .map(v -> v.purchasePriceInCent(grade))
                .orElse(OptionalInt.empty());
    }

    // Farbe unklar: conservative = niedrigster Preis über alle Farben
    public OptionalInt purchasePriceInCent(Memory memory, RebuyGrade grade, boolean conservative) {
        IntStream prices = catalog.getOrDefault(memory, Map.of()).values().stream()
                .filter(RebuyIPhoneModel::sellable)
                .map(v -> v.purchasePriceInCent(grade))
                .flatMapToInt(OptionalInt::stream);
        return conservative ? prices.min() : prices.max();
    }

    // ------------------------------------------------------------------ Parsing

    private static Map<Memory, Map<String, RebuyIPhoneModel>> parse(Document doc) {
        for (Element script : doc.select("script")) {
            String data = script.data();
            int idx = data.indexOf(DTO_KEY);
            if (idx < 0) continue;

            JsonNode docs = MAPPER.readTree(extractJson(data, idx + DTO_KEY.length()))
                    .path("searchResponse").path("products").path("docs");

            Map<Memory, Map<String, RebuyIPhoneModel>> result = new EnumMap<>(Memory.class);
            for (JsonNode node : docs) {
                RebuyIPhoneModel iPhone = toModel(node);
                result.computeIfAbsent(iPhone.memory(), k -> new HashMap<>())
                        .merge(colorKey(iPhone.color()), iPhone, RebuyIPhoneCatalog::better);
            }
            if (result.isEmpty()) {
                throw new IllegalStateException("productListViewDto ohne Produkte");
            }
            return result;
        }
        throw new IllegalStateException("productListViewDto nicht gefunden");
    }

    private static RebuyIPhoneModel toModel(JsonNode node) {
        Map<RebuyGrade, Integer> prices = new EnumMap<>(RebuyGrade.class);
        for (RebuyGrade grade : RebuyGrade.values()) {
            int cents = node.path("purchase_" + grade.code() + "_price").asInt();
            if (cents > 0) prices.put(grade, cents);
        }
        boolean sellable = node.path("allowed_purchase").asBoolean()
                && !node.path("purchase_stop").asBoolean()
                && node.path("is_purchaseable").asBoolean();

        return new RebuyIPhoneModel(
                node.path("id").asLong(),
                node.path("name").asString(),
                Memory.fromString(node.path("memory_size").asString()),
                node.path("originalColor").asString(),
                sellable,
                prices);
    }

    private static RebuyIPhoneModel better(RebuyIPhoneModel a, RebuyIPhoneModel b) {
        if (a.sellable() != b.sellable()) return a.sellable() ? a : b;
        return a.highestPurchasePriceInCent().orElse(0) >= b.highestPurchasePriceInCent().orElse(0) ? a : b;
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    private static String colorKey(String color) {
        return color == null ? "" : color.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // Wortgrenzen: "rot" trifft nicht "Brot"; "space grau" trifft auch "Space-Grau"
    private static Pattern colorPattern(String key) {
        String body = Arrays.stream(key.split(" "))
                .map(Pattern::quote)
                .collect(Collectors.joining("[\\s-]*"));
        return Pattern.compile("(?<![\\p{L}\\d])" + body + "(?![\\p{L}\\d])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static OptionalInt toOptionalInt(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static String extractJson(String s, int from) {
        int start = s.indexOf('{', from);
        if (start < 0) throw new IllegalStateException("JSON-Start nicht gefunden");

        int depth = 0;
        boolean inString = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return s.substring(start, i + 1);
        }
        throw new IllegalStateException("JSON-Ende nicht gefunden");
    }
}