# 01: Session login replaces HTTP Basic

**What to build:** A browser frontend logs in with the operator's single user and then calls the API with a session cookie instead of HTTP Basic (ADR-0002). `POST /api/login` takes username and password, checks them against the operator user from the environment and sets an HttpOnly, SameSite=Strict session cookie. `POST /api/logout` ends the session. `GET /api/session` returns 200 when logged in and 401 when not. Sessions live in memory and last 30 days after login; then the user logs in again. A failed login answers only after a fixed delay of about 1 s. No 401 carries `WWW-Authenticate: Basic`, so the browser never shows its native login dialog. HTTP Basic is removed. Login and session are the only endpoints reachable without a session.

Spec: `octo_frontend/.scratch/frontend-v1/spec.md` (Backend changes, ADR-0002).

**Blocked by:** None (can start immediately)

**Status:** resolved

- [ ] Login with the right credentials sets the session cookie, and API calls with it succeed
- [ ] Login with wrong credentials returns 401 after the delay, with a message that doesn't reveal which part was wrong
- [ ] Logout ends the session; the same cookie then gets 401
- [ ] `GET /api/session` returns 200 or 401 accordingly
- [ ] Every other `/api` endpoint returns 401 without a session, and no 401 has `WWW-Authenticate: Basic`
- [ ] HTTP Basic no longer works
- [ ] The session cookie is HttpOnly and SameSite=Strict, and the session and its cookie last 30 days
- [ ] Existing API tests log in through the session instead of Basic; new API tests cover login, logout, session and the missing header
- [ ] README explains logging in; `docs/VISION.md` says in one short paragraph that the frontend is same-origin and logs in against the backend
