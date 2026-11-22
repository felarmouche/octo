-- Each Search runs on its own Schedule. The next run time is stored, so a restart neither bursts nor pauses.
-- Searches stored before get a five-minute Schedule, the former global interval, and are due at once.
alter table search
    add column schedule    jsonb       not null default '{"intervalMinutes": 5, "window": null, "weekdays": []}',
    add column next_run_at timestamptz not null default now();

alter table search
    alter column schedule drop default,
    alter column next_run_at drop default;

create index search_due on search (next_run_at) where active;
