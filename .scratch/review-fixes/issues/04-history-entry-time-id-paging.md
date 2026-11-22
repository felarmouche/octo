# 04: Listing History entries carry recorded time and id, and are paged

**What to build:** The Listing History endpoint returns, per entry, its id and the time it was recorded (both are stored but dropped today), newest first. It is paged, so a Search with the keep-all Retention can't grow the response without limit. A frontend can render the history view page by page and link to a single entry.

**Blocked by:** refactor/11 (Discovery and history endpoints) committed

**Status:** resolved

- [x] Each history entry has an id and its recorded time
- [x] Entries are ordered by recorded time, newest first
- [x] The endpoint takes page and page size, with a sensible default and maximum, and returns whether more entries exist
- [x] API tests cover ordering, paging and an empty history
