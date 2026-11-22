package com.felarmouche.octo.application.api;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.felarmouche.octo.application.search.RunStatus;
import com.felarmouche.octo.application.search.SearchDefinition;
import com.felarmouche.octo.application.search.SearchMemory;
import com.felarmouche.octo.application.search.SearchService;
import com.felarmouche.octo.application.search.StoredSearch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Create, read, edit, pause, resume and delete Searches. */
@RestController
@RequestMapping("/api/searches")
class SearchController {

    private final SearchService searches;
    private final SearchMemory memory;

    SearchController(SearchService searches, SearchMemory memory) {
        this.searches = searches;
        this.memory = memory;
    }

    /** A Search with its state and last run status (null until it has run). */
    record SearchResponse(String id, boolean active, @JsonUnwrapped SearchDefinition definition, RunStatus lastRun) {
    }

    @GetMapping
    List<SearchResponse> list() {
        return searches.list().stream().map(this::response).toList();
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

    private SearchResponse response(StoredSearch search) {
        return new SearchResponse(search.id(), search.active(), search.definition(),
                memory.lastStatus(search.id()).orElse(null));
    }
}
