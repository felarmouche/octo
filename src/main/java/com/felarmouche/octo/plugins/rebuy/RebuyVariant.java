package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.types.phone.Storage;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/** One storage/colour variant of a Reference Product, with Rebuy's purchase price per grade. */
record RebuyVariant(Storage storage, String colour, boolean bought, Map<RebuyGrade, Integer> pricesInCents) {

    RebuyVariant {
        Objects.requireNonNull(storage, "storage");
        colour = colour == null ? "" : colour.strip();
        pricesInCents = Map.copyOf(pricesInCents);
    }

    /** Rebuy's price at this grade; empty when Rebuy doesn't buy the variant or publishes no price. */
    OptionalInt price(RebuyGrade grade) {
        Integer price = bought ? pricesInCents.get(grade) : null;
        return price == null ? OptionalInt.empty() : OptionalInt.of(price);
    }

    /** The highest price for a phone that reaches at most {@code best}; empty when Rebuy doesn't buy it. */
    OptionalInt bestPrice(RebuyGrade best) {
        if (!bought) return OptionalInt.empty();
        return pricesInCents.entrySet().stream()
                .filter(e -> e.getKey().isNoBetterThan(best))
                .mapToInt(Map.Entry::getValue)
                .max();
    }
}
