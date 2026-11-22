# Octo

Octo watches second-hand marketplaces for underpriced offers. Each **Search** runs on its own Schedule, judges every new Listing and alerts you about the interesting ones. What Octo is meant to be and why is in [`docs/VISION.md`](docs/VISION.md); the terms are in [`GLOSSARY.md`](GLOSSARY.md).

## Running

`docker compose up --build` starts Octo and PostgreSQL. Secrets come from the environment or a local `.env` (never committed): `API_USER`, `API_PASSWORD`, `GEMINI_API_KEY`. For running from the IDE, `docker compose up -d postgres` starts only the database.

Tests need Docker (Testcontainers): `./gradlew test`.

## HTTP API

Everything except `POST /api/login` needs a session. `POST /api/login` with `{"username": ..., "password": ...}` (the `API_USER` / `API_PASSWORD`) answers `204` and sets an HttpOnly, SameSite=Strict session cookie; after 30 days you log in again. A failed login answers `401` after a delay of one second. Without a session, every other endpoint answers a plain `401` (no `WWW-Authenticate`, so browsers show no login dialog). Sessions live in memory, so a restart logs you out. See [ADR-0002](docs/adr/0002-own-session-login-instead-of-identity-provider.md).

| Endpoint | What it does |
| --- | --- |
| `POST /api/login`, `POST /api/logout` | open and end the session |
| `GET /api/session` | `200` while logged in, `401` otherwise |
| `GET /api/listing-types` | Listing Types with their German title, the schemas of their Listing Type Filter and of the Exclusion Filter (offering exactly their Exclusion Presets), and those presets |
| `GET /api/plugins?listingType=phone` | the Plugins a Search of that Listing Type can use, with German title, kind and settings schema; without `listingType` all Plugins |
| `GET`, `POST /api/searches` | list and create Searches |
| `GET`, `PUT`, `DELETE /api/searches/{id}` | read, edit, delete a Search |
| `POST /api/searches/{id}/pause`, `/resume` | pause and resume |
| `GET /api/searches/{id}/history` | the Listing History, newest first, each Listing with its Verdict |

Invalid input gives `400` with `{"errors": [{"field": "finders[0].settings.maxListings", "message": "..."}]}`.

The settings schemas are JSON Schema (draft 2020-12), generated from the settings records. A frontend renders its forms from them and knows no Plugin:
- `type`, `required`, and constraints like `minimum` or `minLength`,
- `title` as the field label, `default` as the initial value,
- enum values as `oneOf` of `const` (what to send) and `title` (what to show).

## Adding a Plugin

A Plugin is one settings record plus one class. Nothing else in Octo changes. See ADR [0001](docs/adr/0001-stateless-plugins-with-typed-settings.md).

1. **Write the settings record** in `plugins/<name>/`. The frontend's form is generated from it:
   ```java
   public record ExampleSettings(
           @Schema(title = "Suchlink") @NotBlank String searchUrl,
           @Schema(title = "Max. Anzeigen pro Lauf", defaultValue = "20") @Min(1) int maxListings) {
   }
   ```
   - Bean Validation annotations are checked on save and before every run, and show up as schema constraints.
   - `@Schema(title, description, defaultValue)` gives the label, help text and initial value.
   - An amount in cents is an integer named `...InCents` with `@Schema(format = "money-cents")`. The frontend shows a euro input and sends cents, so the title has no "(Cent)".
   - Primitive fields are required. Use a wrapper type (`Integer`) for an optional number.
   - An enum shows its values with labels if it has a `String label()` method.
2. **Write the Plugin class**: a `@Component` implementing `Finder`, `Evaluator` or `Notifier`, with
   - `id()`: unique, e.g. `example-finder`,
   - `title()`: the German name the frontend shows, e.g. `Beispiel`,
   - `listingTypes()`: the ids of the Listing Types it supports (Notifiers support all),
   - `settingsType()`: the record from step 1,
   - the method of its kind, which gets the Search's settings on every call.

   Plugins are stateless singletons: technical resources (HTTP client, cache) are fine, per-Search state is not. A failure throws `PluginException` with the Plugin's id. The run reports it and carries on.
3. **Test it** with its own tests at the Plugin, e.g. against saved marketplace pages (see `KleinanzeigenFinderTest`).

The Plugin registry finds the bean by itself, `GET /api/plugins` lists it, and Searches can use it.
