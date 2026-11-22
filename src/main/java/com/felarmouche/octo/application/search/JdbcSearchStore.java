package com.felarmouche.octo.application.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * The stored Searches: scalar columns plus jsonb for slots, filter settings and retention.
 * Deleting a Search deletes its Listing History and seen Fingerprints (the foreign keys cascade).
 */
@Repository
public class JdbcSearchStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String COLUMNS =
            "id, active, name, listing_type, type_filter, exclusion, finders, evaluator, notifiers, retention";

    private final JdbcClient jdbc;

    public JdbcSearchStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(StoredSearch search) {
        SearchDefinition d = search.definition();
        jdbc.sql("""
                        insert into search (id, active, name, listing_type, type_filter, exclusion, finders, evaluator,
                                            notifiers, retention, created_at)
                        values (?, ?, ?, ?, cast(? as jsonb), cast(? as jsonb), cast(? as jsonb), cast(? as jsonb),
                                cast(? as jsonb), cast(? as jsonb), now())""")
                .params(search.id(), search.active(), d.name(), d.listingType(), json(d.typeFilter()),
                        json(d.exclusion()), json(d.finders()), json(d.evaluator()), json(d.notifiers()),
                        json(d.retention()))
                .update();
    }

    /** Replaces the definition except the Listing Type; what was seen and the history stay. False when not found. */
    public boolean update(String id, SearchDefinition d) {
        return jdbc.sql("""
                        update search set name = ?, type_filter = cast(? as jsonb), exclusion = cast(? as jsonb),
                                          finders = cast(? as jsonb), evaluator = cast(? as jsonb),
                                          notifiers = cast(? as jsonb), retention = cast(? as jsonb)
                        where id = ?""")
                .params(d.name(), json(d.typeFilter()), json(d.exclusion()), json(d.finders()),
                        json(d.evaluator()), json(d.notifiers()), json(d.retention()), id)
                .update() == 1;
    }

    /** False when not found. */
    public boolean setActive(String id, boolean active) {
        return jdbc.sql("update search set active = ? where id = ?").params(active, id).update() == 1;
    }

    /** False when not found. */
    public boolean delete(String id) {
        return jdbc.sql("delete from search where id = ?").param(id).update() == 1;
    }

    public Optional<StoredSearch> find(String id) {
        return jdbc.sql("select " + COLUMNS + " from search where id = ?").param(id).query(this::map).optional();
    }

    /** Oldest first. */
    public List<StoredSearch> all() {
        return jdbc.sql("select " + COLUMNS + " from search order by created_at, id").query(this::map).list();
    }

    /** Oldest first. */
    public List<StoredSearch> active() {
        return jdbc.sql("select " + COLUMNS + " from search where active order by created_at, id")
                .query(this::map).list();
    }

    private StoredSearch map(ResultSet rs, int row) throws SQLException {
        String evaluator = rs.getString("evaluator");
        return new StoredSearch(rs.getString("id"), rs.getBoolean("active"), new SearchDefinition(
                rs.getString("name"),
                rs.getString("listing_type"),
                JSON.readTree(rs.getString("type_filter")),
                JSON.readTree(rs.getString("exclusion")),
                JSON.readValue(rs.getString("finders"), new TypeReference<List<SearchDefinition.Slot>>() {
                }),
                evaluator == null ? null : JSON.readValue(evaluator, SearchDefinition.Slot.class),
                JSON.readValue(rs.getString("notifiers"), new TypeReference<List<SearchDefinition.NotifierSlot>>() {
                }),
                JSON.readTree(rs.getString("retention"))));
    }

    private static String json(Object value) {
        return value == null ? null : JSON.writeValueAsString(value);
    }
}
