package com.felarmouche.octo.application.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.felarmouche.octo.core.domain.Decision;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * A Search as the user defines it and as it is stored: Plugins by id, every settings object as JSON.
 * {@link SearchService} turns it into a runnable {@link Search}, which validates it.
 *
 * The API refuses unknown fields (a misspelled "evaluator" must not save a Search without one), except the
 * read-only fields a GET returns, so a read Search can be sent back as an edit.
 *
 * @param evaluator null when the Search has no Evaluator
 */
@JsonIgnoreProperties({"id", "active", "lastRun", "nextRun"})
public record SearchDefinition(
        String name,
        String listingType,
        JsonNode typeFilter,
        JsonNode exclusion,
        List<Slot> finders,
        Slot evaluator,
        List<NotifierSlot> notifiers,
        JsonNode schedule,
        JsonNode retention) {

    public SearchDefinition {
        finders = finders == null ? List.of() : List.copyOf(finders);
        notifiers = notifiers == null ? List.of() : List.copyOf(notifiers);
    }

    /** A Finder or the Evaluator: the Plugin's id and its settings. */
    public record Slot(String plugin, JsonNode settings) {
    }

    /** A Notifier, the Decisions it reports, and its settings. */
    public record NotifierSlot(String plugin, Set<Decision> decisions, JsonNode settings) {
        public NotifierSlot {
            decisions = decisions == null ? Set.of() : Set.copyOf(decisions);
        }
    }
}
