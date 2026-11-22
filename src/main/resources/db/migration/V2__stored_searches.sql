-- Searches are stored now, with their slots and settings as JSON.
-- The rows of the former in-code Search have no definition and are dropped with what it had seen.
delete from listing_history;
delete from seen_fingerprint;
delete from search;

alter table search
    add column name         text    not null,
    add column listing_type text    not null,
    add column active       boolean not null,
    add column type_filter  jsonb   not null,
    add column exclusion    jsonb   not null,
    add column finders      jsonb   not null,
    add column evaluator    jsonb,
    add column notifiers    jsonb   not null,
    add column retention    jsonb   not null,
    add column created_at   timestamptz not null;

-- Deleting a Search deletes its Listing History and what it has seen.
alter table seen_fingerprint
    add foreign key (search_id) references search (id) on delete cascade;
alter table listing_history
    add foreign key (search_id) references search (id) on delete cascade;
