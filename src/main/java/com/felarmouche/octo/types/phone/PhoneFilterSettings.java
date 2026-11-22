package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.listingtype.PriceRange;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Set;

/**
 * The phone Listing Type Filter. Every setting is optional; unknown values always pass.
 *
 * @param price      price range; null means any price
 * @param minBattery minimum battery health in percent; null means any
 * @param storages   allowed storage sizes; empty or null means all
 * @param conditions allowed Phone Conditions; empty or null means all
 */
public record PhoneFilterSettings(@Valid PriceRange price, @Min(0) @Max(100) Integer minBattery, Set<Storage> storages,
                                  Set<PhoneCondition> conditions) {

    public static final PhoneFilterSettings NONE = new PhoneFilterSettings(null, null, null, null);

    public PhoneFilterSettings {
        price = price == null ? PriceRange.ANY : price;
        storages = storages == null ? Set.of() : Set.copyOf(storages);
        conditions = conditions == null ? Set.of() : Set.copyOf(conditions);
    }
}
