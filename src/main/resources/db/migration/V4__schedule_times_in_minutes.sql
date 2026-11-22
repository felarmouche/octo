-- Schedule window times are HH:mm now. Searches stored before hold them as HH:mm:ss, always with zero seconds.
update search
set schedule = jsonb_set(jsonb_set(schedule,
        '{window,from}', to_jsonb(left(schedule #>> '{window,from}', 5))),
        '{window,to}', to_jsonb(left(schedule #>> '{window,to}', 5)))
where jsonb_typeof(schedule -> 'window') = 'object';
