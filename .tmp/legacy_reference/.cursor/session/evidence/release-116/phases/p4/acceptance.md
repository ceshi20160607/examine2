# P4 dynamic business runtime phase acceptance

- phase: `P4_DYNAMIC_RUNTIME`
- predecessor: `P3_PHASE_ACCEPTANCE`
- acceptedAt: `2026-08-07T17:16:00+08:00`
- verdict: `PASS`
- successor: `P5_PHASE_ACCEPTANCE`

## Frozen requirement audit

All established P4 slices remain accepted: published reads, create/activate,
edit/autosave/recovery, lifecycle/recycle, typed query/saved views, numeric,
time, selection, sensitive/structured, relation/reference/subtable and derived
fields, plus collaboration/batch/search/favorite/history capabilities.

The final frozen gap is closed by
`.cursor/session/evidence/cycle116-page-rule-file-runtime/acceptance.md`:

- LIST/FORM/DETAIL layouts and rules are read from an immutable published
  snapshot and applied in the member UI;
- every record mutation path enforces hidden/readonly/required/action rules on
  the server;
- PATCH preserves omitted fields and ACTIVATE has distinct semantics;
- ATTACHMENT/IMAGE/FILE_GROUP/SIGNATURE values bind to the authoritative file
  center with touched-only updates and permission checks.

Real MySQL publish/restart journeys, real PNG/signature/PDF evidence, the full
`599`-test module run and final `1,786`-test reactor prove the runtime boundary.

## Integration gates

Frontend `687` tests/build, the `112`-migration populated-upgrade/no-op gate and
the packaged browser record journey all passed. No mutable draft or client-only
rule is used as acceptance evidence.

This phase acceptance is not release acceptance or user sign-off. Formal
checkpoint attempts remain `0/3`.

