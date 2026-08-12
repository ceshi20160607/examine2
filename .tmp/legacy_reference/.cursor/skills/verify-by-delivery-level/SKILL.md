---
name: verify-by-delivery-level
description: Select, review, and validate the smallest non-duplicative test plan for task, cycle, module, phase, project, or explicitly enabled post-functional performance work. Use when planning tests, defining acceptance evidence, reviewing whether a test scope is excessive or insufficient, preventing repeated full regression, separating test PASS from business achievement and requirement acceptance, or deciding whether load/capacity/million-record testing is allowed.
---

# Verify By Delivery Level

Choose verification by delivery level, not by the number of available tests.

## Workflow

1. Identify exactly one level: `task`, `cycle`, `module`, `phase`, `project`, or `performance_optional`.
2. Read [references/policy.md](references/policy.md) for the required and forbidden checks at that level.
3. Produce a JSON plan using [references/example.md](references/example.md).
4. Validate it before execution:

   ```text
   python scripts/validate_test_plan.py path/to/test-plan.json
   ```

5. For a project cycle, use the framework runner from the repository root; validation alone is never a VERIFY result:

   ```text
   powershell -NoProfile -ExecutionPolicy Bypass -File .cursor/scripts/run-verified-test-plan.ps1 -Plan path/to/test-plan.json -EvidenceRoot .cursor/session/evidence/baseline/<scopeId>
   ```

6. Execute each declared command no more than its declared `executionCount`.
7. Require exit code `0` and a parsed executed-test count greater than `0`; a missing test is a failure.
8. Record command hash, source commit, plan hash, log hash and per-test evidence under the current baseline evidence root.
9. Do not upgrade a test result into a business or requirement decision. Revalidate the completed plan only after its actual evidence fields are updated.

## Non-negotiable rules

- At `task`, run only affected unit tests and affected compilation. Do not run a full suite, browser matrix, package/restart cycle, or performance test.
- At `cycle`, run one combined integration check and one real-entry check at cycle close. Do not repeat the project full suite.
- At `module`, verify the completed module through its real page journey and persisted readback.
- At `phase`, verify the complete promised phase result across its modules.
- At `project`, run full functional acceptance only after all planned functional scope is complete.
- Run `performance_optional` only as a separate plan after project functional completion and only when the project explicitly enables it with an approval source and target. Million-record, load, and capacity tests are never implicit.
- Keep `testVerdict`, `businessOutcome`, and `requirementAcceptance` separate:
  - test PASS means the declared checks passed;
  - business achieved requires a successful positive business path and actual evidence;
  - requirement accepted requires explicit acceptance authority and acceptance evidence.
- A build, error response, permission denial, `403`, empty state, or failure-path assertion cannot alone prove business success.
- A plan validator, successful command lookup, skipped test, or zero executed tests cannot prove test PASS.
- Never use `failIfNoSpecifiedTests=false` or an equivalent option that turns a missing named test into success.
- Maven PASS must be derived from Surefire XML written after a clean build by the current command, in the declared target module, for every declared selector: `tests - skipped > 0`, `failures = 0`, and `errors = 0`. Console summaries are not authoritative evidence.
- In a Maven reactor, use `.cursor/scripts/run-maven-focused-test.ps1` for named tests. Its shared Maven window serializes access to reactor `target` directories; dependencies are clean-installed with tests skipped before the named test runs only in its owning module. The verified runner copies each XML into immutable cycle evidence before the next command can clean it.
- New delivery progress cannot cite historical evidence from another rebuild, release, fast path, capacity run or performance run.
- Never repeat full regression at task, cycle, module, phase, or performance level.

## Stop conditions

Reject or rewrite a plan when:

- the level is selected from habit instead of the delivery result;
- an upstream level repeats checks already owned by a lower level without an integration reason;
- a PASS is based only on negative/error evidence;
- a VERIFY task runs only the plan validator, permits missing tests, or produces zero executed tests;
- evidence is outside the current baseline scope or does not bind command, plan, source and log hashes;
- business achievement or requirement acceptance is inferred from test PASS;
- performance work starts before functional completion or without explicit project approval;
- a task or cycle schedules a full regression, browser matrix, or million-record seed.

## Validator

Run the embedded positive and negative cases after changing the policy or validator:

```text
python scripts/validate_test_plan.py --self-test
```

The validator checks level/category ownership, required checks, verdict consistency, positive-business evidence, acceptance authority, duplicate commands, full-suite frequency, and performance prerequisites.
