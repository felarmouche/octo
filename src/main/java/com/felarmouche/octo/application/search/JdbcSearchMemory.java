package com.felarmouche.octo.application.search;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.felarmouche.octo.core.domain.Fingerprint;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.ListingAttributes;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.types.phone.PhoneAttributes;
import com.felarmouche.octo.types.phone.PhoneListingType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Keeps what a Search remembers between runs in PostgreSQL, so it survives restarts. */
@Repository
public class JdbcSearchMemory implements SearchMemory {

    /** Listing and Verdict as stored JSON. Listing attributes carry their Listing Type's id as "type". */
    private static final JsonMapper JSON = JsonMapper.builder()
            .addMixIn(ListingAttributes.class, TypedAttributes.class)
            .registerSubtypes(new NamedType(PhoneAttributes.class, PhoneListingType.ID))
            .changeDefaultVisibility(v -> v.withIsGetterVisibility(Visibility.NONE))
            .build();

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    private interface TypedAttributes {
    }

    private final JdbcClient jdbc;

    public JdbcSearchMemory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean hasSeen(String searchId, Fingerprint fingerprint) {
        return jdbc.sql("select exists (select 1 from seen_fingerprint where search_id = ? and fingerprint = ?)")
                .params(searchId, fingerprint.value())
                .query(Boolean.class)
                .single();
    }

    @Override
    @Transactional
    public void remember(String searchId, Collection<Fingerprint> fingerprints) {
        for (Fingerprint fingerprint : fingerprints) {
            jdbc.sql("insert into seen_fingerprint (search_id, fingerprint) values (?, ?) on conflict do nothing")
                    .params(searchId, fingerprint.value())
                    .update();
        }
    }

    @Override
    @Transactional
    public void record(String searchId, List<JudgedListing> judged, Retention retention, Instant recordedAt) {
        for (JudgedListing j : judged) {
            jdbc.sql("""
                            insert into listing_history (search_id, recorded_at, decision, listing, verdict)
                            values (?, ?, ?, cast(? as jsonb), cast(? as jsonb))""")
                    .params(searchId, Timestamp.from(recordedAt), j.verdict().decision().name(),
                            JSON.writeValueAsString(j.listing()), JSON.writeValueAsString(j.verdict()))
                    .update();
        }
        applyRetention(searchId, retention);
    }

    /** Keep only the configured Decisions, then the newest N. */
    private void applyRetention(String searchId, Retention retention) {
        if (!retention.decisions().isEmpty()) {
            jdbc.sql("delete from listing_history where search_id = :searchId and decision not in (:decisions)")
                    .param("searchId", searchId)
                    .param("decisions", retention.decisions().stream().map(Enum::name).toList())
                    .update();
        }
        retention.newest().ifPresent(newest -> jdbc.sql("""
                        delete from listing_history
                        where search_id = :searchId
                          and id not in (select id from listing_history where search_id = :searchId
                                         order by id desc limit :newest)""")
                .param("searchId", searchId)
                .param("newest", newest)
                .update());
    }

    @Override
    public HistoryPage history(String searchId, int page, int size) {
        // One row more than the page holds tells whether more entries exist.
        List<RecordedListing> rows = jdbc.sql("""
                        select id, recorded_at, listing, verdict from listing_history
                        where search_id = :searchId
                        order by recorded_at desc, id desc
                        limit :limit offset :offset""")
                .param("searchId", searchId)
                .param("limit", size + 1)
                .param("offset", (long) page * size)
                .query((rs, row) -> new RecordedListing(
                        rs.getLong("id"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        JSON.readValue(rs.getString("listing"), Listing.class),
                        JSON.readValue(rs.getString("verdict"), Verdict.class)))
                .list();
        boolean hasMore = rows.size() > size;
        return new HistoryPage(hasMore ? rows.subList(0, size) : rows, hasMore);
    }

    @Override
    public void saveStatus(String searchId, RunStatus status) {
        jdbc.sql("""
                        update search set last_run_at = ?, last_run_ok = ?, last_run_messages = cast(? as jsonb)
                        where id = ?""")
                .params(Timestamp.from(status.finishedAt()), status.ok(),
                        JSON.writeValueAsString(status.messages()), searchId)
                .update();
    }

    @Override
    public Optional<RunStatus> lastStatus(String searchId) {
        return jdbc.sql("""
                        select last_run_at, last_run_ok, last_run_messages from search
                        where id = ? and last_run_at is not null""")
                .param(searchId)
                .query((rs, row) -> new RunStatus(
                        rs.getTimestamp("last_run_at").toInstant(),
                        rs.getBoolean("last_run_ok"),
                        List.of(JSON.readValue(rs.getString("last_run_messages"), String[].class))))
                .optional();
    }
}
