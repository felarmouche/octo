package com.felarmouche.octo.application.search;

import java.time.Instant;

/** A Search as stored: its id, whether it is active, its definition and when it runs next. */
public record StoredSearch(String id, boolean active, SearchDefinition definition, Instant nextRun) {
}
