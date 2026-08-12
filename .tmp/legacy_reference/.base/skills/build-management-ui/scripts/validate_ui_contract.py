#!/usr/bin/env python3
"""Validate semantic management-UI contracts without third-party packages."""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path
from typing import Any, Dict, Iterable, List


FORBIDDEN_DETAIL_ACTION_IDS = {"view", "detail", "details", "open", "open_detail", "view_detail"}
HIGH_RISK_OPTIONAL_ENTRIES = {
    "favorite_module",
    "follow_record",
    "operations",
    "reports",
    "saved_views",
}
REQUIRED_STATES = ("loading", "empty", "error", "permissionDenied")


class ContractError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def require_dict(value: Any, path: str) -> Dict[str, Any]:
    require(isinstance(value, dict), f"{path} must be an object")
    return value


def require_list(value: Any, path: str) -> List[Any]:
    require(isinstance(value, list), f"{path} must be an array")
    return value


def nonempty(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def unique_ids(items: Iterable[Any], path: str) -> None:
    seen = set()
    for index, raw in enumerate(items):
        item = require_dict(raw, f"{path}[{index}]")
        item_id = item.get("id")
        require(nonempty(item_id), f"{path}[{index}].id must be non-empty")
        require(item_id not in seen, f"{path} contains duplicate id '{item_id}'")
        seen.add(item_id)


def validate_action(action: Any, path: str, selection_expected: bool | None = None) -> None:
    action = require_dict(action, path)
    for field in ("id", "label", "purpose", "kind", "requirementId"):
        require(nonempty(action.get(field)), f"{path}.{field} must be non-empty")
    require(action.get("kind") in {"primary", "secondary", "danger"}, f"{path}.kind is invalid")
    require(action.get("compact") is True, f"{path} must be compact")
    require(isinstance(action.get("requiresSelection"), bool), f"{path}.requiresSelection must be boolean")
    if selection_expected is not None:
        require(
            action["requiresSelection"] is selection_expected,
            f"{path}.requiresSelection must be {str(selection_expected).lower()}",
        )


def validate_contract(contract: Any) -> None:
    root = require_dict(contract, "contract")
    require(root.get("contractVersion") == 1, "contractVersion must be 1")
    require(nonempty(root.get("pageId")), "pageId must be non-empty")
    require(root.get("pageKind") == "management-list-detail", "pageKind must be management-list-detail")

    module = require_dict(root.get("module"), "module")
    require(nonempty(module.get("title")), "module.title must be non-empty")
    require(nonempty(module.get("entityLabel")), "module.entityLabel must be non-empty")
    requirement_ids = require_list(module.get("requirementIds"), "module.requirementIds")
    require(requirement_ids and all(nonempty(item) for item in requirement_ids), "module.requirementIds must be non-empty strings")

    header = require_dict(root.get("header"), "header")
    require(header.get("layout") == "single_row", "header must keep title and actions in one row")
    require(header.get("titleSource") == "module", "header title must come from the module contract")
    header_actions = require_list(header.get("rightActions"), "header.rightActions")
    require(len(header_actions) <= 3, "header may contain at most three compact actions; use More for the rest")
    unique_ids(header_actions, "header.rightActions")
    for index, action in enumerate(header_actions):
        validate_action(action, f"header.rightActions[{index}]", selection_expected=False)
    require(
        sum(1 for action in header_actions if action.get("kind") == "primary") <= 1,
        "header may contain at most one primary action",
    )

    toolbar = require_dict(root.get("toolbar"), "toolbar")
    require(toolbar.get("layout") in {"single_row", "single_row_wrap"}, "toolbar layout must stay compact")
    search = require_dict(toolbar.get("search"), "toolbar.search")
    advanced = require_dict(toolbar.get("advancedFilter"), "toolbar.advancedFilter")
    require(search.get("visible") is True and search.get("mode") == "fuzzy", "toolbar search must be visible fuzzy search")
    require(advanced.get("visible") is True, "advanced filter must be visible")
    require(advanced.get("surface") in {"drawer", "popover"}, "advanced filter must use a drawer or popover")
    require(nonempty(search.get("purpose")), "toolbar.search.purpose must be non-empty")
    require(nonempty(advanced.get("purpose")), "toolbar.advancedFilter.purpose must be non-empty")
    require(search["purpose"] != advanced["purpose"], "fuzzy search and advanced filter must not duplicate the same purpose")

    for transfer_name in ("import", "export"):
        transfer = require_dict(toolbar.get(transfer_name), f"toolbar.{transfer_name}")
        require(isinstance(transfer.get("visible"), bool), f"toolbar.{transfer_name}.visible must be boolean")
        require(transfer.get("compact") is True, f"toolbar.{transfer_name} must be compact")
        if transfer["visible"]:
            require(
                transfer.get("placement") in {"header_right", "toolbar_right", "more"},
                f"visible toolbar.{transfer_name} needs a compact placement",
            )
            require(nonempty(transfer.get("requirementId")), f"visible toolbar.{transfer_name} needs requirementId")
        else:
            require(transfer.get("placement") == "absent", f"hidden toolbar.{transfer_name} placement must be absent")

    batch = require_dict(toolbar.get("batchActions"), "toolbar.batchActions")
    require(batch.get("visibility") == "when_selection_nonempty", "batch actions must appear only after row selection")
    batch_actions = require_list(batch.get("actions"), "toolbar.batchActions.actions")
    unique_ids(batch_actions, "toolbar.batchActions.actions")
    for index, action in enumerate(batch_actions):
        validate_action(action, f"toolbar.batchActions.actions[{index}]", selection_expected=True)

    table = require_dict(root.get("table"), "table")
    require(table.get("headerVisible") is True, "table header must be visible")
    require(table.get("density") in {"compact", "comfortable"}, "table density must be compact or comfortable")
    require(table.get("rowClick") == "open_right_detail", "clicking a row must open right-side detail")
    require(table.get("selectionControl") in {"checkbox", "none"}, "table.selectionControl is invalid")
    if batch_actions:
        require(table["selectionControl"] == "checkbox", "batch actions require checkbox row selection")
    columns = require_list(table.get("columns"), "table.columns")
    require(columns, "table must declare at least one column")
    unique_ids(columns, "table.columns")
    for index, column in enumerate(columns):
        require(nonempty(column.get("label")), f"table.columns[{index}].label must be non-empty")
    row_actions = require_list(table.get("rowActions"), "table.rowActions")
    unique_ids(row_actions, "table.rowActions")
    for index, action in enumerate(row_actions):
        validate_action(action, f"table.rowActions[{index}]", selection_expected=False)
        normalized_id = action["id"].strip().lower().replace("-", "_")
        normalized_purpose = action["purpose"].strip().lower().replace("-", "_")
        require(normalized_id not in FORBIDDEN_DETAIL_ACTION_IDS, f"row action '{action['id']}' duplicates row-click detail")
        require(
            not any(token in normalized_purpose for token in ("view_detail", "open_detail", "show_detail")),
            f"row action '{action['id']}' has a redundant detail purpose",
        )

    detail = require_dict(root.get("detail"), "detail")
    require(detail.get("surface") == "right_drawer", "detail must use a right drawer/workspace")
    require(detail.get("layout") == "tabs", "detail must use tabs")
    require(detail.get("preserveListContext") is True, "detail must preserve list context")
    require(detail.get("stackedSections") is False, "detail must not stack every section vertically")
    require(detail.get("relatedDataPlacement") == "tabs", "related data must live in tabs")
    tabs = require_list(detail.get("tabs"), "detail.tabs")
    require(tabs, "detail must declare at least the overview tab")
    unique_ids(tabs, "detail.tabs")
    require(tabs[0].get("id") == "overview", "the first detail tab must be overview")
    for index, tab in enumerate(tabs):
        require(nonempty(tab.get("label")), f"detail.tabs[{index}].label must be non-empty")
        require(nonempty(tab.get("purpose")), f"detail.tabs[{index}].purpose must be non-empty")

    optional_entries = require_list(root.get("optionalEntries"), "optionalEntries")
    unique_ids(optional_entries, "optionalEntries")
    declared_high_risk = set()
    for index, entry in enumerate(optional_entries):
        path = f"optionalEntries[{index}]"
        entry_id = entry["id"]
        require(isinstance(entry.get("defaultVisible"), bool), f"{path}.defaultVisible must be boolean")
        require(entry.get("placement") in {"absent", "more", "navigation", "row_or_detail", "detail_tab"}, f"{path}.placement is invalid")
        if entry_id in HIGH_RISK_OPTIONAL_ENTRIES:
            declared_high_risk.add(entry_id)
        if entry["defaultVisible"]:
            require(nonempty(entry.get("requirementId")), f"default-visible optional entry '{entry_id}' needs an explicit requirementId")
            require(entry.get("placement") != "absent", f"default-visible optional entry '{entry_id}' cannot be absent")
        else:
            require(entry.get("placement") in {"absent", "more"}, f"hidden optional entry '{entry_id}' cannot occupy primary UI")
        if entry_id == "follow_record" and entry["defaultVisible"]:
            require(entry.get("placement") == "row_or_detail", "record following belongs only to row/detail context")
    require(
        declared_high_risk == HIGH_RISK_OPTIONAL_ENTRIES,
        "optionalEntries must explicitly declare favorite_module, follow_record, operations, reports, and saved_views",
    )

    dashboard = require_dict(root.get("dashboard"), "dashboard")
    require(isinstance(dashboard.get("enabled"), bool), "dashboard.enabled must be boolean")
    if dashboard["enabled"]:
        require(dashboard.get("primaryEditor") == "visual_drag_resize", "dashboard primary editor must support visual drag and resize")
        require(dashboard.get("primarySurface") == "visual_canvas", "dashboard primary surface must be a visual canvas")
        require(dashboard.get("jsonMode") in {"advanced_optional", "disabled"}, "JSON cannot be the primary dashboard editor")
    else:
        require(dashboard.get("primaryEditor") == "not_applicable", "disabled dashboard primaryEditor must be not_applicable")
        require(dashboard.get("primarySurface") == "not_applicable", "disabled dashboard primarySurface must be not_applicable")
        require(dashboard.get("jsonMode") == "not_applicable", "disabled dashboard jsonMode must be not_applicable")

    states = require_dict(root.get("states"), "states")
    for state in REQUIRED_STATES:
        require(states.get(state) is True, f"states.{state} must be explicitly supported")


def load_contract(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ContractError(f"cannot read {path}: {exc}") from exc


def self_test() -> None:
    example_path = Path(__file__).resolve().parent.parent / "references" / "example.json"
    good = load_contract(example_path)
    validate_contract(good)

    mutations = []

    def case(name: str, mutate) -> None:
        candidate = copy.deepcopy(good)
        mutate(candidate)
        mutations.append((name, candidate))

    case("always-visible batch actions", lambda value: value["toolbar"]["batchActions"].update(visibility="always"))
    case("redundant view row action", lambda value: value["table"]["rowActions"][0].update(id="view", purpose="view_detail"))
    case("unrequired Operations entry", lambda value: value["optionalEntries"][2].update(defaultVisible=True, placement="navigation"))
    case("JSON-first dashboard", lambda value: value["dashboard"].update(primaryEditor="json", primarySurface="json_editor", jsonMode="primary"))
    case("stacked detail", lambda value: value["detail"].update(layout="stack", stackedSections=True))
    case("missing permission state", lambda value: value["states"].update(permissionDenied=False))
    case("duplicate search/filter purpose", lambda value: value["toolbar"]["advancedFilter"].update(purpose="quick_text_search"))
    case("two primary header actions", lambda value: value["header"]["rightActions"].append({**value["header"]["rightActions"][0], "id": "create-another"}))

    failures = []
    for name, candidate in mutations:
        try:
            validate_contract(candidate)
        except ContractError:
            continue
        failures.append(name)
    if failures:
        raise ContractError("negative self-tests unexpectedly passed: " + ", ".join(failures))
    print(f"PASS: example accepted and {len(mutations)} negative contracts rejected")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("contracts", nargs="*", type=Path, help="JSON contract files")
    parser.add_argument("--self-test", action="store_true", help="run built-in positive and negative cases")
    args = parser.parse_args()
    if not args.self_test and not args.contracts:
        parser.error("provide at least one contract or --self-test")

    try:
        if args.self_test:
            self_test()
        for path in args.contracts:
            validate_contract(load_contract(path))
            print(f"PASS: {path}")
    except ContractError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
