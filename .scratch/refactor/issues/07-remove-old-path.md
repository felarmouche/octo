# 07: Remove the old path

**What to build:** Only the new architecture remains. The two Searches built in code (generic and iPhone) keep running. These are deleted:
- the old runner, `SearchSettings` and the old in-memory Search
- the `XxxI`/`BaseXxx` types and the per-kind exceptions
- the old Kleinanzeigen, Telegram and Rebuy classes
- `RebuyPhoneFilter`, `DamageTextFilter`, `AIConnection` and other dead code

**Blocked by:** 06

**Status:** ready-for-agent

- [x] No references to the removed types remain; the build and all tests pass
- [x] The dependency rule holds: nothing depends on application or plugins, and core depends on nothing else in Octo
- [x] Both in-code Searches still run and alert as before
