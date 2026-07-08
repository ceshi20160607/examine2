# Framework V8 Flow Blueprint Rebuild Contract

Time: 2026-07-02 Asia/Shanghai

Status: active framework upgrade.

## Why

The user reported that continued fixes still risk becoming unlimited and still do not make the product feel like one coherent system.

The failure class is now:

- local pages can be improved while the global entry flow remains unclear
- role routing can appear to work while the user does not understand which layer they are in
- admin configuration can pass API readback while still feeling like a pile of panels
- runtime pages can pass browser checks while the whole product still lacks a first-use path

## Contract

All future implementation starts from:

- `docs/framework/final-system-flow-blueprint.md`

No coding task is ready unless it names:

- flow id
- target role
- entry URL or navigation action
- user job
- frontend surface
- backend/data/readback contract
- permission positive and negative cases
- states and copy
- generated plumbing versus coded behavior
- deterministic evidence

## Immediate Task

Current active task:

- `REC-P0-060 Flow Blueprint And Rebuild Contract Lock`

Next likely coding task after R60:

- `REC-P0-059 FRC-2/FRC-6 Admin Configuration Human First-Use Closure`

Reason: R59 is still important, but it must follow the flow blueprint instead of becoming another isolated admin-page repair.

## Signoff Boundary

This upgrade does not close the final product goal.

`gates.user_script_passed` remains `false` until explicit user verification or signoff.
