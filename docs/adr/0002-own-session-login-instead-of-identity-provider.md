# Own session login instead of an identity provider

Octo has one user, so the frontend logs in against the backend itself. `POST /api/login` checks the `API_USER` / `API_PASSWORD` from the environment and sets an HttpOnly, SameSite=Strict session cookie. `GET /api/session` tells the frontend whether it is logged in, and `POST /api/logout` ends the session. This replaces HTTP Basic, because a browser app shouldn't hold the password or rely on the browser's Basic prompt. We considered an identity provider (Clerk) and rejected it: its gains (passkeys, MFA, social login, several users) don't matter for a single-user tool, and it would add a SaaS dependency, JWT validation and a heavy frontend SDK. If Octo is exposed publicly, strong protection goes in front of it at the edge (e.g. Cloudflare Access), not into the app.

## Consequences

- Sessions are held in memory: a backend restart logs the user out.
- The frontend and the API must share one origin (the frontend's server proxies `/api`), so the cookie works without CORS.
- Moving to several users later means adding an identity provider *and* an owner on each Search. Neither exists today, by design (see `docs/VISION.md`).
