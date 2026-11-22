package com.felarmouche.octo.application.search;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.Instant;
import java.util.List;

/**
 * The stored Searches. Deleting one deletes its Listing History and seen Fingerprints (the foreign keys cascade).
 * {@code save} only updates: new Searches carry their id already, so they are inserted through the
 * {@link org.springframework.data.jdbc.core.JdbcAggregateTemplate}.
 */
interface SearchRepository extends ListCrudRepository<SearchRow, String> {

    List<SearchRow> findAllByOrderByCreatedAtAscIdAsc();

    /** The active Searches whose next run time has come, longest waiting first. */
    List<SearchRow> findByActiveTrueAndNextRunAtLessThanEqualOrderByNextRunAtAscIdAsc(Instant now);

    /** Only the next run time, so an edit saved during a run is kept. Does nothing for a deleted Search. */
    @Modifying
    @Query("update search set next_run_at = :nextRunAt where id = :id")
    void planRun(String id, Instant nextRunAt);
}
