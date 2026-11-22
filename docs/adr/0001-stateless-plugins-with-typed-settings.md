# Stateless Plugins with typed settings per call

Every Plugin (Finder, Evaluator, Notifier) is one stateless singleton that declares an id, the Listing Types it supports and its settings type, a Java record. The run passes the Search's settings for that Plugin on every call (`find(settings, listingType)`, `evaluate(listing, settings)`, `send(alerts, settings)`). A Plugin may hold technical resources (HTTP client, caches, AI client) but never per-Search state. A Search stores each Plugin's settings as JSON next to the Plugin id, and the settings codec turns that JSON back into the typed record and validates it, on save and before each run. The frontend gets its forms from a JSON Schema generated from the settings record, so it never knows any Plugin.

We chose this over one Plugin instance per Search built by a factory. Per-Search instances would have hidden settings inside objects, made it unclear what a Plugin may cache across Searches, and required a factory per Plugin. With settings per call, adding a Plugin means writing one settings record and one Plugin class, all Plugins receive their settings the same way, and there's one place (the Search) where settings live.

## Consequences

- A cache inside a Plugin must be keyed by whatever in the settings it depends on (e.g. the Rebuy catalog is cached per Reference Product URL), because many Searches share the one instance.
- Settings records are the contract with stored data and the frontend: renaming or removing a field needs a migration of the stored JSON.
