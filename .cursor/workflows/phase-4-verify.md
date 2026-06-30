# Phase 4: Verify

## Entry

- current build milestone tasks are accepted
- affected journey gates have declared evidence scripts
- release/package entry exists when the product must be deployable

## Steps

| # | Actor | Output |
|---|---|---|
| 1 | test | run role-level browser/API journey scripts |
| 2 | test | run release/package verification when applicable |
| 3 | review-gate | produce gate verification evidence |
| 4 | conductor | update session state from evidence only |
| 5 | user | verify or sign off subjective usability and final acceptance |

## Exit

- [ ] all required journey gates pass
- [ ] release/package verification passes when applicable
- [ ] review gate passes
- [ ] user trial has no P0 feedback
- [ ] `gates.user_script_passed = true` only after user verification/signoff

## Failure

If any verification fails:

- do not package or claim final completion
- update the final goal ledger, journey gate, task card, or issue registry with the exact gap
- route the fix back to design, contract, build, or verify
- ask the user only when the next decision changes the final product goal, workflow, or subjective usability target
