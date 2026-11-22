# 06: Every API error has the same body shape

**What to build:** A frontend handles every error the same way. Today 400 has `{errors:[{field,message}]}`, 404 has an empty body, and 401/403/500 use Spring's default bodies. All of them return the 400 shape (with `field` only where an input field is at fault). An `IllegalArgumentException` escaping settings decoding becomes a 400, not a 500. A 500 never leaks a stack trace.

**Blocked by:** None (can start immediately). 05 is wontfix.

**Status:** ready-for-agent

- [ ] 400, 401, 403, 404 and 500 all return `{errors:[{field?, message}]}`
- [ ] Unknown `listingType` and other non-field input errors return 400 without a `field`
- [ ] Invalid input that slips past validation returns 400, not 500
- [ ] API tests cover each status
