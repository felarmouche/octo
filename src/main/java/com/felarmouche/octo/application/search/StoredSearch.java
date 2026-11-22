package com.felarmouche.octo.application.search;

/** A Search as stored: its id, whether it is active, and its definition. */
public record StoredSearch(String id, boolean active, SearchDefinition definition) {
}
