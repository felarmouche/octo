# Spec: From test setup to the target architecture

**Status:** ready-for-agent

## Problem Statement

Octo works, but only as a test setup. The one Search it runs is hardcoded: one Kleinanzeigen URL, one Rebuy Reference Product, one Telegram chat and one global interval. To watch a different item, I have to change code and redeploy. Nothing is stored: after a restart Octo forgets what it has seen and alerts old Listings again. In fact it only remembers the previous run anyway, so a Listing that drops off the results page and comes back gets alerted twice. I can't see why a Listing was rejected, and I can't tune thresholds without editing constants.

The Plugins are each built differently:
- The Finder gets its settings through the constructor.
- The Evaluator gets them as a method argument.
- The Notifier reads them from `application.properties`.

Adding a marketplace, a buyback service or a channel therefore means learning a new pattern each time. The Rebuy Evaluator mixes damage-word filtering, battery checks, AI infrastructure, catalog parsing and the buy decision in one place. It writes AI guesses back into the Listing and puts its findings (resale price, margin) only into a reason text.

There are also real bugs:
- A Listing without a price is marked BUY.
- One failing part stops the whole run.
- Two Searches with different Reference Products would keep evicting each other's catalog.
- The Telegram bot token is committed.

## Solution

Octo becomes what `docs/VISION.md` describes. I define Searches through an HTTP API (a frontend comes later). Each Search has:
- a name and one Listing Type,
- one or more Finders,
- an Exclusion Filter and a Listing Type Filter,
- an optional Evaluator,
- one or more Notifiers, each with the Decisions it reports,
- a Schedule and a retention rule for its Listing History.

Octo stores all of this and runs every active Search on its own Schedule. Every run goes through the same seven steps: Find, Deduplicate, Exclude, Filter by type, Evaluate, Record, Notify. A failing Finder or Notifier doesn't stop the rest. Every Listing gets a Verdict with a reason, and Evaluator findings are stored as labelled details. What a Search has seen, its Listing History and its last run status all survive restarts.

All Plugins look alike. Each one has:
- an id,
- the Listing Types it supports,
- a typed settings record that the backend describes as a schema for the frontend.

Adding a Plugin means writing one settings record and one Plugin class; nothing else in Octo changes. Rebuy is split along the vision's lines:
- **Damage words** become a phone Exclusion Preset.
- **Battery, condition and price range** become phone Listing Type Filter settings.
- **Generic AI infrastructure** (model fallback chain, cooldown, timeouts) becomes reusable.
- **The Rebuy Evaluator itself** keeps only what needs the Reference Product: the cheap pre-checks, the AI condition assessment and the margin decision.

## User Stories

### Searches
1. As the user, I want to create a Search with a name, so that I can tell my Searches apart.
2. As the user, I want to pick exactly one Listing Type when I create a Search, so that its Finders, filters and Evaluator agree on what a Listing carries.
3. As the user, I want the Listing Type of a Search to be fixed after creation, so that stored settings never become inconsistent with it.
4. As the user, I want to add one or more Finders to a Search, each with its own settings, so that one Search can watch several marketplaces.
5. As the user, I want to give the Kleinanzeigen Finder the marketplace's own search URL and a max number of Listings per run, so that price range, location and keywords are set where Kleinanzeigen already supports them.
6. As the user, I want to add one or more Notifiers to a Search, each with its own channel settings, so that I can get Alerts in more than one place.
7. As the user, I want to choose per Notifier which Decisions it reports (e.g. only BUY, or BUY and UNSURE), so that each channel only gets the Alerts I care about there.
8. As the user, I want to optionally pick one Evaluator for a Search and fill in its settings, so that Listings are judged for resale value when I need that.
9. As the user, I want a Search without an Evaluator to give every Listing that passes the filters the Decision BUY with the reason "passed all filters", so that I can watch items like "PS5 under 250 €" without a buyback service.
10. As the user, I want Octo to refuse a Search that uses a Plugin which doesn't support the Search's Listing Type, so that I can't build a Search that silently ignores Listings.
11. As the user, I want Octo to refuse a Search whose Plugin Settings are invalid (e.g. missing URL, negative margin), and to say which field is wrong, so that I fix it before it runs.
12. As the user, I want to edit a Search at any time and have the changes apply from the next run, so that I can tune it while it keeps running.
13. As the user, I want editing a Search to keep what it has already seen, so that tuning doesn't re-alert old Listings.
14. As the user, I want to pause and resume a Search without losing anything, so that I can stop alerts temporarily.
15. As the user, I want deleting a Search to delete its Listing History and what it has seen, so that nothing is left behind.
16. As the user, I want to list all my Searches with their state (active or paused) and last run status, so that I see at a glance what's running.

