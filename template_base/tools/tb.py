from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from template_base.contract import ContractError, load_contract, require_valid_contract
from template_base.architecture import (
    ArchitectureError,
    load_architecture,
    require_architecture_ready,
    validate_architecture,
)
from template_base.delivery import delivery_status
from template_base.generator import GenerationError, check_repeatable, generate_project
from template_base.requirements import (
    RequirementError,
    build_intake,
    load_analysis,
    build_analysis_plan,
    merge_analysis_fragments,
    promote_contract,
    read_source_blocks,
    validate_analysis,
)
from template_base.workspace import WorkspaceError, load_workspace, workspace_status


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="tb", description="Template Base deterministic project tooling")
    commands = root.add_subparsers(dest="command", required=True)

    validate = commands.add_parser("validate", help="validate a project contract and its source hash")
    validate.add_argument("--contract", required=True, type=Path)

    generate = commands.add_parser("generate", help="generate a project from a validated contract")
    generate.add_argument("--contract", required=True, type=Path)
    generate.add_argument("--output", required=True, type=Path)

    check = commands.add_parser("check", help="prove generation is repeatable and idempotent")
    check.add_argument("--contract", required=True, type=Path)

    for command, help_text in (
        ("status", "report engineering, user-acceptance and release gates separately"),
        ("release-check", "fail unless engineering evidence and explicit user acceptance both pass"),
    ):
        status = commands.add_parser(command, help=help_text)
        status.add_argument("--contract", required=True, type=Path)
        status.add_argument("--output", required=True, type=Path)
        status.add_argument("--evidence", type=Path)
        status.add_argument("--acceptance", type=Path)

    intake = commands.add_parser("intake", help="build a deterministic block index for a UTF-8 Markdown requirement")
    intake.add_argument("--requirements", required=True, type=Path)
    intake.add_argument("--output", required=True, type=Path)

    requirements_validate = commands.add_parser("requirements-validate", help="validate traceability, coverage and decisions")
    requirements_validate.add_argument("--analysis", required=True, type=Path)

    promote = commands.add_parser("contract-promote", help="promote a fully traced and decided contract candidate")
    promote.add_argument("--analysis", required=True, type=Path)
    promote.add_argument("--candidate", required=True, type=Path)
    promote.add_argument("--output", required=True, type=Path)

    source_slice = commands.add_parser("requirements-slice", help="read only selected source blocks from a current intake")
    source_slice.add_argument("--intake", required=True, type=Path)
    source_slice.add_argument("--block", required=True, action="append")

    analysis_plan = commands.add_parser("requirements-plan", help="partition an intake into bounded non-overlapping agent work packets")
    analysis_plan.add_argument("--intake", required=True, type=Path)
    analysis_plan.add_argument("--output", required=True, type=Path)
    analysis_plan.add_argument("--max-blocks", type=int, default=12)
    analysis_plan.add_argument("--max-lines", type=int, default=200)
    analysis_plan.add_argument("--scope", type=Path)

    merge = commands.add_parser("requirements-merge", help="merge exactly one hash-bound fragment for every analysis packet")
    merge.add_argument("--plan", required=True, type=Path)
    merge.add_argument("--fragment", required=True, action="append", type=Path)
    merge.add_argument("--output", required=True, type=Path)

    architecture_validate = commands.add_parser("architecture-validate", help="validate a source-bound architecture baseline")
    architecture_validate.add_argument("--baseline", required=True, type=Path)

    architecture_ready = commands.add_parser("architecture-ready", help="fail unless architecture is valid and all decisions are resolved")
    architecture_ready.add_argument("--baseline", required=True, type=Path)

    workspace_validate = commands.add_parser("workspace-validate", help="validate active project, template and read-only legacy boundaries")
    workspace_validate.add_argument("--workspace", required=True, type=Path)
    return root


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "intake":
            result = build_intake(args.requirements, args.output)
        elif args.command == "requirements-validate":
            analysis_path = args.analysis.resolve()
            result = validate_analysis(load_analysis(analysis_path), analysis_path)
        elif args.command == "contract-promote":
            result = promote_contract(args.analysis, args.candidate, args.output)
        elif args.command == "requirements-slice":
            result = read_source_blocks(args.intake, args.block)
        elif args.command == "requirements-plan":
            result = build_analysis_plan(args.intake, args.output, args.max_blocks, args.max_lines, args.scope)
        elif args.command == "requirements-merge":
            result = merge_analysis_fragments(args.plan, args.fragment, args.output)
        elif args.command in {"architecture-validate", "architecture-ready"}:
            baseline_path = args.baseline.resolve()
            architecture = load_architecture(baseline_path)
            issues = validate_architecture(architecture, baseline_path)
            if issues:
                raise ArchitectureError(issues)
            if args.command == "architecture-ready":
                require_architecture_ready(architecture, baseline_path)
            result = {
                "valid": True,
                "ready": not architecture["unresolvedDecisions"],
                "applicationKind": architecture["application"]["kind"],
                "businessModuleRealization": architecture["application"]["businessModuleRealization"],
            }
        elif args.command == "workspace-validate":
            workspace_path = args.workspace.resolve()
            result = workspace_status(load_workspace(workspace_path), workspace_path)
        else:
            contract_path = args.contract.resolve()
            contract = load_contract(contract_path)
            if args.command == "validate":
                require_valid_contract(contract, contract_path)
                result = {"valid": True, "projectId": contract["project"]["id"], "modules": len(contract["modules"])}
            elif args.command == "generate":
                result = generate_project(contract, contract_path, args.output)
            elif args.command == "check":
                result = check_repeatable(contract, contract_path)
            elif args.command in {"status", "release-check"}:
                result = delivery_status(contract_path, args.output, args.evidence, args.acceptance)
                if args.command == "release-check" and not result["releaseReady"]:
                    print(json.dumps({"ok": False, **result}, ensure_ascii=False, indent=2), file=sys.stderr)
                    return 3
            else:
                raise AssertionError(f"unsupported command: {args.command}")
    except (ArchitectureError, ContractError, GenerationError, RequirementError, WorkspaceError) as error:
        print(json.dumps({"ok": False, "error": str(error)}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 2
    print(json.dumps({"ok": True, **result}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
