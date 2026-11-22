# Octo — Vision, Flow and Philosophy

This document describes what Octo is meant to be: the idea, the rules we design by, how the user works with it, and how a Search runs. Terms in **bold** are defined in [`GLOSSARY.md`](../GLOSSARY.md).

Where code and this document disagree, this document describes the target.

## The idea

Octo watches second-hand marketplaces for underpriced offers. The user defines **Searches** in a frontend (e.g. "iPhone 15 on Kleinanzeigen between 100 and 400 €"). Octo runs each Search on its **Schedule**, collects new **Listings**, throws out the ones that don't fit, optionally judges the rest (e.g. "can I resell this to Rebuy with at least 20 € profit?"), and alerts the user about the interesting ones.

Octo is a single-user tool. There are no accounts, roles or tenants.

## Philosophy

1. **Simple over clever.** This is the top rule. When in doubt, pick the solution with fewer concepts, fewer classes and fewer settings. Don't build for hypothetical future needs.
2. **Configurable, without being configurable everywhere.** Everything a user might reasonably want to tune per Search is a setting. Everything else is a sensible fixed behaviour.
3. **All Plugins look alike.** Every Plugin has an id, the Listing Types it supports, and its own typed **Plugin Settings**. Learning one Plugin means knowing them all.
4. **Logic lives in the backend.** The frontend asks the backend which Plugins exist and what their settings look like, and renders forms from that. The frontend has no knowledge of Kleinanzeigen, phones or Rebuy.
5. **Every run is the same.** A Search always goes through the same fixed steps in the same order. Plugins fill steps; they don't change the flow.
6. **Cheap before expensive.** Steps are ordered so that scraping and AI calls only happen for Listings that survived the cheap checks.
7. **Every Listing gets a Verdict, with a reason.** Whether rejected by a filter, judged by an Evaluator or simply passed, the user can always see *why*.
8. **One failing part doesn't stop the run.** If one Finder or Notifier fails, the rest of the run continues, and the failure is shown on the Search.

## User journey

### Creating a Search

In the frontend, the user creates a Search in this order:

1. **Name** the Search.
2. **Pick the Listing Type** (e.g. phone, or generic). It is fixed once the Search is created; another type means a new Search. From here on, the frontend only offers Plugins that support this Listing Type.
3. **Add one or more Finders** and fill in their settings. Where the marketplace allows it, the main setting is simply the marketplace's own search URL (with price range, location etc. already in it), plus a max number of Listings per run.
4. **Configure the Exclusion Filter**: own excluded words, optionally adding the **Exclusion Preset** of the Listing Type (e.g. typical damage words for phones).
5. **Configure the Listing Type Filter**: e.g. for phones min battery, allowed storage sizes, price range, allowed conditions. All fields are optional.
6. **Optionally pick an Evaluator** and fill in its settings (e.g. for Rebuy: the **Reference Product** URL, min margin, min AI confidence).
7. **Add one or more Notifiers**, each with its channel (e.g. a Telegram chat) and the Decisions it reports (e.g. only BUY, or BUY and UNSURE).
8. **Set the Schedule**: how often (e.g. every 5 minutes), within which hours (e.g. 11:00–17:00) and on which weekdays. All Schedules use one global timezone (Europe/Berlin).
9. **Set the retention rule** of the **Listing History**: keep the newest N Listings, only Listings with certain Decisions, or both.
10. **Activate** the Search.

### Living with a Search

- The user gets alerts on the configured channels. Octo's job ends with the alert: buying and reselling happen outside Octo.
- In the frontend the user sees, per Search, the Listing History (each Listing with its Verdict and reason) and the last run status (time, ok or error, message). The history is how the user tunes filters and settings.
- The user can **edit** a Search at any time. Changes apply from the next run. What the Search has already seen is kept, so editing doesn't cause old Listings to be alerted again.
- The user can **pause** and resume a Search. Pausing keeps everything.
- **Deleting** a Search deletes its Listing History and what it has seen.

## The run flow

Every run of a Search follows the same steps in this order:

