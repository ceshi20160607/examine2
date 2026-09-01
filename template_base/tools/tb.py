from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from template_base.architecture import ArchitectureError, validate_architecture
from template_base.database import DatabaseError, prepare_database
from template_base.generator import GenerationError
from template_base.requirements import (
    RequirementError,
    build_analysis_plan,
    build_intake,
    consolidate_analysis_fragments,
    load_analysis,
    collect_analysis_fragments,
    extract_literal_fragments,
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
from template_base.tasks import TaskCatalogError, generate_task_catalog, validate_task_catalog
from template_base.rules import RuleValidationError, validate_artifact
from template_base.scheduling import ScheduleError, generate_task_graph, validate_task_graph
from template_base.use_cases import UseCaseError, validate_use_case_catalog
from template_base.workspace import WorkspaceError, computed_project_status, load_workspace, workspace_status


def parser() -> argparse.ArgumentParser:
    root = argparse.ArgumentParser(prog="tb", description="Template Base deterministic project tooling")
    commands = root.add_subparsers(dest="command", required=True)

    artifact_validate = commands.add_parser("artifact-validate", help="validate one project control artifact with a Template Base schema")
    artifact_validate.add_argument("--artifact", required=True, type=Path)
    artifact_validate.add_argument("--rule", required=True)

    architecture_validate = commands.add_parser("architecture-validate", help="validate architecture source binding and exclusive requirement ownership")
    architecture_validate.add_argument("--design", required=True, type=Path)

    use_cases_validate = commands.add_parser("use-cases-validate", help="validate use-case source binding, ownership and full requirement coverage")
    use_cases_validate.add_argument("--catalog", required=True, type=Path)

    tasks_validate = commands.add_parser("tasks-validate", help="validate atomic task ownership and complete implementation/verification coverage")
    tasks_validate.add_argument("--catalog", required=True, type=Path)

    tasks_generate = commands.add_parser("tasks-generate", help="expand a reviewed capability layer plan into deterministic atomic tasks")
    tasks_generate.add_argument("--plan", required=True, type=Path)
    tasks_generate.add_argument("--project-root", required=True, type=Path)
    tasks_generate.add_argument("--output", required=True, type=Path)

    task_graph_validate = commands.add_parser("task-graph-validate", help="validate assessed estimates, dependencies, phases and four-hour cycles")
    task_graph_validate.add_argument("--graph", required=True, type=Path)

    schedule_generate = commands.add_parser("schedule-generate", help="generate an assessed task graph and four-hour cycles")
    schedule_generate.add_argument("--plan", required=True, type=Path)
    schedule_generate.add_argument("--output", required=True, type=Path)

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

    literal_extract = commands.add_parser("requirements-extract-literals", help="create lossless unreviewed candidates for every analysis packet")
    literal_extract.add_argument("--plan", required=True, type=Path)
    literal_extract.add_argument("--output-directory", required=True, type=Path)

    collect = commands.add_parser("requirements-collect", help="validate and index exactly one analysis result for every work packet")
    collect.add_argument("--plan", required=True, type=Path)
    collect.add_argument("--fragment", required=True, action="append", type=Path)
    collect.add_argument("--output", required=True, type=Path)

    consolidate = commands.add_parser("requirements-consolidate", help="merge semantically aliased candidates into final atomic requirements")
    consolidate.add_argument("--fragment-index", required=True, type=Path)
    consolidate.add_argument("--aliases", required=True, type=Path)
    consolidate.add_argument("--output", required=True, type=Path)

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
        if args.command == "artifact-validate":
            result = validate_artifact(args.artifact, args.rule)
        elif args.command == "architecture-validate":
            result = validate_architecture(args.design)
        elif args.command == "use-cases-validate":
            result = validate_use_case_catalog(args.catalog)
        elif args.command == "tasks-validate":
            result = validate_task_catalog(args.catalog)
        elif args.command == "tasks-generate":
            result = generate_task_catalog(args.plan, args.output, args.project_root)
        elif args.command == "task-graph-validate":
            result = validate_task_graph(args.graph)
        elif args.command == "schedule-generate":
            result = generate_task_graph(args.plan, args.output)
        elif args.command == "intake":
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
        elif args.command == "requirements-extract-literals":
            result = extract_literal_fragments(args.plan, args.output_directory)
        elif args.command == "requirements-collect":
            result = collect_analysis_fragments(args.plan, args.fragment, args.output)
        elif args.command == "requirements-consolidate":
            result = consolidate_analysis_fragments(args.fragment_index, args.aliases, args.output)
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
    except (ArchitectureError, DatabaseError, GenerationError, RequirementError, RuleValidationError, ScheduleError, StarterError, TaskCatalogError, UseCaseError, WorkspaceError) as error:
        print(json.dumps({"ok": False, "error": str(error)}, ensure_ascii=False, indent=2), file=sys.stderr)
        return 2
    print(json.dumps({"ok": True, **result}, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
