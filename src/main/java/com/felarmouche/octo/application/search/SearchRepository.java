package com.felarmouche.octo.application.search;

import org.springframework.data.repository.ListCrudRepository;

import java.util.List;

/**
 * The stored Searches. Deleting one deletes its Listing History and seen Fingerprints (the foreign keys cascade).
 * {@code save} only updates: new Searches carry their id already, so they are inserted through the
 * {@link org.springframework.data.jdbc.core.JdbcAggregateTemplate}.
 */
interface SearchRepository extends ListCrudRepository<SearchRow, String> {

    List<SearchRow> findAllByOrderByCreatedAtAscIdAsc();

    List<SearchRow> findByActiveTrueOrderByCreatedAtAscIdAsc();
}
