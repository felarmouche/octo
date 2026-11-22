package com.felarmouche.octo.application.plugin;

import com.felarmouche.octo.application.settings.InvalidSettingsException;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.types.generic.GenericListingType;
import com.felarmouche.octo.types.phone.PhoneListingType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** The Listing Types a Search can pick, by id. */
@Component
public class ListingTypes {

    private final List<ListingType<?>> all = List.of(new PhoneListingType(), new GenericListingType());

    public Optional<ListingType<?>> find(String id) {
        return all.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    /** @throws InvalidSettingsException on the field {@code listingType} when there is none with this id */
    public ListingType<?> require(String id) {
        return find(id).orElseThrow(() -> new InvalidSettingsException("listingType", "unknown Listing Type: " + id));
    }

    public List<ListingType<?>> all() {
        return all;
    }
}
