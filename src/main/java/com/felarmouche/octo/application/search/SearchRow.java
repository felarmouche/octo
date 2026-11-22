package com.felarmouche.octo.application.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

/**
 * A Search as one row of the {@code search} table: scalar columns plus jsonb for slots, filter settings,
 * Schedule and retention ({@link JsonbConverters}), and when it runs next. The last run columns are not mapped here, so saving a Search
 * never touches them; {@link JdbcSearchMemory} owns them.
 */
@Table("search")
record SearchRow(
        @Id String id,
        boolean active,
        String name,
        String listingType,
        JsonNode typeFilter,
        JsonNode exclusion,
        JsonNode finders,
        JsonNode evaluator,
        JsonNode notifiers,
        JsonNode schedule,
        JsonNode retention,
        Instant nextRunAt,
        Instant createdAt) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static SearchRow of(StoredSearch search, Instant createdAt) {
        SearchDefinition d = search.definition();
        return new SearchRow(search.id(), search.active(), d.name(), d.listingType(), d.typeFilter(), d.exclusion(),
                JSON.valueToTree(d.finders()), d.evaluator() == null ? null : JSON.valueToTree(d.evaluator()),
                JSON.valueToTree(d.notifiers()), d.schedule(), d.retention(), search.nextRun(), createdAt);
    }

    StoredSearch toStored() {
        return new StoredSearch(id, active, new SearchDefinition(name, listingType, typeFilter, exclusion,
                JSON.treeToValue(finders, new TypeReference<List<SearchDefinition.Slot>>() {
                }),
                evaluator == null || evaluator.isNull() ? null : JSON.treeToValue(evaluator, SearchDefinition.Slot.class),
                JSON.treeToValue(notifiers, new TypeReference<List<SearchDefinition.NotifierSlot>>() {
                }),
                schedule,
                retention), nextRunAt);
    }

    /** The new definition and next run time; id, active, Listing Type and creation time stay. */
    SearchRow with(SearchDefinition d, Instant nextRunAt) {
        return of(new StoredSearch(id, active, d, nextRunAt), createdAt);
    }

    SearchRow withActive(boolean active, Instant nextRunAt) {
        return new SearchRow(id, active, name, listingType, typeFilter, exclusion, finders, evaluator, notifiers,
                schedule, retention, nextRunAt, createdAt);
    }
}
