# 03: Rebuy catalogs load independently per Reference Product

**What to build:** The Rebuy catalog cache holds one lock across the whole HTTP load, so while any catalog loads, every Search using the Rebuy Evaluator waits, even for a different Reference Product. Loads for different Reference Products run independently; concurrent requests for the same Reference Product still load it only once.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] A slow load of one Reference Product doesn't delay a lookup of another (test with a blocking fake loader)
- [ ] Two concurrent lookups of the same uncached Reference Product trigger one load
- [ ] TTL and "last good catalog when Rebuy is unreachable" behave as before
