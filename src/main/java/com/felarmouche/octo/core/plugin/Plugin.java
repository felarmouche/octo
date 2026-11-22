package com.felarmouche.octo.core.plugin;

import java.util.Set;

/**
 * An interchangeable building block that performs one stage of a Search.
 * Plugins are stateless: they receive their typed settings on every call (see ADR 0001).
 *
 * @param <S> the Plugin's settings record
 */
public interface Plugin<S extends Record> {

    String id();

    /** The ids of the Listing Types this Plugin supports. */
    Set<String> listingTypes();

    Class<S> settingsType();

    default boolean supports(String listingTypeId) {
        return listingTypes().contains(listingTypeId);
    }
}
