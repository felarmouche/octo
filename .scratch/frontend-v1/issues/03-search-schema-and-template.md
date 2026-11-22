# 03: Search schema and template for new Searches

**What to build:** A frontend can render the fixed, non-Plugin parts of a Search from schemas, and start a new Search from values the backend chooses:
- `GET /api/search-schema` returns `{schedule, retention, notifierDecisions}` as JSON Schemas with German titles and enum labels:
  - `schedule`: interval in minutes, an optional window whose `from` and `to` are strings with `format: "time"`, and weekdays labelled Mo–So.
  - `retention`: newest N (nullable) and the Decisions to keep.
  - `notifierDecisions`: a set of Decisions labelled "Kaufen", "Unsicher", "Nicht kaufen" and "Nicht bewertbar".
- `GET /api/searches/template?listingType=` returns a complete `SearchDefinition` with initial values for that Listing Type: an empty name, an empty Listing Type Filter and Exclusion Filter, no Finders, no Evaluator, no Notifiers, and a default Schedule and retention. Saving it after adding a name, a Finder and a Notifier creates a valid Search.

Spec: `octo_frontend/.scratch/frontend-v1/spec.md` (Backend changes, API contract).

**Blocked by:** 02 (uses the same schema generation and formats)

**Status:** resolved

- [ ] The search-schema's fields have German titles and labelled enum values, and the window times have `format: "time"`
- [ ] Decoding still accepts exactly what the schema describes (e.g. `"08:00"` for a window time)
- [ ] The template exists for every Listing Type, and an unknown Listing Type returns 400 without a `field`
- [ ] An API test fills a template with name, a Finder and a Notifier, POSTs it unchanged otherwise, and gets 201
- [ ] API tests cover search-schema and template
