# Delivery-level verification policy

## Status meanings

| Field | Meaning | Who can assert it |
|---|---|---|
| `testVerdict` | The checks declared by this plan passed, failed, are blocked, or remain planned. | Test executor/verifier from actual results. |
| `businessOutcome` | A target role completed the promised positive result through a real entry and the result was read back. | Delivery owner plus independent evidence. |
| `requirementAcceptance` | The requirement owner accepted the delivered behavior. | Declared acceptance authority only. |

Never derive one field mechanically from another. A test plan may be `pass` while the business outcome remains `not_evaluated`, and an achieved outcome may remain `pending` acceptance.

## Level matrix

| Level | Purpose | Required categories | Optional categories | Forbidden |
|---|---|---|---|---|
| `task` | Keep the changed code green. | At least one of `affected_unit`, `affected_compile`. | The other task category. | Integration suites, browser/real-entry journeys, packaging, full functional, performance/load/capacity. |
| `cycle` | Close one delivery cycle once. | Exactly one `cycle_integration`; exactly one `real_entry`. | `api_data_readback`, `permission_path`, `package_smoke`. | Full functional suite, performance, repeated integration/entry execution. |
| `module` | Prove a completed module from its page. | `page_journey`, `api_data_readback`. | `permission_path`. | Project full suite and performance. |
| `phase` | Prove the complete promised phase result. | `phase_functional`, `cross_module_journey`. | `api_data_readback`, `permission_path`. | Project full suite and performance. |
| `project` | Perform full functional acceptance after functional completion. | Exactly one `full_functional`; `end_to_end_journey`. | `api_data_readback`, `permission_path`, `release_smoke`. | Performance/load/capacity; these use a separate optional plan. |
| `performance_optional` | Run an explicitly approved non-functional specialty after functional completion. | At least one of `performance`, `load`, `capacity`. | The other performance categories. | Functional acceptance categories and any claim of business or requirement acceptance. |

## Evidence classes

Each test declares one `outcomeClass`:

- `build`: compilation or static build result.
- `regression`: existing behavior remains green.
- `positive_business`: a target role reaches the promised successful result.
- `negative_permission`: unauthorized behavior is rejected.
- `error_handling`: invalid or failed behavior is understandable and safe.
- `nonfunctional`: measured performance, load, or capacity behavior.

`businessOutcome: achieved` requires a passed `positive_business` test with non-empty `actualEvidence`. Negative permission, error handling, build, and regression evidence are valuable but cannot substitute for it. A `positive_business` test cannot declare `403` as its only HTTP status.

## Frequency and duplication

- Set every test `executionCount` to `1` unless the approved performance target explicitly requires repetitions inside the performance tool.
- Represent tool-internal samples in the performance target, not by repeating the plan command.
- Use `fullSuiteRuns: 0` at all levels except `project`; a passing project plan uses exactly `1`.
- Reject duplicate normalized commands inside one plan.
- Reject `failIfNoSpecifiedTests=false` and equivalent skip-on-missing options.
- For Maven, accept only current-run, post-clean Surefire XML from the declared module, require a report for every named selector, require at least one non-skipped test and zero failures/errors, and copy reports into evidence before any later clean. Do not sum repeated console summaries.
- Use the framework focused-test runner for reactor modules instead of combining `-am` with a named `-Dtest` selector.
- A successful execution has both process exit code `0` and an executed-test count greater than `0`; command success without an actual test is failure.
- Do not rerun lower-level unit suites independently at cycle/module/phase close. Their results are inputs; the higher level runs only its integration or journey obligation.

## Cycle execution evidence

The JSON validator proves only that a plan is structurally and semantically allowed. It never executes tests and never closes a VERIFY task.

For a cycle, the framework runner must bind evidence to:

- cycle `scopeId` and test-plan SHA-256;
- source commit and dirty-state flag;
- exact command and command SHA-256;
- process exit code and parsed executed-test count;
- raw log path and SHA-256;
- per-test verdict and an aggregate manifest verdict.

Write new evidence only to `.cursor/session/evidence/baseline/<scopeId>/`. Evidence from prior rebuilds, releases, fast paths, capacity work or performance runs cannot be assigned to the current cycle.

## Performance authorization

A `performance_optional` plan must declare all of:

- `projectFunctionalComplete: true`;
- `performanceExplicitlyEnabled: true`;
- `separatePerformancePlan: true`;
- `performanceApproval.source`, `approvedBy`, `approvedAt`, measurable `target`, and integer `approvedRecordCount`.

Every performance test declares `datasetRecords`; it cannot exceed `approvedRecordCount`. Do not invent record counts, concurrency, percentiles, or response thresholds. Keep performance absent when the project has not explicitly selected it. Million-record testing is permitted only when at least one million records are explicitly approved.

## Acceptance authority

For `requirementAcceptance: accepted`, require:

- `testVerdict: pass`;
- `businessOutcome: achieved`;
- a non-empty `acceptanceAuthority` identifying the authorized requirement owner;
- a non-empty `acceptanceEvidence` pointing to an explicit decision.

Engineering evidence cannot set acceptance on behalf of the user or requirement owner.
