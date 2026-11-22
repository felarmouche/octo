# 02: Schemas ready for the frontend form

**What to build:** A frontend can render every Plugin and Listing Type form, with German names and proper inputs, without knowing any Plugin:
- `GET /api/listing-types` and `GET /api/plugins` give each entry a German display `title` (e.g. "Handy", "Allgemein", "Kleinanzeigen", "Rebuy", "Telegram").
- Each Listing Type's `exclusionSchema` describes `presets` as a set (`uniqueItems`) whose items are `oneOf` `{const, title}` with exactly the Exclusion Presets available to that Listing Type. The separate `exclusionPresets` list may stay.
- Every amount stored in cents carries `format: "money-cents"`, and its title drops the "(Cent)" suffix (e.g. "Mindestpreis", "Höchstpreis", "Mindestgewinn"). The frontend shows a euro input and sends cents.

The Schedule window's times get `format: "time"` (`HH:mm`) in ticket 03, where the Schedule schema is first served.

Spec: `octo_frontend/.scratch/frontend-v1/spec.md` (Backend changes, API contract).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Listing Types and Plugins each have a German `title`; adding a Plugin or Listing Type requires declaring one
- [ ] The phone `exclusionSchema` offers exactly "Gesuche / Tausch" and "Schäden" as presets; generic offers only "Gesuche / Tausch"
- [ ] All cent fields (price range, min margin) are `integer` with `format: "money-cents"` and titles without "(Cent)"
- [ ] A settings field can declare `money-cents` by annotation, so new Plugins get it the same way
- [ ] Discovery API tests cover titles, narrowed presets and the money format
- [ ] README's "how to add a Plugin" mentions the title and the money format
