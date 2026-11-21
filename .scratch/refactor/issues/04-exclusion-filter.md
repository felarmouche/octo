# 04: Exclusion Filter with presets

**What to build:** Every Search on the new run has an Exclusion Filter, made of the user's own words plus the Exclusion Presets they select.
- The phone preset "damage" carries the existing damage rules.
- The "wanted / swap" preset is available for every Listing Type and matches on the title only.
- One matcher applies to all rules. It ignores negated mentions ("kein Riss", "nicht gebrochen", "Defekt: nein") and damage to accessories ("Panzerglas gesprungen", "Hülle kaputt").

See spec "Exclusion Filter" and user stories 41–47.

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] The Exclusion Filter runs after Deduplication and before the Listing Type Filter
- [ ] Rejections are NO_BUY with the reason `<rule> ("<matched text>")`
- [ ] Own words match on word boundaries and use the same negation and accessory handling as presets
- [ ] Presets are offered per Listing Type or for all types; a Search can only select presets of its own Listing Type or common ones
- [ ] Table tests: "Display gesprungen" rejects; "kein Riss", "Panzerglas gesprungen" and "Defekt: nein" pass; "suche iPhone" rejects in the title and passes in the description
