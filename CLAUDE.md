## Vision

Read `docs/VISION.md` before designing or changing anything. It holds the idea, philosophy, user journey and the fixed run flow of a Search. Its top rule: simple over clever, don't over-engineer. Where the code disagrees with it, the document describes the target.

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix), recorded as `Status:` lines. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
