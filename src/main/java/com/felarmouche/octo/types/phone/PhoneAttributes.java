package com.felarmouche.octo.types.phone;

import com.felarmouche.octo.core.domain.ListingAttributes;

import java.util.Optional;

/** The attributes of a Phone Listing. Every one is optional: absent means unknown. */
public record PhoneAttributes(
        Optional<Storage> storage,
        Optional<Battery> battery,
        Optional<String> colour,
        Optional<PhoneCondition> condition) implements ListingAttributes {

    public static final PhoneAttributes UNKNOWN =
            new PhoneAttributes(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public PhoneAttributes {
        storage = storage == null ? Optional.empty() : storage;
        battery = battery == null ? Optional.empty() : battery;
        colour = colour == null ? Optional.empty() : colour.filter(c -> !c.isBlank());
        condition = condition == null ? Optional.empty() : condition;
    }
}
