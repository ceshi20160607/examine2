#!/usr/bin/env python3
"""Validate delivery-level test plans with no external dependencies."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


LEVEL_CATEGORIES = {
    "task": {"affected_unit", "affected_compile"},
    "cycle": {"cycle_integration", "real_entry", "api_data_readback", "permission_path", "package_smoke"},
    "module": {"page_journey", "api_data_readback", "permission_path"},
    "phase": {"phase_functional", "cross_module_journey", "api_data_readback", "permission_path"},
    "project": {"full_functional", "end_to_end_journey", "api_data_readback", "permission_path", "release_smoke"},
    "performance_optional": {"performance", "load", "capacity"},
}

REQUIRED_CATEGORIES = {
    "cycle": {"cycle_integration", "real_entry"},
    "module": {"page_journey", "api_data_readback"},
    "phase": {"phase_functional", "cross_module_journey"},
    "project": {"full_functional", "end_to_end_journey"},
}

OUTCOME_CLASSES = {
    "build", "regression", "positive_business", "negative_permission", "error_handling", "nonfunctional"
}
TEST_RESULTS = {"planned", "pass", "fail", "blocked"}
TEST_VERDICTS = TEST_RESULTS
BUSINESS_OUTCOMES = {"not_evaluated", "not_achieved", "achieved"}
REQUIREMENT_ACCEPTANCE = {"not_evaluated", "pending", "accepted", "rejected"}


class PlanError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PlanError(message)


def nonempty(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def validate(plan: dict[str, Any]) -> None:
    required = {
        "schemaVersion", "planId", "level", "scopeId", "projectFunctionalComplete",
        "performanceExplicitlyEnabled", "separatePerformancePlan", "performanceApproval",
        "testVerdict", "businessOutcome", "requirementAcceptance", "acceptanceAuthority",
        "acceptanceEvidence", "fullSuiteRuns", "tests",
    }
    missing = sorted(required - set(plan))
    require(not missing, f"missing plan fields: {', '.join(missing)}")
    require(plan["schemaVersion"] == 1, "schemaVersion must be 1")
    require(nonempty(plan["planId"]), "planId must be non-empty")
    require(nonempty(plan["scopeId"]), "scopeId must be non-empty")

    level = plan["level"]
    require(level in LEVEL_CATEGORIES, f"unknown level: {level}")
    require(isinstance(plan["projectFunctionalComplete"], bool), "projectFunctionalComplete must be boolean")
    require(isinstance(plan["performanceExplicitlyEnabled"], bool), "performanceExplicitlyEnabled must be boolean")
    require(isinstance(plan["separatePerformancePlan"], bool), "separatePerformancePlan must be boolean")
    require(plan["testVerdict"] in TEST_VERDICTS, "invalid testVerdict")
    require(plan["businessOutcome"] in BUSINESS_OUTCOMES, "invalid businessOutcome")
    require(plan["requirementAcceptance"] in REQUIREMENT_ACCEPTANCE, "invalid requirementAcceptance")
    require(isinstance(plan["fullSuiteRuns"], int) and not isinstance(plan["fullSuiteRuns"], bool),
            "fullSuiteRuns must be an integer")
    require(isinstance(plan["tests"], list) and plan["tests"], "tests must be a non-empty array")

    if level == "project":
        require(plan["projectFunctionalComplete"] is True, "project verification requires completed functional scope")
        require(plan["fullSuiteRuns"] == 1, "project verification must run the full functional suite exactly once")
    else:
        require(plan["fullSuiteRuns"] == 0, f"{level} must not run the project full suite")

    if level == "performance_optional":
        require(plan["projectFunctionalComplete"] is True, "performance requires project functional completion")
        require(plan["performanceExplicitlyEnabled"] is True, "performance must be explicitly enabled")
        require(plan["separatePerformancePlan"] is True, "performance must use a separate plan")
        approval = plan["performanceApproval"]
        require(isinstance(approval, dict), "performanceApproval must be an object")
        for field in ("source", "approvedBy", "approvedAt", "target"):
            require(nonempty(approval.get(field)), f"performanceApproval.{field} must be non-empty")
        require(isinstance(approval.get("approvedRecordCount"), int) and
                not isinstance(approval.get("approvedRecordCount"), bool) and
                approval["approvedRecordCount"] >= 0,
                "performanceApproval.approvedRecordCount must be a non-negative integer")
        require(plan["businessOutcome"] == "not_evaluated",
                "performance plan cannot claim business achievement")
        require(plan["requirementAcceptance"] == "not_evaluated",
                "performance plan cannot claim requirement acceptance")
    else:
        require(plan["performanceExplicitlyEnabled"] is False,
                "performance enablement belongs only to performance_optional")
        require(plan["separatePerformancePlan"] is False,
                "separatePerformancePlan is only valid for performance_optional")
        require(plan["performanceApproval"] is None,
                "performanceApproval is only valid for performance_optional")

    ids: set[str] = set()
    commands: set[str] = set()
    categories: list[str] = []
    results: list[str] = []
    positive_evidence = False

    for index, test in enumerate(plan["tests"]):
        label = f"tests[{index}]"
        require(isinstance(test, dict), f"{label} must be an object")
        for field in ("id", "category", "outcomeClass", "command", "executionCount", "result",
                      "httpStatuses", "actualEvidence"):
            require(field in test, f"{label} missing {field}")
        require(nonempty(test["id"]), f"{label}.id must be non-empty")
        require(test["id"] not in ids, f"duplicate test id: {test['id']}")
        ids.add(test["id"])
        category = test["category"]
        require(category in LEVEL_CATEGORIES[level], f"category {category} is forbidden at {level}")
        categories.append(category)
        require(test["outcomeClass"] in OUTCOME_CLASSES, f"invalid outcomeClass in {label}")
        require(nonempty(test["command"]), f"{label}.command must be non-empty")
        normalized_command = " ".join(test["command"].split()).casefold()
        require(normalized_command not in commands, f"duplicate command: {test['command']}")
        commands.add(normalized_command)
        require(test["executionCount"] == 1, f"{label}.executionCount must be 1")
        require(test["result"] in TEST_RESULTS, f"invalid result in {label}")
        results.append(test["result"])
        require(isinstance(test["httpStatuses"], list) and
                all(isinstance(value, int) and not isinstance(value, bool) for value in test["httpStatuses"]),
                f"{label}.httpStatuses must be an integer array")
        if test["result"] == "pass":
            require(nonempty(test["actualEvidence"]), f"passed {label} requires actualEvidence")
        if test["outcomeClass"] == "positive_business":
            statuses = test["httpStatuses"]
            require(not statuses or any(200 <= status < 300 for status in statuses),
                    f"{label} positive business path cannot be proved only by error/403 statuses")
            if test["result"] == "pass" and nonempty(test["actualEvidence"]):
                positive_evidence = True
        if level == "performance_optional":
            require(isinstance(test.get("datasetRecords"), int) and
                    not isinstance(test.get("datasetRecords"), bool) and test["datasetRecords"] >= 0,
                    f"{label}.datasetRecords must be a non-negative integer")
            require(test["datasetRecords"] <= plan["performanceApproval"]["approvedRecordCount"],
                    f"{label}.datasetRecords exceeds the explicitly approved record count")

    category_set = set(categories)
    if level == "task":
        require(bool(category_set), "task needs affected unit or compile verification")
    elif level in REQUIRED_CATEGORIES:
        missing_categories = sorted(REQUIRED_CATEGORIES[level] - category_set)
        require(not missing_categories,
                f"{level} missing required categories: {', '.join(missing_categories)}")
    else:
        require(bool(category_set & {"performance", "load", "capacity"}),
                "performance plan needs performance, load, or capacity verification")

    if level == "cycle":
        require(categories.count("cycle_integration") == 1, "cycle requires exactly one integration check")
        require(categories.count("real_entry") == 1, "cycle requires exactly one real-entry check")
    if level == "project":
        require(categories.count("full_functional") == 1, "project requires exactly one full functional suite")

    expected_verdict = "pass"
    if any(result == "fail" for result in results):
        expected_verdict = "fail"
    elif any(result == "blocked" for result in results):
        expected_verdict = "blocked"
    elif any(result == "planned" for result in results):
        expected_verdict = "planned"
    require(plan["testVerdict"] == expected_verdict,
            f"testVerdict must be {expected_verdict} for declared test results")

    if plan["businessOutcome"] == "achieved":
        require(level != "task", "task checks alone cannot declare business achievement")
        require(plan["testVerdict"] == "pass", "business achievement requires test PASS")
        require(positive_evidence, "business achievement requires passed positive_business evidence")

    if plan["requirementAcceptance"] == "accepted":
        require(plan["testVerdict"] == "pass", "accepted requirement requires test PASS")
        require(plan["businessOutcome"] == "achieved", "accepted requirement requires achieved business outcome")
        require(nonempty(plan["acceptanceAuthority"]), "accepted requirement requires acceptanceAuthority")
        require(nonempty(plan["acceptanceEvidence"]), "accepted requirement requires acceptanceEvidence")
    elif level == "performance_optional":
        require(plan["acceptanceAuthority"] is None and plan["acceptanceEvidence"] is None,
                "performance plan must not carry functional acceptance authority")


def load_plan(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise PlanError(f"cannot read JSON plan: {exc}") from exc
    require(isinstance(value, dict), "plan root must be an object")
    return value


def base_cycle_plan() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "planId": "SELFTEST-CYCLE",
        "level": "cycle",
        "scopeId": "CYCLE-1",
        "projectFunctionalComplete": False,
        "performanceExplicitlyEnabled": False,
        "separatePerformancePlan": False,
        "performanceApproval": None,
        "testVerdict": "pass",
        "businessOutcome": "achieved",
        "requirementAcceptance": "pending",
        "acceptanceAuthority": None,
        "acceptanceEvidence": None,
        "fullSuiteRuns": 0,
        "tests": [
            {
                "id": "integration", "category": "cycle_integration", "outcomeClass": "regression",
                "command": "run cycle integration", "executionCount": 1, "result": "pass",
                "httpStatuses": [200], "actualEvidence": "evidence/integration.json",
            },
            {
                "id": "entry", "category": "real_entry", "outcomeClass": "positive_business",
                "command": "run real entry", "executionCount": 1, "result": "pass",
                "httpStatuses": [201], "actualEvidence": "evidence/entry.json",
            },
        ],
    }


def run_self_test() -> None:
    positive = base_cycle_plan()
    validate(positive)

    negative_cases: list[tuple[str, dict[str, Any], str]] = []

    full_at_task = base_cycle_plan()
    full_at_task.update({"level": "task", "businessOutcome": "not_evaluated", "fullSuiteRuns": 1})
    full_at_task["tests"] = [{
        "id": "full", "category": "affected_compile", "outcomeClass": "build",
        "command": "compile affected module", "executionCount": 1, "result": "pass",
        "httpStatuses": [], "actualEvidence": "evidence/compile.txt",
    }]
    negative_cases.append(("task full-suite", full_at_task, "must not run the project full suite"))

    error_only = base_cycle_plan()
    error_only["tests"][1].update({"outcomeClass": "negative_permission", "httpStatuses": [403]})
    negative_cases.append(("403 as success", error_only, "positive_business evidence"))

    false_positive_403 = base_cycle_plan()
    false_positive_403["tests"][1]["httpStatuses"] = [403]
    negative_cases.append(("403 labelled positive", false_positive_403, "cannot be proved only by error/403"))

    early_perf = base_cycle_plan()
    early_perf.update({
        "level": "performance_optional", "projectFunctionalComplete": False,
        "performanceExplicitlyEnabled": True, "separatePerformancePlan": True,
        "performanceApproval": {
            "source": "decision", "approvedBy": "owner", "approvedAt": "now",
            "target": "P95", "approvedRecordCount": 1000,
        },
        "testVerdict": "planned", "businessOutcome": "not_evaluated",
        "requirementAcceptance": "not_evaluated",
    })
    early_perf["tests"] = [{
        "id": "load", "category": "load", "outcomeClass": "nonfunctional",
        "command": "run load", "executionCount": 1, "result": "planned",
        "httpStatuses": [], "actualEvidence": None, "datasetRecords": 1000,
    }]
    negative_cases.append(("early performance", early_perf, "functional completion"))

    unapproved_million = json.loads(json.dumps(early_perf))
    unapproved_million["projectFunctionalComplete"] = True
    unapproved_million["tests"][0]["datasetRecords"] = 1_000_000
    negative_cases.append(("unapproved million", unapproved_million, "exceeds the explicitly approved"))

    duplicate_cycle = base_cycle_plan()
    duplicate_cycle["tests"].append({
        "id": "entry-2", "category": "real_entry", "outcomeClass": "positive_business",
        "command": "run another entry", "executionCount": 1, "result": "pass",
        "httpStatuses": [200], "actualEvidence": "evidence/entry-2.json",
    })
    negative_cases.append(("duplicate cycle entry", duplicate_cycle, "exactly one real-entry"))

    accepted_without_authority = base_cycle_plan()
    accepted_without_authority["requirementAcceptance"] = "accepted"
    negative_cases.append(("acceptance without authority", accepted_without_authority, "acceptanceAuthority"))

    for name, plan, expected in negative_cases:
        try:
            validate(plan)
        except PlanError as exc:
            require(expected in str(exc), f"self-test {name} returned unexpected error: {exc}")
        else:
            raise PlanError(f"self-test {name} unexpectedly passed")

    print(f"self-test passed: 1 positive, {len(negative_cases)} negative cases")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("plan", nargs="?", type=Path, help="JSON test plan")
    parser.add_argument("--self-test", action="store_true", help="run embedded positive and negative cases")
    args = parser.parse_args()
    try:
        if args.self_test:
            run_self_test()
        else:
            require(args.plan is not None, "provide a JSON plan or --self-test")
            validate(load_plan(args.plan))
            print(f"test plan valid: {args.plan}")
    except PlanError as exc:
        print(f"test plan invalid: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
