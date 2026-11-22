# 06: Rebuy Evaluator on the new run

**What to build:** The iPhone 15 Search runs on the new path, with the Rebuy Evaluator rebuilt on the new contract.

- **Settings:** `RebuySettings` (Reference Product URL, min margin, min AI confidence, max photos) replace the constants and `SearchSettings`.
- **Output:** the Evaluator returns one Verdict per Listing with labelled details. It never writes to the Listing.
- **Order of checks:**
  1. Cheap pre-check, without AI:
     - storage not offered → UNSURE
     - best-case margin too low → NO_BUY
     - variant not bought → NO_BUY
  2. No photos → NOT_EVALUATABLE.
  3. AI assessment.
  4. The decision.
- **No price:** a Listing without a price gets UNSURE with the details "Rebuy zahlt" and "Max. Einkauf".
- **Functional defects:** the AI response gets an explicit functional-defect flag.
- **Catalog cache:** cached per Reference Product URL, falling back to the last good catalog.

Damage words, battery and "defekt" are no longer in Rebuy; tickets 03 and 04 handle them.

See spec "Rebuy Evaluator" and user stories 54–68.

**Blocked by:** 04, 05

**Status:** ready-for-agent

- [x] The best plausible grade comes from the Phone Condition (VERY_GOOD → A1, GOOD → A2, OK → A3, else A0), not from marketplace wording
- [x] No AI call for Listings the pre-check rejects
- [x] Unloadable photos or all models failing → NOT_EVALUATABLE, retried when the Listing is found again
- [x] A Listing without a price never gets BUY
- [x] Verdict details include the assessed grade, the AI's colour/storage/battery, "Rebuy zahlt", "Einkauf" and "Gewinn" where they apply
- [x] The grade and the catalog variant are their own types; unused catalog lookups are removed
- [x] Tests: a decision table with a fake AI assessor and a catalog parsed from a saved Rebuy HTML fixture; two Reference Products don't evict each other's cache
- [x] The iPhone 15 Search (Kleinanzeigen + phone filter + damage preset + Rebuy + Telegram) runs on the new path
