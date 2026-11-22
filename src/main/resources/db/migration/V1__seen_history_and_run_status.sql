-- One row per Search. For now it holds only the last run status; the Search itself follows.
create table search
(
    id                text primary key,
    last_run_at       timestamptz,
    last_run_ok       boolean,
    last_run_messages jsonb
);

-- Every Fingerprint a Search has seen. Separate from the Listing History, so it outlives the Listings.
create table seen_fingerprint
(
    search_id   text not null,
    fingerprint text not null,
    primary key (search_id, fingerprint)
);

-- The Listings a Search keeps with their Verdicts, limited by its retention rule.
create table listing_history
(
    id          bigserial primary key,
    search_id   text        not null,
    recorded_at timestamptz not null default now(),
    decision    text        not null,
    listing     jsonb       not null,
    verdict     jsonb       not null
);

create index listing_history_search_id on listing_history (search_id, id);
