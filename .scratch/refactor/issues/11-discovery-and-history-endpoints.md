# 11: Discovery and history endpoints for the frontend

**What to build:** A frontend can render every form and the history view from what the backend returns:
- **Listing Types** with their filter settings schema and Exclusion Presets.
- **Plugins per Listing Type** with kind, id and settings schema (types, constraints, labels, initial values). The schema is generated from the settings records.
- **A Search's Listing History**, newest first, with Verdicts and details.

Wrap-up: remove the "Gaps to the current code" section from `docs/VISION.md`.

See spec "HTTP API" and user stories 74, 76–77.

**Blocked by:** 09

**Status:** resolved

- [x] Schemas are generated from the settings records, not written by hand
- [x] Listing Plugins for a Listing Type returns only compatible Plugins
- [x] The history endpoint returns Listings with Decision, reason and details
- [x] API-seam tests for discovery and history
- [x] The gaps section is removed from `docs/VISION.md`, and the README explains how to add a Plugin

## Comments

- Done:
  - `GET /api/listing-types` returns per Listing Type its filter schema, the Exclusion Filter schema and its presets.
  - `GET /api/plugins?listingType=` returns id, kind, Listing Types and the settings schema. An unknown Listing Type gives 400.
  - `GET /api/searches/{id}/history` is newest first, each entry with the Listing, its attributes as the Listing Type labels them (`display`) and the Verdict.
- Schemas: `SettingsSchema` uses victools (from Spring AI) plus its Jakarta-validation and Swagger modules.
  - Labels and initial values come from `@Schema(title, defaultValue)` on the settings records, also in `core` (`PriceRange`, `ExclusionFilterSettings`).
  - Enum values get labels from their `label()` method. Fields keep the order of the record.
- Notifiers list empty `listingTypes`, meaning every Listing Type (documented in the README).
- The removed gaps section still had the committed Telegram token. No issue covered it, so it is now issue 12.

