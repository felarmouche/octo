# 01: A run's Record step is all-or-nothing

**What to build:** When a run records its judged Listings, the Listing History entries and the Seen Fingerprints are stored together or not at all. Today they are stored in separate transactions: if remembering the Fingerprints fails after the history was written, the run skips Notify, and the next run records the same Listings in the history a second time and sends their Alerts. Edits of a Search (update, pause, resume) read and write in one transaction too.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Record (history + retention + Fingerprints) runs in a single transaction
- [ ] A test makes remembering the Fingerprints fail and shows that no history entry was kept, and the next run records and alerts each Listing exactly once
- [ ] Update, pause and resume of a Search each run in one transaction
- [ ] The run flow in `docs/VISION.md` still matches the code
