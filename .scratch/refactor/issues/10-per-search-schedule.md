# 10: Per-Search Schedule

**What to build:** Each Search runs on its own Schedule: an interval, an optional hour window and weekdays, in the global timezone (Europe/Berlin, from config).

- The next run is planned from the end of the previous one.
- A scheduler tick starts the Searches that are due, each on its own virtual thread. One Search never runs twice at the same time; different Searches run in parallel.
- The next run time is stored, so a restart neither causes a burst of runs nor a long pause.
- A new or resumed Search is due immediately, if it is inside its window.

See spec "Schedule" and user stories 17–23.

**Blocked by:** 09

**Status:** resolved

- [x] The Schedule is part of the Search in the API and is validated
- [x] Unit tests for the next-run calculation: inside the window, crossing the end of the window, excluded weekdays, timezone and DST
- [x] A slow Search doesn't delay others, and a Search never overlaps itself
- [x] The global fixed-delay property is gone

## Comments

- Done: `Schedule` (`intervalMinutes`, optional `window` {from, to} with `to` exclusive, `weekdays`, empty = all) is stored as jsonb on `search`, decoded and validated by the `SettingsCodec` under `schedule.*`. `next_run_at` is stored (V3) and returned as `nextRun`. Times are read in `octo.timezone`, carried by the `Clock` bean.
- Create and resume plan the first allowed time from now; an edit plans from the end of the last run with the new Schedule; the end of a run plans from that end with the Schedule as stored then. A run missed while Octo was down, outside the window now, is moved to the next window instead of running.
- `SearchRunner` ticks every 30 s and starts each due Search on its own virtual thread, skipping ones still running. `octo.searches.interval` is gone; `octo.scheduler.first-tick-after` delays the first tick (tests set it to a day and call `startDue()`).
- Windows can't cross midnight (from must be before to).