### Schedule
17. As the user, I want to set how often a Search runs (e.g. every 5 minutes), so that I control how fast I hear about new Listings.
18. As the user, I want to restrict a Search to hours of the day (e.g. 11:00–17:00), so that it doesn't run or alert when I can't act.
19. As the user, I want to restrict a Search to certain weekdays, so that it rests on days I don't buy.
20. As the user, I want all Schedules to use one global timezone (Europe/Berlin), so that "11:00" means what I think it means.
21. As the user, I want the next run of a Search to be planned from the end of the previous one, so that a Search never runs twice at the same time.
22. As the user, I want different Searches to run in parallel, so that one slow Search doesn't delay the others.
23. As the user, I want Schedules to continue correctly after a restart, so that a redeploy doesn't cause a burst of runs or a long pause.

### The run
24. As the user, I want every run to follow the same seven steps in the same order, so that I can predict and understand what happened.
25. As the user, I want each Finder to turn its marketplace's ads into Listings of the Search's Listing Type, so that the later steps don't care where a Listing came from.
26. As the user, I want phone attributes that a marketplace doesn't provide as structured fields (storage, battery) to be read from the title and description, so that every Finder benefits from the same text reading.
27. As the user, I want the marketplace's condition wording (e.g. "Sehr gut") mapped onto Octo's Phone Condition, so that filters and Evaluators work the same for every marketplace.
28. As the user, I want Listings whose Fingerprint the Search has already seen to be dropped, so that I'm never alerted twice about the same Listing.
29. As the user, I want a re-post with the same text and price to count as already seen, so that sellers re-posting don't spam me.
30. As the user, I want a Listing whose price dropped to count as new, so that I hear about the price drop.
31. As the user, I want the same phone on two marketplaces to count as two Listings, so that I accept two Alerts rather than miss one.
32. As the user, I want what a Search has seen to be remembered even after its Listing History has deleted the Listing, so that deleted Listings don't come back as new.
33. As the user, I want one failing Finder to not stop the other Finders or the rest of the run, and the failure to be shown on the Search, so that a broken marketplace doesn't blind me everywhere.
34. As the user, I want one failing ad detail page to only skip that ad, so that one bad page doesn't lose the whole result list.
35. As the user, I want one failing Notifier to not stop the other Notifiers, and the failure to be shown on the Search, so that a broken channel doesn't silence the others.
36. As the user, I want an Evaluator failure on one Listing to give that Listing NOT_EVALUATABLE and continue with the next, so that one bad Listing doesn't stop evaluation.
37. As the user, I want NOT_EVALUATABLE Listings to not be remembered as seen, so that they are evaluated again when a Finder finds them on a later run.
38. As the user, I want no retry limit for NOT_EVALUATABLE, so that retrying stops by itself once the ad is gone.
39. As the user, I want Listings to be stored with their Verdicts before Notifiers run, so that the Listing History is complete even if a Notifier fails.
40. As the user, I want scraping and AI calls to happen only for Listings that survived the cheap checks, so that runs are fast and AI costs stay low.

### Exclusion Filter
41. As the user, I want to enter my own excluded words per Search, so that I can reject Listings mentioning e.g. "Mietkauf".
42. As the user, I want to add the phone Exclusion Preset "damage" (display broken, back broken, water damage, iCloud lock, Face ID defect, dummy, for parts, defect) to a phone Search, so that I don't retype typical damage words.
43. As the user, I want an Exclusion Preset for wanted and swap ads ("suche", "tausche"), available for every Listing Type and matched on the title only, so that buy requests never reach me.
44. As the user, I want negated mentions like "kein Riss", "nicht gebrochen" or "Defekt: nein" to not trigger a rejection, so that good Listings aren't thrown out.
45. As the user, I want damage that concerns only an accessory (e.g. "Panzerglas gesprungen", "Hülle kaputt") to not trigger a rejection, so that a broken screen protector doesn't reject a good phone.
46. As the user, I want the same negation handling for my own words as for presets, so that my words behave predictably.
47. As the user, I want a rejected Listing's reason to name the rule and the matched text (e.g. `Display beschädigt ("Display gesprungen")`), so that I can see why and tune my filters.

