# 10: Per-Search Schedule

**What to build:** Each Search runs on its own Schedule: an interval, an optional hour window and weekdays, in the global timezone (Europe/Berlin, from config).

- The next run is planned from the end of the previous one.
- A scheduler tick starts the Searches that are due, each on its own virtual thread. One Search never runs twice at the same time; different Searches run in parallel.
- The next run time is stored, so a restart neither causes a burst of runs nor a long pause.
- A new or resumed Search is due immediately, if it is inside its window.

See spec "Schedule" and user stories 17–23.

**Blocked by:** 09

**Status:** ready-for-agent

- [ ] The Schedule is part of the Search in the API and is validated
- [ ] Unit tests for the next-run calculation: inside the window, crossing the end of the window, excluded weekdays, timezone and DST
- [ ] A slow Search doesn't delay others, and a Search never overlaps itself
- [ ] The global fixed-delay property is gone
