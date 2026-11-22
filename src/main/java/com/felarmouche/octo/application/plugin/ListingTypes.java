package com.felarmouche.octo.application.plugin;

import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.types.generic.GenericListingType;
import com.felarmouche.octo.types.phone.PhoneListingType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The Listing Types a Search can pick, by id. */
@Component
public class ListingTypes {

    private final Map<String, ListingType<?>> byId = Stream.of(new PhoneListingType(), new GenericListingType())
            .collect(Collectors.toUnmodifiableMap(ListingType::id, Function.identity()));

    public Optional<ListingType<?>> find(String id) {
        return Optional.ofNullable(id).map(byId::get);
    }
}