### Listing Type Filter
48. As the user, I want to set an optional price range for any Listing Type, so that I can narrow down beyond the marketplace URL.
49. As the user, I want to set an optional minimum battery for phones, so that phones with a known weak battery are rejected.
50. As the user, I want to set optional allowed storage sizes for phones, so that I only get the variants I want.
51. As the user, I want to set optional allowed Phone Conditions, so that I can e.g. exclude defect phones.
52. As the user, I want Listings with an unknown value to pass the corresponding filter, so that the Evaluator can still find out from the photos.
53. As the user, I want a rejection reason like "Akku 74 % < 80 %", so that I see exactly which setting rejected the Listing.

### Rebuy Evaluator
54. As the user, I want to enter the Rebuy Reference Product URL, the min margin, the min AI confidence and the max number of photos sent to the AI as Rebuy settings per Search, so that I can tune them without code changes.
55. As the user, I want Listings whose margin is too low even in the best plausible condition to be rejected before any AI call, so that AI is only used on candidates.
56. As the user, I want a phone whose storage size doesn't exist for the Reference Product to get UNSURE with a hint about a possibly wrong model, so that I can check it myself.
57. As the user, I want the AI to assess the condition (Rebuy grade), colour, storage and battery from the photos and text, so that the resale price can be looked up.
58. As the user, I want an AI assessment below my min confidence to give UNSURE with the AI's reasoning, so that I can judge uncertain cases.
59. As the user, I want a functional defect found by the AI (display faults, Face ID, camera, battery "Service") to give NO_BUY, so that I'm not alerted to phones Rebuy won't take at a good price.
60. As the user, I want a broken phone (grade A5) to give NO_BUY, because Rebuy publishes no price for it.
61. As the user, I want a Listing with no Rebuy price for the assessed variant to give UNSURE, so that I can check manually.
62. As the user, I want BUY when Rebuy's price for the assessed variant minus the purchase price is at least my min margin, and NO_BUY otherwise, so that the Decision reflects my profit goal.
63. As the user, I want a Listing without a price (VB / 0 €) to still be assessed and then get UNSURE with the details "Rebuy zahlt X €" and "Max. Einkauf: Y €", so that I know what to offer and never get a false BUY.
64. As the user, I want the Evaluator's findings stored as labelled details in the Verdict (assessed condition, AI battery, resale price, purchase price, profit), and not written into the Listing, so that the seller's data and Octo's judgement stay separate.
65. As the user, I want Listings without photos, or whose photos can't be loaded, or that the AI can't assess because all models failed, to get NOT_EVALUATABLE, so that they are retried on a later run.
66. As the user, I want a broken or overloaded AI model to be skipped in favour of the next model in the fallback chain, so that evaluation continues during outages.
67. As the user, I want the Rebuy catalog cached per Reference Product, so that several Searches with different Reference Products don't reload catalogs on every run.
68. As the user, I want the last good catalog used when Rebuy can't be reached, so that a short Rebuy outage doesn't stop evaluation.

### Notifiers and Alerts
69. As the user, I want the Telegram Notifier's chat to be a Notifier setting, so that different Searches can alert different chats.
70. As the user, I want each Alert to show the title, price, link, Decision, reason, and the labelled values from the Listing Type (e.g. "Speicher: 128 GB") and the Evaluator (e.g. "Rebuy zahlt: 310 €"), so that I can decide without opening the ad.
71. As a developer, I want Notifiers to render a plain list of labelled values without knowing any Listing Type, so that a new Listing Type needs no Notifier change.

### Listing History and run status
72. As the user, I want every Listing of a run stored with its Verdict (including filter rejections, excluding Deduplication drops), so that I can see why each Listing was judged as it was.
73. As the user, I want to set a retention rule per Search (keep the newest N, keep only certain Decisions, or both), so that the history stays useful and small.
74. As the user, I want to read a Search's Listing History through the API, newest first, so that I can tune filters and settings.
75. As the user, I want each Search to show its last run time, whether it was ok, and the failure messages, so that I notice broken parts.

