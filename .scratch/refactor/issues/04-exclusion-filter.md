# 04: Exclusion Filter with presets

**What to build:** Every Search on the new run has an Exclusion Filter, made of the user's own words plus the Exclusion Presets they select.
- The phone preset "damage" carries the existing damage rules.
- The "wanted / swap" preset is available for every Listing Type and matches on the title only.
- One matcher applies to all rules. It ignores negated mentions ("kein Riss", "nicht gebrochen", "Defekt: nein") and damage to accessories ("Panzerglas gesprungen", "Hülle kaputt").

See spec "Exclusion Filter" and user stories 41–47.

**Blocked by:** 03

**Status:** ready-for-agent

- [x] The Exclusion Filter runs after Deduplication and before the Listing Type Filter
- [x] Rejections are NO_BUY with the reason `<rule> ("<matched text>")`
- [x] Own words match on word boundaries and use the same negation and accessory handling as presets
- [x] Presets are offered per Listing Type or for all types; a Search can only select presets of its own Listing Type or common ones
- [x] Table tests: "Display gesprungen" rejects; "kein Riss", "Panzerglas gesprungen" and "Defekt: nein" pass; "suche iPhone" rejects in the title and passes in the description

## Comments

Implemented. The Exclusion Filter lives in `core.exclusion` (`ExclusionFilter`, `ExclusionRule`, `ExclusionPreset`, `ExclusionFilterSettings`). `ExclusionFilter.of(settings, availablePresets)` builds the rules: own words first, then the selected presets. `reject` checks the title before the description, using the matcher carried over from `DamageTextFilter` (negation before, inside or after the match, and accessories nearby). The common preset `wanted-swap` (title only) is `ExclusionPreset.COMMON`. The phone preset `damage` is `PhoneDamagePreset` in `types.phone`. `ListingType` declares `exclusionPresets()` and gets a default `availableExclusionPresets()` (the common ones, then its own). The Search holds `ExclusionFilterSettings` after its type slot. Its constructor rejects presets the Listing Type doesn't offer. The run builds the filter once per run and applies it between Deduplicate and Filter by type.

Notes:
- The in-code PS5 Search now selects `wanted-swap`.
- `DamageTextFilter` stays in place for the old path and is removed in 07. Until then the matcher exists twice.
- Rule order is fixed: the selected presets in the order the Listing Type offers them (common first), then the user's own words, as in the old `DamageTextFilter`. When several rules match, the reason is always the same.
