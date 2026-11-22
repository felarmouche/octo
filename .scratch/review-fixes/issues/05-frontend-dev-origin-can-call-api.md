# 05: A frontend on its own dev origin can call the API

**What to build:** A browser frontend served from another origin (e.g. the Vite dev server on port 5173) can call the API. Today there is no CORS configuration and every request, including the CORS preflight, needs authentication, so the preflight is rejected with 401. Also, a 401 carries `WWW-Authenticate: Basic`, which pops the browser's native login dialog on `fetch`.

**Blocked by:** None (can start immediately)

**Status:** wontfix

- [ ] Allowed origins are an operator setting from the environment, defaulting to the local Vite dev origin
- [ ] Preflight `OPTIONS` requests from an allowed origin succeed without credentials
- [ ] Requests from an origin not on the list get no CORS headers
- [ ] An unauthenticated API call gets 401 without the header that triggers the browser's login dialog
- [ ] API tests cover preflight, allowed and disallowed origin

## Comments

**wontfix** (frontend-v1 grilling, 2026-11-22): the frontend is served from the same origin as the API. In production, the frontend's Caddy proxies `/api`, and locally the Vite dev server does the same. So no CORS is needed. The remaining point, a 401 without `WWW-Authenticate: Basic`, is covered by the session login that replaces HTTP Basic (ADR-0002, `../octo_frontend/.scratch/frontend-v1/spec.md`).
