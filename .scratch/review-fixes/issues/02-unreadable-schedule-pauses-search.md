# 02: An unreadable Schedule pauses the Search instead of retrying every 30 s

**What to build:** When a stored Schedule of a Search can't be read, the scheduler today leaves its next run time unchanged, so the Search is picked up again on every tick, fails again and logs a warning each time. Instead, the Search is paused and its run status says why, so the operator (and later the frontend) sees the reason and can fix the Schedule.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] A Search whose Schedule can't be read is paused on the first failed attempt
- [ ] Its run status records a failure with a message naming the unreadable Schedule
- [ ] It is not picked up again until it is resumed with a valid Schedule
- [ ] The warning is logged once, not on every tick
- [ ] Other due Searches still run on the same tick
