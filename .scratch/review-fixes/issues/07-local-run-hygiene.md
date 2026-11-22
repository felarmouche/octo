# 07: Local-run hygiene

**What to build:** A fresh clone runs locally as documented without accidentally committing secrets. The compose file points at a local `.env` for secrets, but `.env` isn't ignored by git. `gradlew` lacks its executable bit, so `./gradlew` fails with exit 126 (the Dockerfile works around it).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `.env` is listed in `.gitignore`
- [ ] `gradlew` is committed with its executable bit; the Dockerfile workaround is removed
- [ ] The README names the environment variables needed for `bootRun` (API user and password, Gemini key, and the Telegram token once refactor/12 lands)
