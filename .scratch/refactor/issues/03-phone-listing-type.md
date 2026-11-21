# 03: Phone Listing Type

**What to build:** A phone Search on the new run produces Phone Listings with storage, battery, colour and Phone Condition. It rejects Listings by the phone Listing Type Filter and shows the phone values in Alerts.

- **Text reading belongs to the phone Listing Type.** It reads storage and battery from the title and description, constrained by range hints like "Schwach (< 80%)". It is applied to every found Listing, so every Finder gets it.
- **The Kleinanzeigen Finder only maps its own data.** It maps its fields ("Speicher (GB)", "Akkuzustand", "Zustand", "Farbe") onto the phone attributes, and its condition wording onto Phone Condition.

See spec "Listing Types" and user stories 25–27, 48–53, 70–71.

**Blocked by:** 02

**Status:** ready-for-agent

- [ ] All phone attributes are optional; unknown means absent, not a magic number; a battery marked defect counts as 0 %
- [ ] All phone Listing Type Filter settings (price range, min battery, storage sizes, Phone Conditions) are optional; unknown values and Listings without a price pass
- [ ] Rejections are NO_BUY with a reason naming the setting, e.g. "Akku 74 % < 80 %"; they are recorded and remembered as seen
- [ ] The generic Listing Type gets an optional price range filter
- [ ] Alerts include the phone's labelled values (e.g. "Speicher: 128 GB"), and the Telegram Notifier stays unaware of phones
- [ ] Unit tests for phone text reading (the existing patterns and range hints) and run-seam tests for the filter
