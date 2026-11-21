# 09: Searches managed through the API

**What to build:** The user creates, reads, edits, pauses, resumes and deletes Searches through the HTTP API. One Basic-auth user from the environment protects it.

- **Storage:** Searches are stored with their slots and settings as JSON. A settings codec turns the JSON into typed settings records and validates them.
- **Saving refuses:**
  - unknown Plugins
  - Plugins of the wrong kind or for the wrong Listing Type
  - invalid settings (400 with the field path)
  - Searches without a Finder or a Notifier
- **The Listing Type is fixed** after creation.
- **Running:** the runner runs all active stored Searches, still on one global interval. The in-code Searches are removed.

See spec "Search aggregate", "Plugin Settings mechanism" and "HTTP API", and user stories 1–16, 78, 81.

**Blocked by:** 08

**Status:** ready-for-agent

- [ ] CRUD endpoints plus pause and resume; paused Searches don't run
- [ ] Edits apply from the next run, and the Search keeps what it has seen
- [ ] Deleting a Search removes its Listing History and seen Fingerprints
- [ ] Changing the Listing Type of an existing Search is refused
- [ ] Notifier slots carry the Decisions they report
- [ ] API-seam tests (Spring Boot + Testcontainers) cover the points above and authentication
