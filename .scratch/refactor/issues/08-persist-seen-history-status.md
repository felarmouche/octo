# 08: Seen, history and run status survive restarts

**What to build:** Octo runs with PostgreSQL. The two are started together via docker compose, and Flyway manages the schema. For the in-code Searches, three things are stored:
- the seen Fingerprints,
- the Listing History, with each Search's retention rule (keep the newest N, keep only certain Decisions, or both),
- the last run status.

After a restart, nothing already seen is alerted again.

See spec "Persistence" and user stories 32, 72–73, 75, 82–83.

**Blocked by:** 07

**Status:** ready-for-agent

- [x] `docker compose up` starts Octo and PostgreSQL, and the schema is applied on startup
- [x] Restarting Octo doesn't re-alert Listings seen before the restart
- [x] Fingerprints stay remembered after their Listing was removed from the Listing History
- [x] NOT_EVALUATABLE Listings are recorded but not remembered as seen
- [x] Retention is applied after each run; filter rejections are recorded, Deduplication drops are not
- [x] Run-seam tests against PostgreSQL (Testcontainers)