### Discovery for the frontend
76. As a frontend developer, I want to ask the backend which Listing Types exist, with their filter settings schema and Exclusion Presets, so that I can render the filter forms.
77. As a frontend developer, I want to ask the backend which Plugins exist for a Listing Type, with their kind, id and settings schema (types, constraints, labels, initial values), so that I can render Plugin forms without knowing any Plugin.
78. As a frontend developer, I want validation errors from the API to name the offending field, so that I can show them next to the right input.

### Operation and security
79. As the operator, I want the Telegram bot token, Gemini API key, AI model fallback chain, Rebuy catalog cache duration, timezone, database connection and API credentials to come from environment variables or `application.properties`, never from the frontend, so that secrets stay on the server.
80. As the operator, I want no secrets committed to the repository, so that a leaked repo doesn't leak access.
81. As the operator, I want the API protected by one username and password from the environment, so that nobody else can change my Searches when Octo runs on a server.
82. As the operator, I want Octo and its PostgreSQL database started together with docker compose, so that deployment is one command.
83. As the operator, I want database schema changes applied automatically on startup, so that upgrades don't need manual SQL.

### Extensibility
84. As a developer, I want to add a new Finder, Evaluator or Notifier by writing one settings record and one Plugin class, so that new Plugins are cheap.
85. As a developer, I want all Plugins to receive their settings the same way (typed, per call), so that learning one Plugin means knowing them all.
86. As a developer, I want a new Listing Type to be one self-contained unit (attributes, filter, presets, text reading, display), so that adding e.g. "console" doesn't touch the run or the Notifiers.
87. As a developer, I want reusable helpers for loading HTML pages, loading and shrinking images, and calling an AI model with a fallback chain, so that a new Plugin doesn't rebuild them.
88. As a developer, I want the domain and Plugin contracts to depend on nothing else in Octo, so that Plugins can't reach into orchestration or persistence.
89. As a developer, I want the application to start and the tests to run without a Gemini API key or network, so that I can work offline and CI stays green.

## Implementation Decisions

### Module layout and dependencies
- Five areas:
  - **core**: domain and Plugin contracts.
  - **types**: one unit per Listing Type (phone, generic).
  - **plugins**: one unit per Plugin (kleinanzeigen, rebuy, telegram).
  - **infra**: reusable technical helpers (HTML loading, image loading, AI fallback client).
  - **application**: Searches, the run, scheduling, persistence, registries, the HTTP API.
- Allowed dependencies: plugins → core, infra, types · types → core · application → all. Core depends on nothing else in Octo and has no Spring dependency. Nothing depends on application or plugins.
- The old `SearchRunner`, `SearchSettings`, `Search` (in-memory), the `XxxI`/`BaseXxx`/per-kind exceptions, `AIConnection` and the unused telegrambots dependency are removed. `WebScraper` moves to infra and throws on failure instead of returning null.

### Plugin contract (one way for all Plugins)
- A Plugin declares an id, the Listing Types it supports (Notifiers support any), and its settings type, which is a record.
- Three kinds:
  - **Finder**: given its settings and the Listing Type, returns Listings.
  - **Evaluator**: given one Listing and its settings, returns one Verdict.
  - **Notifier**: given a list of Alerts and its settings, sends them.
- Plugins are stateless singletons that receive typed settings on every call. They may hold technical resources (HTTP clients, caches, AI client) but no per-Search state. *(ADR candidate: hard to reverse and surprising; the alternative was one Plugin instance per Search built by a factory.)*
- Plugins never mutate Listings. Evaluators return Verdicts, and the run attaches them.
- One Plugin exception type carries the Plugin id. The run knows which step failed.
- Plugins are discovered automatically by a Plugin registry that indexes them by id, kind and Listing Type.

