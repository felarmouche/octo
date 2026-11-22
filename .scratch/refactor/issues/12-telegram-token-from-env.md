# 12: Telegram bot token from the environment

**What to build:** The Telegram bot token is an operator setting read from an environment variable (e.g. `TELEGRAM_BOT_TOKEN`), like the Gemini key and the API user. The committed token is removed from `application.properties`.

- The token in the repository is compromised: it is in the git history. Revoke it with BotFather and create a new one (a human step).
- Octo doesn't start without a token, like it doesn't without the API user.

See spec "Operation" and `docs/VISION.md` "Settings the frontend never sees".

**Blocked by:** none

**Status:** needs-triage

## Comments

- From 11: this was the last open point of the "Gaps to the current code" section in `docs/VISION.md`, which 11 removed. No other issue covered it, so it is kept here.
