---
name: compile-project-delivery
description: Compile one authoritative natural-language requirement source into typed project phases, atomic requirements, acceptance cases, an implementation task DAG, and four-hour delivery cycles. Use when planning or replanning a software project, converting requirements into bounded multi-agent work, checking bidirectional traceability and ambiguity closure, or preventing premature performance work and oversized tasks.
---

# Compile Project Delivery

Produce a machine-validated delivery plan, not a prose task list.

## Workflow

1. Read the single authoritative source directly. Record its path and SHA-256; do not use chat summaries as facts.
2. Extract source-backed candidates. Keep inferred, conflicting, or unanswered items as `candidate` or `blocked`; never promote them silently.
3. Resolve only decisions required by the active slice. Record the authority and evidence for every resolution.
4. Split accepted scope into atomic requirements: one actor, trigger, action, object, and observable outcome. State explicit exclusions.
5. Write accepted Given/When/Then cases and connect both sides of every requirement/case link.
6. Define project phases by dependency and user result. Keep future phases as `outline`; expand only the current phase, and at most the immediately next phase, to `detailed`. Do not pre-create implementation tasks for outline phases.
7. Compile tasks only for detailed phases. Tasks consume accepted contracts, permit no unresolved product judgment, declare Base reuse and exact allowed/forbidden changes, and stay at 120 minutes or less.
8. Form cycles of at most 240 critical-path minutes. Give each cycle exactly one real entry point and one demonstrable acceptance case. Schedule all ready tasks with disjoint file, database, port, and test-resource scopes in parallel.
9. Exclude performance, capacity, million-record, and long-concurrency work from the core delivery plan. A project may create a separate optional plan only after full functional completion and explicit authorization. Functional tasks may run only affected checks.
10. Run the validator. Do not mark a plan ready while any error remains.

Read [references/typed-contract.md](references/typed-contract.md) before authoring the JSON. Use [references/example.json](references/example.json) as a structural example, not as project facts.

## Validate

```powershell
python scripts/validate_delivery_plan.py path/to/delivery-plan.json
```

Optionally emit canonical JSON after validation:

```powershell
python scripts/validate_delivery_plan.py path/to/delivery-plan.json --canonical-output path/to/canonical.json
```

Run the bundled regression checks after changing the contract or validator:

```powershell
python scripts/validate_delivery_plan.py --self-test
```

## Non-negotiable rules

- Reject orphan or one-way requirement, case, task, phase, cycle, decision, artifact, and gate links.
- Reject accepted requirements that depend on unresolved decisions.
- Reject tasks referencing candidate, blocked, or rejected requirements/cases.
- Reject accepted requirements or implementation tasks inside an outline phase.
- Reject tasks longer than 120 minutes and cycles longer than 240 critical-path minutes.
- Reject forced serial scheduling for ready, dependency-free tasks with disjoint write scopes.
- Reject cycles without exactly one demonstrable user result.
- Reject every performance/capacity task and check from the core delivery plan.
- Never infer completion from document volume, tests, builds, packages, or Agent consensus.