### Listing, Fingerprint, Verdict, Alert
- A Listing has:
  - marketplace, external id, title, description, link, images, the time it was found,
  - price in cents, which is optional (absent = no price given),
  - Marketplace Details (raw key/value fields in the marketplace's wording),
  - the attributes of its Listing Type (absent for generic),
  - its Fingerprint.
- The Fingerprint is a SHA-256 hash over the normalised marketplace, title, description and price. External id and images are not included. It is stored as a string.
- A Verdict has a Decision (BUY, NO_BUY, UNSURE, NOT_EVALUATABLE), a reason text and a list of details (label + value).
- An Alert is a Listing plus its Verdict plus the labelled values from the Listing Type's display and the Verdict details.

### Listing Types
- A Listing Type has an id, a filter settings type, and can:
  - complete a Listing (fill unknown attributes from title and description),
  - reject a Listing given its filter settings (returning a reason) or let it pass,
  - offer its Exclusion Presets,
  - describe a Listing as labelled values.
- **phone**:
  - Attributes: storage (64 GB … 2 TB), battery percent, colour, Phone Condition (NEW, VERY_GOOD, GOOD, OK, DEFECT). Every attribute is optional; absent means unknown.
  - Text reading: storage from title, then description; battery from description, then title, using the existing patterns ("Akkukapazität: 87 %", "99% Batteriezustand"). Range hints from structured fields ("Schwach (< 80%)") constrain the text value. A battery marked defect counts as 0 %.
  - Filter settings, all optional: price range, min battery, allowed storage sizes, allowed Phone Conditions.
  - Presets: "damage" (the existing damage rules).
- **generic**:
  - No attributes.
  - Filter settings: optional price range.
- Unknown values always pass the Listing Type Filter. A Listing without a price passes the price range.
- The run calls "complete" on every found Listing, so text reading is shared by all Finders. A Finder only maps its own structured fields onto the attributes.

### Exclusion Filter
- A fixed step of every Search, not a Plugin. Settings: own words plus the ids of selected Exclusion Presets.
- An Exclusion Preset is a named set of rules. Each rule has a reason, a pattern, and a flag saying whether it matches the title only.
- Presets belong to one Listing Type or to all Listing Types. "Wanted / swap" is the only preset for all types (title only).
- The user's own words become rules with the reason "Ausschlusswort", matched on word boundaries.
- One matcher applies to every rule:
  - Negation before the match ("kein", "nicht", "ohne", "nie", within the same sentence) cancels it.
  - Negation inside the match ("Display ist nicht gebrochen") cancels it, except "geht nicht an".
  - Negation after the match ("Defekt: nein", "Risse: keine") cancels it.
  - An accessory nearby ("Folie", "Schutzglas", "Panzerglas", "Displayschutz", "Hülle", "Case", "Cover" except "Coverglas") cancels it.
- The title is checked before the description. The first match rejects with NO_BUY and the reason `<rule reason> ("<matched text>")`.

### Plugin Settings mechanism
- Every settings type (Plugins, Listing Type Filters, Exclusion Filter) is a record with validation constraints and field descriptions. Record fields may provide initial form values. Once saved, values belong to the Search; there are no shared defaults.
- A JSON Schema is generated from each settings type (with the schema generator already on the classpath through Spring AI) and exposed to the frontend.
- Settings are stored as JSON per Search. A settings codec turns stored JSON into the typed record and validates it, both on save and before a run.

### Search aggregate
- A Search has:
  - id, name, Listing Type (fixed after creation), active flag,
  - finder slots (Plugin id + settings),
  - Exclusion Filter settings and Listing Type Filter settings,
  - optional evaluator slot,
  - notifier slots (Plugin id + Decisions to report + settings),
  - Schedule and retention rule,
  - last run status (time, ok, messages) and next run time.
- The Decisions to report live on the notifier slot. The run filters Alerts per slot, so no Notifier implements this filtering itself.
- On save, the Search service checks that each Plugin id exists, is of the right kind, supports the Listing Type, and has valid settings. At least one Finder and one Notifier are required.

### The run
1. **Find**: each Finder runs on its own. A failure is recorded and the run continues. Then "complete" is applied to every found Listing.
2. **Deduplicate**: drop Listings whose Fingerprint the Search has already seen.
3. **Exclude**: Exclusion Filter. A rejection gives NO_BUY with the reason.
4. **Filter by type**: Listing Type Filter. A rejection gives NO_BUY with the reason.
5. **Evaluate**: each survivor goes to the Evaluator. An exception for one Listing gives NOT_EVALUATABLE with the message. Without an Evaluator, every survivor gets BUY "Alle Filter bestanden".
6. **Record**: store all judged Listings in the Listing History and apply the retention rule. Remember every Fingerprint except NOT_EVALUATABLE ones.
7. **Notify**: each notifier slot gets the Alerts whose Decision it reports. A failure is recorded and the others continue.

Afterwards, the run status (ok when there were no failures, otherwise the messages) and the next run time are stored.

### Schedule
- A Schedule has an interval, an optional window of hours (from, to), and weekdays (empty = all).
- "Next run after" is a pure calculation: end of the previous run plus the interval. If that falls outside the window or on an excluded day, the next run is the start of the next allowed window.
- One global timezone comes from configuration (Europe/Berlin).
- A scheduler tick (every ~30 s) starts every active Search that is due and not already running, each on its own virtual thread. A newly created or resumed Search is due immediately (within its window).

### Rebuy Evaluator
- Settings: Reference Product URL, min margin in cents, min AI confidence, max photos.
- Order within one evaluation:
  1. **Pre-check (no AI):**
     - If the storage is known and not offered by the Reference Product → UNSURE "possibly wrong model".
     - If the price is known and the best-case Rebuy price at the best plausible grade minus the price is below the min margin → NO_BUY "Marge selbst bei <grade> zu gering". The best plausible grade is derived from the Phone Condition: VERY_GOOD → A1, GOOD → A2, OK → A3, otherwise A0.
     - If storage is known and the variant isn't bought at all → NO_BUY.
  2. **No photos** → NOT_EVALUATABLE.
  3. **AI assessment** (grade, colour, storage, battery, confidence, functional defect flag, reasoning). Failure to load photos, or all models failing → NOT_EVALUATABLE.
  4. **Decision:**
     - Confidence below the minimum → UNSURE.
     - Functional defect → NO_BUY.
     - A5 → NO_BUY.
     - No price for the assessed variant → UNSURE.
     - No purchase price on the Listing → UNSURE with the details resale price and max purchase price (resale − min margin).
     - Otherwise BUY if profit ≥ min margin, else NO_BUY.
- Details on the Verdict: assessed grade, AI colour, AI storage, AI battery, "Rebuy zahlt", "Einkauf", "Gewinn" (whichever apply).
- The AI structured response gains an explicit functional-defect flag instead of the "FUNKTIONSFEHLER" text prefix. The prompt is updated to match.
- The catalog is parsed from the Rebuy product page as today. It is cached per Reference Product URL with a configurable lifetime, and the last good catalog is used when a reload fails. Unused catalog lookups are removed. The grade and the catalog variant become their own types (no more `Properties` holder).

### AI infrastructure
- A reusable AI client:
  - takes a system prompt, a user template with parameters, images and a target response type,
  - tries the configured models in priority order and skips models that are cooling down after capacity errors (429/503/504/timeout),
  - fails immediately on an invalid API key,
  - exposes a readable root-cause message.
- The Gemini client configuration (hard timeout, no SDK retries) and the fallback chain properties move here. The application starts without an API key: the failure moves from startup to the first AI call.

### Kleinanzeigen Finder
- Settings: search URL, max Listings per run.
- Paging, detail parsing, price parsing and Marketplace Details parsing stay as today. A failing results page ends that Finder's run with a recorded failure. A failing detail page skips that ad.
- A phone mapper turns "Speicher (GB)", "Akkuzustand", "Zustand" and "Farbe" into phone attributes. Kleinanzeigen condition values map as: Neu → NEW, Sehr gut → VERY_GOOD, Gut → GOOD, In Ordnung → OK, Defekt → DEFECT.
- A price of 0 or no price becomes "no price".

### Telegram Notifier
- Settings: chat id. The bot token is an operator setting from the environment.
- Message format as today (badge per Decision, title, price, reason, link). The "Details" block lists the Alert's labelled values instead of the raw Marketplace Details.

### Persistence (PostgreSQL, Spring Data JDBC, Flyway)
- **Search table**: scalar columns plus jsonb for slots, filter settings, Schedule and retention. Also last run time, ok flag, messages and next run time.
- **Seen Fingerprints table**: Search id + Fingerprint, unique, deleted with the Search.
- **Listing History table**: Search id, recorded time, Decision, Listing as jsonb, Verdict as jsonb. Retention is applied after each run's insert (keep only the configured Decisions, then the newest N).

### HTTP API
- `GET /api/listing-types`: id, filter settings schema, Exclusion Presets (id, name).
- `GET /api/plugins?listingType=`: id, kind, Listing Types, settings schema.
- `GET`, `POST /api/searches` · `GET`, `PUT`, `DELETE /api/searches/{id}` · `POST /api/searches/{id}/pause` and `/resume`.
- `GET /api/searches/{id}/history`: newest first.
- Validation errors return 400 with the field path and message.
- HTTP Basic with one user from the environment.

### Operation
- Secrets and operator settings come from the environment: Telegram bot token, Gemini key, model chain, catalog cache lifetime, timezone, DB connection, API user. The committed token is revoked and removed.
- docker compose starts Octo and PostgreSQL.

## Testing Decisions

- **A good test** checks external behaviour through the highest available seam: what goes in (Search, marketplace pages, AI responses) and what comes out (Verdicts, Listing History, Alerts sent, run status, HTTP responses). It doesn't check private methods, call order or intermediate collections, so the inside can be refactored freely.
- **Seam 1, the run of a Search (main seam).** Run a Search with fake Finders, a fake Evaluator and recording Notifiers, plus in-memory or Testcontainers storage. Cover:
  - each of the seven steps,
  - Deduplication across runs, re-posts and price drops,
  - NOT_EVALUATABLE not remembered,
  - no Evaluator → BUY,
  - failure isolation per Finder, per Listing and per Notifier,
  - Decision filtering per notifier slot,
  - the retention rule,
  - the run status.
- **Seam 2, the HTTP API.** Spring Boot test against PostgreSQL (Testcontainers). Cover:
  - Search CRUD, pause/resume, delete removing history and seen,
  - refusal of incompatible Plugins and invalid settings with field paths,
  - Listing Type and Plugin discovery with schemas,
  - history reads,
  - authentication.
- **Dense units**, only where the logic is dense:
  - phone text reading (storage, battery patterns and ranges),
  - Exclusion Filter (table of phrases: "Display gesprungen" rejects; "kein Riss", "Panzerglas gesprungen", "Defekt: nein" pass; "suche iPhone" in the title rejects, in the description passes),
  - next-run calculation of the Schedule (window, weekdays, timezone),
  - the Rebuy decision with a fake AI assessor and a catalog parsed from a saved Rebuy HTML fixture,
  - Kleinanzeigen parsing against saved result and detail HTML fixtures,
  - the AI client's fallback and cooldown with a fake model.
- No test needs the network or a Gemini key. The context-loads test runs offline.
- **Prior art:** there is none besides the default context-loads test. These tests set the pattern. Saved HTML fixtures live with the tests.

## Out of Scope

- The frontend itself (only the API it needs).
- Multiple users, accounts, roles.
- Tracking purchases, resales or profit after the Alert.
- Deduplication across marketplaces.
- Chains of several Evaluators.
- Finder-specific filters.
- Shared default settings across Searches.
- New marketplaces, buyback services or channels beyond Kleinanzeigen, Rebuy and Telegram.
- Listing Types beyond phone and generic.
- Migrating in-memory state from the current version (there is none worth keeping).

## Further Notes

- The domain vocabulary follows `GLOSSARY.md`. The new or sharpened terms (Fingerprint, Exclusion Preset, Marketplace Details, Phone Condition, Alert) were settled while preparing this spec.
- `docs/VISION.md` remains the reference for philosophy and the run flow. Its "Gaps to the current code" section should shrink as tickets land and be removed at the end.
- The ADR for "stateless Plugins with typed settings per call" should be written before or with the first ticket that introduces the Plugin contract.
- Revoking the committed Telegram bot token is a manual step for the user and should happen before any other work.
- Suggested order:
  1. Secrets.
  2. New core contracts next to the old ones.
  3. Listing Types and Exclusion Filter.
  4. Infra extraction.
  5. Port the Plugins.
  6. The new run with a Search built in code, then delete the old core.
  7. Persistence.
  8. Schedule.
  9. Registries, settings schema and API.

  `/to-tickets` decides the final slicing.
