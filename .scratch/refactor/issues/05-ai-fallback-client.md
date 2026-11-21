# 05: Reusable AI client with fallback chain

**What to build:** Any Plugin can call an AI model through one reusable client in infra. A call takes a system prompt, a user template with parameters, images and a typed response. The client:
- tries the configured models in priority order,
- skips models that are cooling down after capacity problems (429/503/504/timeout),
- fails immediately on an invalid API key,
- gives readable root-cause messages.

The current Rebuy assessment switches to it, and its behaviour stays the same.

See spec "AI infrastructure" and user stories 66, 87.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The Gemini client configuration and the fallback chain properties live in infra, not in the Rebuy package
- [ ] Rebuy's assessment contains only its prompt, parameters and response type
- [ ] Tests with a fake model:
  - falls through to the next model on failure
  - skips a model that is cooling down
  - tries all models when all are cooling down
  - stops on an invalid key
  - collects the failure messages
- [ ] Existing Rebuy behaviour on the old path is unchanged
