# 03: Phone Listing Type

**What to build:** A phone Search on the new run produces Phone Listings with storage, battery, colour and Phone Condition. It rejects Listings by the phone Listing Type Filter and shows the phone values in Alerts.

- **Text reading belongs to the phone Listing Type.** It reads storage and battery from the title and description, constrained by range hints like "Schwach (< 80%)". It is applied to every found Listing, so every Finder gets it.
- **The Kleinanzeigen Finder only maps its own data.** It maps its fields ("Speicher (GB)", "Akkuzustand", "Zustand", "Farbe") onto the phone attributes, and its condition wording onto Phone Condition.

See spec "Listing Types" and user stories 25–27, 48–53, 70–71.

**Blocked by:** 02

**Status:** ready-for-agent

- [x] All phone attributes are optional; unknown means absent, not a magic number; a battery marked defect counts as 0 %
- [x] All phone Listing Type Filter settings (price range, min battery, storage sizes, Phone Conditions) are optional; unknown values and Listings without a price pass
- [x] Rejections are NO_BUY with a reason naming the setting, e.g. "Akku 74 % < 80 %"; they are recorded and remembered as seen
- [x] The generic Listing Type gets an optional price range filter
- [x] Alerts include the phone's labelled values (e.g. "Speicher: 128 GB"), and the Telegram Notifier stays unaware of phones
- [x] Unit tests for phone text reading (the existing patterns and range hints) and run-seam tests for the filter

## Comments

Implemented. The phone Listing Type lives in `types.phone` (`PhoneListingType`, `PhoneAttributes`, `Storage`, `Battery`, `PhoneCondition`, `PhoneFilterSettings`). `ListingType` now has a filter settings type and `reject`. The Search holds it in a `TypeSlot` (type + filter settings), and the run's new step 4 turns a rejection into NO_BUY. The shared `PriceRange` (in cents) lives in `core.listingtype` and is used by the generic and phone filters. Battery is a range (`min..max`): an exact value has `min == max`, a range hint like "Schwach (< 80%)" gives 0..79, and defect gives exactly 0. The filter rejects only when even the best case is below the min ("Akku höchstens 79 % < 80 %"). `Battery.fromHint` is public, so every Finder can parse its hints the same way.

Notes:
- `plugins.kleinanzeigen` now depends on `types.phone`. The spec's dependency list ("plugins → core, infra") doesn't name this, but the Finder has to map onto phone attributes. The rule should read "plugins → core, infra, types".
- No phone Search is wired in `InCodeSearches` yet. Without an Evaluator it would alert every iPhone as BUY. The phone path is covered by run-seam tests and will be wired with the Rebuy Evaluator (06).
- A bare "Schwach" hint without a number is now unknown. The old converter treated it as 1..79. Kleinanzeigen's option text carries "(< 80%)".
