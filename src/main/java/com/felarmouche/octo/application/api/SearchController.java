package com.felarmouche.octo.application.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.felarmouche.octo.application.plugin.ListingTypes;
import com.felarmouche.octo.application.search.HistoryPage;
import com.felarmouche.octo.application.search.RunStatus;
import com.felarmouche.octo.application.search.SearchDefinition;
import com.felarmouche.octo.application.search.SearchMemory;
import com.felarmouche.octo.application.search.SearchService;
import com.felarmouche.octo.application.search.StoredSearch;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.listingtype.ListingType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/** Create, read, edit, pause, resume and delete Searches, and read their Listing History. */
@RestController
@RequestMapping("/api/searches")
class SearchController {

    private final SearchService searches;
    private final SearchMemory memory;
    private final ListingTypes listingTypes;

    SearchController(SearchService searches, SearchMemory memory, ListingTypes listingTypes) {
        this.searches = searches;
        this.memory = memory;
        this.listingTypes = listingTypes;
    }

    /** A Search with its state, last run status (null until it has run) and next run time. */
    record SearchResponse(String id, boolean active, @JsonUnwrapped SearchDefinition definition, RunStatus lastRun,
                          Instant nextRun) {
    }

    /**
     * One Listing of the Listing History.
     *
     * @param display the Listing's attributes as its Listing Type shows them, e.g. "Speicher: 128 GB"
     */
    record HistoryEntry(long id, Instant recordedAt, Listing listing, List<LabelledValue> display, Verdict verdict) {
    }

    /** One page of the Listing History, newest first, and whether older entries follow. */
    record HistoryResponse(List<HistoryEntry> entries, boolean hasMore) {
    }

    @GetMapping
    List<SearchResponse> list() {
        return searches.list().stream().map(this::response).toList();
    }

    /** What a new Search of this Listing Type starts from, to be edited and then POSTed. */
    @GetMapping("/template")
    SearchDefinition template(@RequestParam String listingType) {
        return searches.template(listingType);
    }

    @GetMapping("/{id}")
    SearchResponse get(@PathVariable String id) {
        return response(searches.get(id));
    }

    @PostMapping
    ResponseEntity<SearchResponse> create(@RequestBody SearchDefinition definition) {
        StoredSearch created = searches.create(definition);
        return ResponseEntity.created(URI.create("/api/searches/" + created.id())).body(response(created));
    }

    @PutMapping("/{id}")
    SearchResponse update(@PathVariable String id, @RequestBody SearchDefinition definition) {
        return response(searches.update(id, definition));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        searches.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pause")
    SearchResponse pause(@PathVariable String id) {
        return response(searches.pause(id));
    }

    @PostMapping("/{id}/resume")
    SearchResponse resume(@PathVariable String id) {
        return response(searches.resume(id));
    }

    /** Newest first, page by page. Pages count from 0. */
    @GetMapping("/{id}/history")
    HistoryResponse history(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "" + SearchService.DEFAULT_HISTORY_PAGE_SIZE) int size) {
        StoredSearch search = searches.get(id);
        ListingType<?> type = listingTypes.require(search.definition().listingType());
        HistoryPage history = searches.history(id, page, size);
        return new HistoryResponse(history.entries().stream()
                .map(e -> new HistoryEntry(e.id(), e.recordedAt(), e.listing(), type.describe(e.listing()), e.verdict()))
                .toList(), history.hasMore());
    }

    private SearchResponse response(StoredSearch search) {
        return new SearchResponse(search.id(), search.active(), search.definition(),
                memory.lastStatus(search.id()).orElse(null), search.nextRun());
    }
}
