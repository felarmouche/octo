# 02: New Plugin contracts and the seven-step run (generic tracer bullet)

**What to build:** A generic Search runs end to end on the new architecture, next to the old iPhone/Rebuy path, which stays untouched. The Search looks for "PS5 under 250 €" on Kleinanzeigen, has no Evaluator, and alerts via Telegram.

This ticket introduces the core contracts every later ticket builds on:
- **One Plugin contract**: an id, the supported Listing Types, and a typed settings record that is passed on every call. It has three kinds: Finder, Evaluator and Notifier.
- **Domain types**: the Listing (optional price, Marketplace Details, Fingerprint), the Verdict with details, and the Alert.
- **The generic Listing Type.**
- **The run** with its fixed steps:
  1. Find
  2. Deduplicate
  3. Evaluate, or BUY when there is no Evaluator
  4. Record in memory
  5. Notify, per notifier slot with the Decisions it reports

Kleinanzeigen and Telegram get new versions with typed settings. The old classes stay until ticket 07.

See spec sections "Plugin contract", "Listing, Fingerprint, Verdict, Alert", "The run", "Kleinanzeigen Finder" and "Telegram Notifier".

**Blocked by:** 01

**Status:** ready-for-agent

- [x] The core contracts depend on nothing else in Octo and have no Spring dependency
- [x] The Fingerprint is a SHA-256 over the normalised marketplace, title, description and price; a re-post with the same text and price is recognised, and a price drop counts as new
- [x] Without an Evaluator, every survivor gets BUY "Alle Filter bestanden"
- [x] A failing Finder, a failing ad detail page or a failing Notifier is recorded in the run status and doesn't stop the rest (no NPE on unloadable pages)
- [x] Deduplication remembers every Fingerprint across runs, not just the last run
- [x] Each notifier slot only receives Alerts with the Decisions it reports
- [x] Run-seam tests with fake Finders and Notifiers cover the points above
- [x] Kleinanzeigen parsing is tested against saved result and detail HTML fixtures
- [x] ADR 0001 is written: stateless Plugins with typed settings per call, settings stored as JSON per Search, forms from a generated schema
- [x] The old iPhone/Rebuy Search still runs unchanged

## Comments

Implemented. New contracts live in `core.domain`, `core.plugin` and `core.listingtype`, the generic Listing Type in `types.generic`, the new Plugins in `plugins.kleinanzeigen` / `plugins.telegram`, the page loader in `infra.html`, and the run in `application.search` (`SearchRun`, wired for the in-code PS5 Search by `InCodeSearches`). Exclude and Filter by type are not in the run yet (tickets 03/04). Seen and history are in memory (`InMemorySearchMemory`) until ticket 08. A Finder reports a skipped ad detail page as a problem in `Finder.Result`, and the run adds it to the run status. The Kleinanzeigen fixtures are trimmed by hand to the structure the parser reads, with no real seller data.
