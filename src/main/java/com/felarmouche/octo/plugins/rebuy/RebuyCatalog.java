package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.types.phone.Storage;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** The variants of one Reference Product and Rebuy's purchase prices, parsed from its product page. */
final class RebuyCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DTO_KEY = "\"productListViewDto\"";

    private final Map<Storage, Map<String, RebuyVariant>> variants;

    private RebuyCatalog(Map<Storage, Map<String, RebuyVariant>> variants) {
        this.variants = variants;
    }

    /** @throws IllegalStateException when the page holds no product data */
    static RebuyCatalog parse(Document page) {
        Map<Storage, Map<String, RebuyVariant>> variants = new EnumMap<>(Storage.class);
        for (JsonNode node : products(page)) {
            toVariant(node).ifPresent(v -> variants.computeIfAbsent(v.storage(), s -> new LinkedHashMap<>())
                    .merge(colourKey(v.colour()), v, RebuyCatalog::better));
        }
        if (variants.isEmpty()) throw new IllegalStateException("Rebuy-Seite ohne Produkte");
        variants.replaceAll((storage, byColour) -> Collections.unmodifiableMap(byColour));
        return new RebuyCatalog(Collections.unmodifiableMap(variants));
    }

    boolean offers(Storage storage) {
        return variants.containsKey(storage);
    }

    Set<Storage> storages() {
        return variants.keySet();
    }

    /** The colours of the Reference Product in Rebuy's wording, sorted. */
    Set<String> colours() {
        return variants.values().stream()
                .flatMap(m -> m.values().stream())
                .map(RebuyVariant::colour)
                .filter(c -> !c.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Rebuy's price for exactly this variant and grade; empty when there is none. */
    OptionalInt price(Storage storage, String colour, RebuyGrade grade) {
        RebuyVariant variant = variants.getOrDefault(storage, Map.of()).get(colourKey(colour));
        return variant == null ? OptionalInt.empty() : variant.price(grade);
    }

    /**
     * The highest price Rebuy pays for a phone that reaches at most {@code best}. Unknown storage means
     * any storage; an unknown colour, or one the catalog doesn't have, means any colour (optimistic).
     */
    OptionalInt bestPrice(Optional<Storage> storage, Optional<String> colour, RebuyGrade best) {
        List<RebuyVariant> candidates = storage
                .map(s -> List.copyOf(variants.getOrDefault(s, Map.of()).values()))
                .orElseGet(() -> variants.values().stream().flatMap(m -> m.values().stream()).toList());
        List<RebuyVariant> inColour = colour
                .map(c -> candidates.stream().filter(v -> colourKey(v.colour()).equals(colourKey(c))).toList())
                .orElse(List.of());
        return (inColour.isEmpty() ? candidates : inColour).stream()
                .map(v -> v.bestPrice(best))
                .flatMapToInt(OptionalInt::stream)
                .max();
    }

    private static JsonNode products(Document page) {
        for (Element script : page.select("script")) {
            String data = script.data();
            int idx = data.indexOf(DTO_KEY);
            if (idx < 0) continue;
            return MAPPER.readTree(extractJson(data, idx + DTO_KEY.length()))
                    .path("searchResponse").path("products").path("docs");
        }
        throw new IllegalStateException("productListViewDto nicht gefunden");
    }

    private static Optional<RebuyVariant> toVariant(JsonNode node) {
        Map<RebuyGrade, Integer> prices = new EnumMap<>(RebuyGrade.class);
        for (RebuyGrade grade : RebuyGrade.values()) {
            int cents = node.path("purchase_" + grade.code() + "_price").asInt();
            if (cents > 0) prices.put(grade, cents);
        }
        boolean bought = node.path("allowed_purchase").asBoolean()
                && !node.path("purchase_stop").asBoolean()
                && node.path("is_purchaseable").asBoolean();
        return Storage.parse(node.path("memory_size").asString())
                .map(storage -> new RebuyVariant(storage, node.path("originalColor").asString(), bought, prices));
    }

    /** Two entries for the same variant: prefer the one Rebuy buys, then the higher price. */
    private static RebuyVariant better(RebuyVariant a, RebuyVariant b) {
        if (a.bought() != b.bought()) return a.bought() ? a : b;
        return a.bestPrice(RebuyGrade.A0).orElse(0) >= b.bestPrice(RebuyGrade.A0).orElse(0) ? a : b;
    }

    private static String colourKey(String colour) {
        return colour == null ? "" : colour.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
