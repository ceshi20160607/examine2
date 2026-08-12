# Test-plan examples

## Cycle close

```json
{
  "schemaVersion": 1,
  "planId": "CYCLE-CUSTOMER-001-VERIFY",
  "level": "cycle",
  "scopeId": "CYCLE-CUSTOMER-001",
  "projectFunctionalComplete": false,
  "performanceExplicitlyEnabled": false,
  "separatePerformancePlan": false,
  "performanceApproval": null,
  "testVerdict": "pass",
  "businessOutcome": "achieved",
  "requirementAcceptance": "pending",
  "acceptanceAuthority": null,
  "acceptanceEvidence": null,
  "fullSuiteRuns": 0,
  "tests": [
    {
      "id": "cycle-integration",
      "category": "cycle_integration",
      "outcomeClass": "regression",
      "command": "run customer cycle integration",
      "executionCount": 1,
      "result": "pass",
      "httpStatuses": [200],
      "actualEvidence": "evidence/customer-cycle-integration.json"
    },
    {
      "id": "real-entry",
      "category": "real_entry",
      "outcomeClass": "positive_business",
      "command": "run customer create-and-readback journey",
      "executionCount": 1,
      "result": "pass",
      "httpStatuses": [200, 201],
      "actualEvidence": "evidence/customer-create-readback.json"
    }
  ]
}
```

This plan may report business achievement because it contains a passed positive business path. Requirement acceptance remains pending until its owner explicitly accepts it.

## Explicit post-functional performance plan

```json
{
  "schemaVersion": 1,
  "planId": "PERF-CUSTOMER-LIST-001",
  "level": "performance_optional",
  "scopeId": "CUSTOMER-LIST",
  "projectFunctionalComplete": true,
  "performanceExplicitlyEnabled": true,
  "separatePerformancePlan": true,
  "performanceApproval": {
    "source": "decisions/PERF-001.md",
    "approvedBy": "project-owner",
    "approvedAt": "2026-08-08T10:00:00+08:00",
    "target": "At 50 concurrent readers and 100000 approved records, list API P95 <= 1200 ms",
    "approvedRecordCount": 100000
  },
  "testVerdict": "planned",
  "businessOutcome": "not_evaluated",
  "requirementAcceptance": "not_evaluated",
  "acceptanceAuthority": null,
  "acceptanceEvidence": null,
  "fullSuiteRuns": 0,
  "tests": [
    {
      "id": "approved-list-load",
      "category": "load",
      "outcomeClass": "nonfunctional",
      "command": "run approved customer list load profile",
      "executionCount": 1,
      "result": "planned",
      "httpStatuses": [],
      "actualEvidence": null,
      "datasetRecords": 100000
    }
  ]
}
```

Do not change the target to a million records unless the approval source explicitly authorizes that number.