```
Schedule fires
   │
   ▼
1. Find            each Finder turns the ads of its marketplace into Listings of the Search's Listing Type
   ▼
2. Deduplicate     drop Listings whose Fingerprint the Search has already seen        (always)
   ▼
3. Exclude         Exclusion Filter: matching words → NO_BUY                          (always available)
   ▼
4. Filter by type  Listing Type Filter: known-bad attributes → NO_BUY                 (always available)
   ▼
5. Evaluate        Evaluator gives BUY / NO_BUY / UNSURE / NOT_EVALUATABLE            (optional)
   │               without an Evaluator, survivors get BUY ("passed all filters")
   ▼
6. Record          store Listings with Verdicts in the Listing History (retention rule applies);
   │               remember what was seen, except NOT_EVALUATABLE Listings
   ▼
7. Notify          each Notifier sends the Listings whose Decision it reports
```

Rules that apply across the steps:

- **Rejections are Verdicts.** A Listing rejected in step 3 or 4 gets NO_BUY with a reason naming the filter (e.g. "Akku 74 % < 80 %") and lands in the Listing History. Listings dropped by Deduplication are not recorded again.
- **Unknown passes.** Filters reject only what is *known* to be bad. A phone with unknown battery passes the Listing Type Filter; the Evaluator may still find out from the photos.
- **Retrying NOT_EVALUATABLE.** Such a Listing is not remembered as seen. It is evaluated again only if a Finder finds it again on a later run. There is no retry limit: if the ad is gone, retrying stops by itself.
- **Seen is separate from history.** The Search remembers every Fingerprint it has seen, even after the Listing History has deleted the Listing. Otherwise deleted Listings would come back and trigger alerts again.
- **No Deduplication across marketplaces.** The same phone on Kleinanzeigen and eBay is two Listings and may cause two alerts. That's accepted.
- **No overlap.** The next run of a Search is planned from the end of the previous one, so a Search never runs twice at the same time. Different Searches may run in parallel.

## The Plugin model

Every Plugin declares:

- an **id** (e.g. `kleinanzeigen-finder`),
- the **Listing Types** it supports,
- its **Plugin Settings**: one typed settings object per Plugin. The backend can describe it so the frontend can render a form, and it is stored per Search.

There are no shared or global defaults for Plugin Settings. Every value belongs to exactly one Search.

### Finders (one or more per Search)

Turn a marketplace into Listings of the Search's Listing Type. Finders don't deduplicate. That is only done in the Deduplication step, on the full Fingerprint, so a Listing the seller changed (e.g. a price drop) counts as new, and a NOT_EVALUATABLE Listing is found and evaluated again. A Finder declares which Listing Types it can produce, e.g. Kleinanzeigen knows how to read "Speicher (GB)" and "Akkuzustand" for phones. Finders have no filters of their own: the marketplace's search URL covers that.

### Filters (fixed slots)

- **Deduplication**: always on, no settings, invisible to the user.
- **Exclusion Filter**: always available. Own words plus optional Exclusion Presets of the Listing Type.
- **Listing Type Filter**: one per Listing Type, with all fields optional.

The user configures the filters but never adds or removes them. They use the same settings and form mechanism as every other Plugin.

### Evaluator (zero or one per Search)

Judges the Listings that survived the filters. An Evaluator only contains checks that need something the filters don't have, e.g. the Rebuy Evaluator needs the Reference Product for "margin too low even in the best condition" or "this storage size doesn't exist for this model". Everything that can be judged from the Listing alone belongs in a filter. The Evaluator puts its findings into the Verdict as evaluation details (e.g. resale price, margin, assessed condition), not into the Listing.

### Notifiers (one or more per Search)

Each sends to one channel, and only the Decisions it was set to report. A Notifier doesn't know any Listing Type: Listing Types and Evaluators each provide a plain list of labelled values (e.g. "Speicher: 128 GB", "Rebuy zahlt: 310 €"), and the Notifier renders that list. A new Listing Type therefore needs no changes to any Notifier.

### Listing Types

A Listing Type defines the attributes its Listings carry, its Listing Type Filter, its Exclusion Presets and how its attributes are displayed. To start:

- **phone**: storage, battery, colour, condition.
- **generic**: only what every Listing has (title, price, description, images). It lets you watch anything, e.g. "PS5 under 250 €".

## Settings the frontend never sees

Operator settings stay in environment variables or `application.properties`. Examples:

- Telegram bot token (one global bot; only the chat is per Notifier)
- Gemini API key and model fallback chain
- Rebuy catalog cache duration
- the global timezone

Secrets are never committed.

## Out of scope (for now)

- Multiple users, accounts, permissions
- Tracking purchases, resales or profit after the alert
- Deduplication across marketplaces
- Chains of several Evaluators
- Finder-specific filters
- Shared default settings across Searches
