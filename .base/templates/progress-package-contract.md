# Project progress and package checkpoint contract

This contract is project-agnostic. An instance copies the two progress templates
to `session/project-progress.json` and `session/PROJECT_PROGRESS.md`, replaces the
generic ledger with evidence-backed project outcomes, and keeps the JSON as the
sole machine-readable whole-project progress source.

## Progress ledger

- Freeze outcomes from accepted product results, not code, file, commit or time
  counts.
- Give every outcome one stable token id, title, phase, positive integer weight,
  status, nullable acceptance evidence path, nullable blocking reason and a list
  of dependency ids.
- Use only `completed`, `in_progress`, `remaining` and `deferred`. Deferred weight
  stays in the denominator. Only the current outcome may be `in_progress`.
- A completed outcome must point to an existing `acceptance.md` whose verdict is
  `PASS`. Outcomes without that direct evidence remain non-completed.
- PASS recognition is deliberately narrow: use an explicit `...verdict: PASS`
  assignment (including `engineering_verdict: pass` and a documented `PASS_*`
  verdict token), or put `PASS` / `PASS_*` on the first nonblank line after a
  Markdown `Verdict` heading. Ordinary test-output lines containing `passed` do
  not establish an acceptance verdict.
- Calculate percentage as completed outcome units divided by total outcome units,
  multiplied by 100 and rounded to one decimal with midpoint values away from
  zero. Counts and units for every status must equal the ledger.
- Do not hand-edit the concise Markdown shape. It is the validator's canonical
  projection of JSON and contains exactly three package rows.

## Package checkpoints

Rolling four-hour delivery-cycle packages are runnable local snapshots, not
formal package checkpoints. A cycle records its package path, SHA-256, cold
start/health result and completed/remaining module inventory in the current
engineering-node evidence. It belongs to the currently active formal
checkpoint, but it does not add a checkpoint row or change checkpoint state.
Routine tasks never create packages; one rolling package is created exactly
once at the end of each delivery cycle.

There are exactly three checkpoint ids, in order:

1. `CP1_FUNCTIONAL_BASELINE`
2. `CP2_FEATURE_COMPLETE`
3. `CP3_RELEASE_CANDIDATE`

Checkpoint states are `not_ready`, `ready`, `packaged` and `superseded`.
`ready` and later states require every declared outcome to be completed and
every declared verification command to have PASS evidence. CP2 cannot become
ready before CP1 is packaged or superseded; CP3 cannot become ready before CP2
is packaged or superseded.

Every package run appends a globally unique attempt beneath the same checkpoint
id. A passing attempt points to a real package file, its `sha256:` hash and PASS
evidence. A failed attempt points only to its failure evidence. A packaged or
superseded checkpoint has at least one passing attempt. An earlier packaged
checkpoint becomes superseded when a later checkpoint is packaged; CP3 cannot
be superseded.

## Validation

Run `scripts/render-project-progress.ps1` after changing the JSON to write the
canonical UTF-8 Markdown projection, then run
`scripts/validate-project-progress.ps1` directly for focused validation.
Normal `scripts/validate-framework.ps1` Structure, Active and Release gates run
the same read-only validation automatically. The validator never creates a
package, changes progress or executes a declared verification command.
