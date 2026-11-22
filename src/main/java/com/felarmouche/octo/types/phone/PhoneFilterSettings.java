package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.listingtype.PriceRange;

import java.util.Set;

/**
 * The phone Listing Type Filter. Every setting is optional; unknown values always pass.
 *
 * @param price      price range; null means any price
 * @param minBattery minimum battery health in percent; null means any
 * @param storages   allowed storage sizes; empty or null means all
 * @param conditions allowed Phone Conditions; empty or null means all
 */
public record PhoneFilterSettings(PriceRange price, Integer minBattery, Set<Storage> storages,
                                  Set<PhoneCondition> conditions) {

    public static final PhoneFilterSettings NONE = new PhoneFilterSettings(null, null, null, null);

    public PhoneFilterSettings {
        price = price == null ? PriceRange.ANY : price;
        if (minBattery != null && (minBattery < 0 || minBattery > 100))
            throw new IllegalArgumentException("minBattery must lie within 0..100");
        storages = storages == null ? Set.of() : Set.copyOf(storages);
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    }
}
