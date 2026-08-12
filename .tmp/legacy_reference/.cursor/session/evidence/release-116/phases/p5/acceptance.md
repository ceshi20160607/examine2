# P5 Flow and approval phase acceptance

- phase: `P5_FLOW`
- predecessor: `P4_PHASE_ACCEPTANCE`
- acceptedAt: `2026-08-07T17:16:00+08:00`
- verdict: `PASS`
- successor: `P6_PHASE_ACCEPTANCE`

## Frozen requirement audit

The existing Flow outcomes cover definition publication, record triggers and
status mapping, manual/periodic start, sequential/conditional/parallel/
inclusive routing, approval modes/sources/stages/deadlines/evidence,
delegation/claim/transfer/add/reduce/return/copy/withdraw/terminate, durable
completion, compensation, external work, subflow and history.

The final form/node-catalog gap is proved by
`.cursor/session/evidence/cycle115-flow-form-node-catalog/acceptance.md` and its
Cycle116 UI/runtime integration evidence at
`.cursor/session/evidence/cycle116-flow-ui-journey/acceptance.md`.

- All 22 frozen nodes have validated configuration and durable behavior.
- Immutable exact-version form policy controls real record read/write/history.
- Admin draft/impact/publish and member form/execute/resume/history use the real
  APIs and owner paths.
- No-op writes do not create false record or form versions.

Automation dispatch and upstream AI inference remain explicitly separated
owners; their request flags are not treated as completion.

## Integration gates

`FlowExtensionServiceTest` `7/7`, real-MySQL Flow extension journey `2/2`, final
backend `1,786/1,786`, frontend `687/687` and packaged cold start all passed.

This phase acceptance is not release acceptance or user sign-off. Formal
checkpoint attempts remain `0/3`.

