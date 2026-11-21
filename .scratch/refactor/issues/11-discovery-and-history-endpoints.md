# 11: Discovery and history endpoints for the frontend

**What to build:** A frontend can render every form and the history view from what the backend returns:
- **Listing Types** with their filter settings schema and Exclusion Presets.
- **Plugins per Listing Type** with kind, id and settings schema (types, constraints, labels, initial values). The schema is generated from the settings records.
- **A Search's Listing History**, newest first, with Verdicts and details.

Wrap-up: remove the "Gaps to the current code" section from `docs/VISION.md`.

See spec "HTTP API" and user stories 74, 76–77.

**Blocked by:** 09

**Status:** ready-for-agent

- [ ] Schemas are generated from the settings records, not written by hand
- [ ] Listing Plugins for a Listing Type returns only compatible Plugins
- [ ] The history endpoint returns Listings with Decision, reason and details
- [ ] API-seam tests for discovery and history
- [ ] The gaps section is removed from `docs/VISION.md`, and the README explains how to add a Plugin
