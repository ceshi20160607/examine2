from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from template_base.database import DatabaseError, prepare_database
from template_base.generator import GenerationError
from template_base.requirements import (
    RequirementError,
    build_analysis_plan,
    build_intake,
    load_analysis,
    collect_analysis_fragments,
    read_source_blocks,
    validate_analysis_fragment,
    validate_analysis,
)
from template_base.starter import (
    StarterError,
    check_starter_repeatable,
    generate_starter,
    load_starter,
    require_valid_starter,
)
from template_base.workspace import WorkspaceError, computed_project_status, load_workspace, workspace_status


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="tb", description="Template Base deterministic project tooling")
    commands = root.add_subparsers(dest="command", required=True)

    intake = commands.add_parser("intake", help="index every block in a UTF-8 Markdown requirement")
    intake.add_argument("--requirements", required=True, type=Path)
    intake.add_argument("--output", required=True, type=Path)

    requirements_validate = commands.add_parser("requirements-validate", help="validate requirement traceability, coverage and decisions")
    requirements_validate.add_argument("--analysis", required=True, type=Path)

    fragment_validate = commands.add_parser("requirements-fragment-validate", help="validate one source-bound requirement work packet result")
    fragment_validate.add_argument("--fragment", required=True, type=Path)

    source_blocks = commands.add_parser("requirements-read-blocks", help="read selected requirement blocks")
    source_blocks.add_argument("--intake", required=True, type=Path)
    source_blocks.add_argument("--block", required=True, action="append")

    analysis_plan = commands.add_parser("requirements-plan", help="partition every requirement block into bounded work packets")
    analysis_plan.add_argument("--intake", required=True, type=Path)
    analysis_plan.add_argument("--output", required=True, type=Path)
    analysis_plan.add_argument("--max-blocks", type=int, default=12)
    analysis_plan.add_argument("--max-lines", type=int, default=200)

    collect = commands.add_parser("requirements-collect", help="validate and index exactly one analysis result for every work packet")
    collect.add_argument("--plan", required=True, type=Path)
    collect.add_argument("--fragment", required=True, action="append", type=Path)
    collect.add_argument("--output", required=True, type=Path)

    workspace_validate = commands.add_parser("workspace-validate", help="validate active project and template boundaries")
    workspace_validate.add_argument("--workspace", required=True, type=Path)

    project_status = commands.add_parser("project-status", help="compute gate, requirement and audit status from repository evidence")
    project_status.add_argument("--workspace", required=True, type=Path)

    db_prepare = commands.add_parser("db-prepare", help="derive combined SQL and Flyway inputs from versioned database sources")
    db_prepare.add_argument("--project-root", required=True, type=Path)

    starter_validate = commands.add_parser("starter-validate", help="validate a project starter bound to the requirement source")
    starter_validate.add_argument("--starter", required=True, type=Path)

    for command, help_text in (
        ("starter-generate", "generate a new buildable engineering skeleton"),
        ("starter-check", "prove project starter generation is repeatable"),
    ):
        starter_command = commands.add_parser(command, help=help_text)
        starter_command.add_argument("--starter", required=True, type=Path)
        if command == "starter-generate":
            starter_command.add_argument("--output", required=True, type=Path)

    return root


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "intake":
            result = build_intake(args.requirements, args.output)
        elif args.command == "requirements-validate":
            analysis_path = args.analysis.resolve()
            result = validate_analysis(load_analysis(analysis_path), analysis_path)
        elif args.command == "requirements-fragment-validate":
            result = validate_analysis_fragment(args.fragment)
        elif args.command == "requirements-read-blocks":
            result = read_source_blocks(args.intake, args.block)
        elif args.command == "requirements-plan":
            result = build_analysis_plan(args.intake, args.output, args.max_blocks, args.max_lines)
        elif args.command == "requirements-collect":
            result = collect_analysis_fragments(args.plan, args.fragment, args.output)
        elif args.command == "workspace-validate":
            workspace_path = args.workspace.resolve()
            result = workspace_status(load_workspace(workspace_path), workspace_path)
        elif args.command == "project-status":
            workspace_path = args.workspace.resolve()
            result = computed_project_status(load_workspace(workspace_path), workspace_path)
        elif args.command == "db-prepare":
            result = prepare_database(args.project_root)
        elif args.command in {"starter-validate", "starter-generate", "starter-check"}:
            starter_path = args.starter.resolve()
            starter = load_starter(starter_path)
            if args.command == "starter-validate":
                require_valid_starter(starter, starter_path)
                result = {"valid": True, "projectId": starter["project"]["id"]}
            elif args.command == "starter-generate":
                result = generate_starter(starter, starter_path, args.output)
            else:
                result = check_starter_repeatable(starter, starter_path)
        else:
            raise AssertionError(f"unsupported command: {args.command}")
    except (DatabaseError, GenerationError, RequirementError, StarterError, WorkspaceError) as error:
        print(json.dumps({"ok": False, "error": str(error)}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 2
    print(json.dumps({"ok": True, **result}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
