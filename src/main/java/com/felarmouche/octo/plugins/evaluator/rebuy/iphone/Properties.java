package com.felarmouche.octo.plugins.evaluator.rebuy.iphone;

import com.felarmouche.octo.core.shared.listing.PhoneListing.Memory;

import java.util.*;

public final class Properties {

    private Properties() {}

    public enum RebuyGrade {
        A0, A1, A2, A3, A4, A5;

        public static Optional<RebuyGrade> fromCode(String value) {
            if (value == null) return Optional.empty();
            try {
                return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }

        public String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record RebuyIPhoneModel(long id, String name, Memory memory, String color, boolean sellable,
                                   Map<RebuyGrade, Integer> purchasePriceInCent) {

        public RebuyIPhoneModel {
            name = Objects.requireNonNullElse(name, "");
            memory = Objects.requireNonNullElse(memory, Memory.UNKNOWN);
            color = Objects.requireNonNullElse(color, "").trim();
            purchasePriceInCent = purchasePriceInCent == null ? Map.of() : Map.copyOf(purchasePriceInCent);
        }

        public OptionalInt highestPurchasePriceInCent() {
            return purchasePriceInCent.values().stream().mapToInt(Integer::intValue).max();
        }

        public OptionalInt purchasePriceInCent(RebuyGrade grade) {
            Integer price = purchasePriceInCent.get(grade);
            return price == null ? OptionalInt.empty() : OptionalInt.of(price);
        }
    }
}