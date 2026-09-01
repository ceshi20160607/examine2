from __future__ import annotations

import json
import os
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from template_base.architecture import validate_architecture
from template_base.common import canonical_json, sha256_bytes
from template_base.rules import validate_rule
from template_base.use_cases import validate_use_case_catalog


class TaskCatalogError(ValueError):
    pass


_ROLE_BY_KIND = {
    "design": "architecture",
    "database": "database",
    "base-generation": "database",
    "backend-manage": "backend",
    "frontend": "frontend",
    "integration-test": "test",
    "operations": "operations",
}

_TITLE_BY_KIND = {
    "design": "固化{title}工程控制产物",
    "base-generation": "执行{title}所需的全项目 Base 生成",
    "backend-manage": "实现{title}后端业务编排",
    "frontend": "实现{title}真实页面与交互",
    "operations": "实现{title}运维自动化",
    "integration-test": "按真实入口验收{title}",
}


def _read_json_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise TaskCatalogError(f"cannot read {label} {path}: {error}") from error
    if not isinstance(value, dict):
        raise TaskCatalogError(f"{label} root must be an object: {path}")
    return value


def _resolve_bound_source(catalog_path: Path, path_value: str, expected_hash: str, label: str) -> Path:
    source_path = (catalog_path.parent / path_value).resolve()
    try:
        actual_hash = sha256_bytes(source_path.read_bytes())
    except OSError as error:
        raise TaskCatalogError(f"cannot read {label} {source_path}: {error}") from error
    if actual_hash != expected_hash:
        raise TaskCatalogError(f"{label} hash mismatch: declared {expected_hash}, actual {actual_hash}")
    return source_path


def validate_task_catalog(path: Path) -> dict[str, object]:
    catalog_path = path.resolve()
    catalog = _read_json_object(catalog_path, "task catalog")
    issues = validate_rule(catalog, "task-catalog.schema.json")
    if issues:
        raise TaskCatalogError("\n".join(str(issue) for issue in issues))

    source = catalog["source"]
    architecture_path = _resolve_bound_source(
        catalog_path, source["architecturePath"], source["architectureSha256"], "architecture"
    )
    use_case_path = _resolve_bound_source(
        catalog_path, source["useCaseCatalogPath"], source["useCaseCatalogSha256"], "use-case catalog"
    )
    validate_architecture(architecture_path)
    validate_use_case_catalog(use_case_path)
    architecture = _read_json_object(architecture_path, "architecture")
    use_case_catalog = _read_json_object(use_case_path, "use-case catalog")

    capability_ids = {item["id"] for item in architecture["capabilities"]}
    requirement_owner = {
        requirement_id: capability["id"]
        for capability in architecture["capabilities"]
        for requirement_id in capability["requirementIds"]
    }
    use_cases = {item["id"]: item for item in use_case_catalog["useCases"]}

    task_ids = [item["id"] for item in catalog["tasks"]]
    duplicates = sorted(task_id for task_id, count in Counter(task_ids).items() if count > 1)
    if duplicates:
        raise TaskCatalogError(f"duplicate task IDs: {', '.join(duplicates)}")

    requirement_coverage: set[str] = set()
    use_case_kinds: dict[str, set[str]] = defaultdict(set)
    for task in catalog["tasks"]:
        task_id = task["id"]
        capability_id = task["capabilityId"]
        if capability_id not in capability_ids:
            raise TaskCatalogError(f"{task_id} references unknown capability {capability_id}")
        unknown_use_cases = sorted(set(task["useCaseIds"]) - use_cases.keys())
        if unknown_use_cases:
            raise TaskCatalogError(
                f"{task_id} references unknown use cases: {', '.join(unknown_use_cases)}"
            )
        wrong_use_cases = sorted(
            use_case_id
            for use_case_id in task["useCaseIds"]
            if use_cases[use_case_id]["capabilityId"] != capability_id
        )
        if wrong_use_cases:
            raise TaskCatalogError(
                f"{task_id} crosses capability ownership through use cases: {', '.join(wrong_use_cases)}"
            )
        linked_requirements = {
            requirement_id
            for use_case_id in task["useCaseIds"]
            for requirement_id in use_cases[use_case_id]["requirementIds"]
        }
        unknown_requirements = sorted(set(task["requirementIds"]) - linked_requirements)
        if unknown_requirements:
            raise TaskCatalogError(
                f"{task_id} requirements are not supplied by its use cases: {', '.join(unknown_requirements)}"
            )
        wrong_requirements = sorted(
            requirement_id
            for requirement_id in task["requirementIds"]
            if requirement_owner.get(requirement_id) != capability_id
        )
        if wrong_requirements:
            raise TaskCatalogError(
                f"{task_id} crosses exclusive requirement ownership: {', '.join(wrong_requirements)}"
            )
        requirement_coverage.update(task["requirementIds"])
        for use_case_id in task["useCaseIds"]:
            use_case_kinds[use_case_id].add(task["kind"])

    missing_requirements = sorted(requirement_owner.keys() - requirement_coverage)
    if missing_requirements:
        raise TaskCatalogError("requirements lack task coverage: " + ", ".join(missing_requirements))
    missing_implementation = sorted(
        use_case_id
        for use_case_id in use_cases
        if not (use_case_kinds[use_case_id] - {"integration-test"})
    )
    if missing_implementation:
        raise TaskCatalogError(
            "use cases lack implementation tasks: " + ", ".join(missing_implementation)
        )
    missing_verification = sorted(
        use_case_id for use_case_id in use_cases if "integration-test" not in use_case_kinds[use_case_id]
    )
    if missing_verification:
        raise TaskCatalogError(
            "use cases lack integration-test tasks: " + ", ".join(missing_verification)
        )

    counts = Counter(task["kind"] for task in catalog["tasks"])
    return {
        "valid": True,
        "catalog": catalog_path.as_posix(),
        "taskCount": len(catalog["tasks"]),
        "requirementCount": len(requirement_coverage),
        "useCaseCount": len(use_case_kinds),
        "tasksByKind": dict(sorted(counts.items())),
    }


def _workspace_prefix(project_root: Path) -> str:
    value = project_root.as_posix().rstrip("/")
    if not value or value == ".":
        raise TaskCatalogError("project root must be a non-blank workspace-relative path")
    if project_root.is_absolute() or ".." in project_root.parts:
        raise TaskCatalogError("project root must be workspace-relative and cannot contain '..'")
    return value


def _artifact_reference(from_directory: Path, target: Path) -> str:
    return Path(os.path.relpath(target, from_directory)).as_posix()


def _task_paths(kind: str, project_prefix: str) -> tuple[list[str], list[str]]:
    if kind == "design":
        return ([f"{project_prefix}/ai"], [f"{project_prefix}/ai"])
    if kind == "database":
        return ([f"{project_prefix}/db/init"], [f"{project_prefix}/db/init"])
    if kind == "base-generation":
        return (
            [f"{project_prefix}/db/init", f"{project_prefix}/tools/base-codegen.json"],
            [f"{project_prefix}/backend/src/main/java", f"{project_prefix}/backend/src/main/resources/mapper"],
        )
    if kind == "backend-manage":
        return ([f"{project_prefix}/backend"], [f"{project_prefix}/backend"])
    if kind == "frontend":
        return ([f"{project_prefix}/frontend"], [f"{project_prefix}/frontend"])
    if kind == "operations":
        return (
            [f"{project_prefix}/deploy", f"{project_prefix}/backend"],
            [f"{project_prefix}/deploy", f"{project_prefix}/backend"],
        )
    if kind == "integration-test":
        return (
            [f"{project_prefix}/backend/src/test", f"{project_prefix}/frontend/src", f"{project_prefix}/ai/evidence"],
            [f"{project_prefix}/ai/evidence"],
        )
    raise TaskCatalogError(f"unsupported task kind: {kind}")


def _implementation_acceptance(kind: str, use_case: dict[str, Any]) -> list[str]:
    if kind == "design":
        return [
            *(step["visibleResult"] for step in use_case["steps"]),
            *use_case["persistentResults"],
            *use_case["failureResults"],
        ]
    if kind == "base-generation":
        return [
            "完整 init.sql 已在真实 MySQL 执行成功。",
            "一次生成全部固定能力及通用运行模型物理表的 Entity、Mapper、XML 和基础 Service。",
            "重新生成不覆盖 manage 业务代码，前端不直接调用生成的 Base controller。",
        ]
    if kind == "backend-manage":
        return [
            *use_case["persistentResults"],
            *use_case["authorizationResults"],
            *use_case["failureResults"],
            "业务判断位于 manage/application 层，不直接写 SQL，也不修改生成的 Base。",
        ]
    if kind == "frontend":
        return [
            *(step["visibleResult"] for step in use_case["steps"]),
            *use_case["authorizationResults"],
            *use_case["failureResults"],
            "页面调用 manage API，提交成功后读回真实业务状态。",
        ]
    if kind == "operations":
        return [
            *(step["visibleResult"] for step in use_case["steps"]),
            *use_case["persistentResults"],
            *use_case["failureResults"],
        ]
    if kind == "integration-test":
        return [
            *(f"执行步骤 {step['order']}“{step['action']}”后验证：{step['visibleResult']}" for step in use_case["steps"]),
            *(f"持久化读回：{result}" for result in use_case["persistentResults"]),
            *(f"授权边界：{result}" for result in use_case["authorizationResults"]),
            *(f"失败分支：{result}" for result in use_case["failureResults"]),
            "证据来自真实入口和真实依赖，不以 mock、编译成功或 HTTP 200 代替。",
        ]
    raise TaskCatalogError(f"unsupported implementation task kind: {kind}")


def generate_task_catalog(plan_path: Path, output_path: Path, project_root: Path) -> dict[str, object]:
    layering_path = plan_path.resolve()
    layering = _read_json_object(layering_path, "task layering plan")
    issues = validate_rule(layering, "task-layering.schema.json")
    if issues:
        raise TaskCatalogError("\n".join(str(issue) for issue in issues))
    source = layering["source"]
    architecture_path = _resolve_bound_source(
        layering_path, source["architecturePath"], source["architectureSha256"], "architecture"
    )
    use_case_path = _resolve_bound_source(
        layering_path, source["useCaseCatalogPath"], source["useCaseCatalogSha256"], "use-case catalog"
    )
    validate_architecture(architecture_path)
    validate_use_case_catalog(use_case_path)
    architecture = _read_json_object(architecture_path, "architecture")
    use_case_catalog = _read_json_object(use_case_path, "use-case catalog")
    capabilities = {item["id"]: item for item in architecture["capabilities"]}
    use_cases = {item["id"]: item for item in use_case_catalog["useCases"]}
    use_cases_by_capability: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for use_case in use_case_catalog["useCases"]:
        use_cases_by_capability[use_case["capabilityId"]].append(use_case)

    plans = layering["capabilityPlans"]
    plan_ids = [item["capabilityId"] for item in plans]
    duplicate_plans = sorted(item for item, count in Counter(plan_ids).items() if count > 1)
    if duplicate_plans:
        raise TaskCatalogError(f"duplicate capability layering plans: {', '.join(duplicate_plans)}")
    missing_plans = sorted(capabilities.keys() - set(plan_ids))
    unknown_plans = sorted(set(plan_ids) - capabilities.keys())
    if missing_plans or unknown_plans:
        raise TaskCatalogError(
            f"capability layering plans must exactly match architecture; missing={missing_plans}, unknown={unknown_plans}"
        )

    project_prefix = _workspace_prefix(project_root)
    tasks: list[dict[str, Any]] = []

    def append_task(
        *,
        title: str,
        capability_id: str,
        kind: str,
        requirement_ids: list[str],
        use_case_ids: list[str],
        outcome: str,
        acceptance: list[str],
    ) -> None:
        task_id = f"TASK-{len(tasks) + 1:04d}"
        resources, outputs = _task_paths(kind, project_prefix)
        tasks.append({
            "id": task_id,
            "title": title,
            "capabilityId": capability_id,
            "kind": kind,
            "role": _ROLE_BY_KIND[kind],
            "requirementIds": requirement_ids,
            "useCaseIds": use_case_ids,
            "outcome": outcome,
            "inputs": [
                f"{project_prefix}/ai/architecture/design.json",
                f"{project_prefix}/ai/requirements/use-cases.json",
            ],
            "outputs": outputs,
            "resources": resources,
            "acceptance": acceptance,
        })

    plan_by_capability = {item["capabilityId"]: item for item in plans}
    for capability in architecture["capabilities"]:
        capability_id = capability["id"]
        capability_plan = plan_by_capability[capability_id]
        bootstrap_kinds = capability_plan["bootstrapKinds"]
        if "base-generation" in bootstrap_kinds and "database" not in bootstrap_kinds:
            raise TaskCatalogError(f"{capability_id} base-generation requires a database bootstrap task")
        if capability["storageModel"] == "none" and bootstrap_kinds:
            raise TaskCatalogError(f"{capability_id} has storageModel none and cannot have bootstrap tasks")
        capability_use_cases = use_cases_by_capability[capability_id]
        capability_use_case_ids = [item["id"] for item in capability_use_cases]
        capability_requirement_ids = capability["requirementIds"]
        for kind in bootstrap_kinds:
            if kind == "database":
                append_task(
                    title=f"定义{capability['name']}完整初始表结构",
                    capability_id=capability_id,
                    kind=kind,
                    requirement_ids=capability_requirement_ids,
                    use_case_ids=capability_use_case_ids,
                    outcome=f"db/init 覆盖{capability['name']}全部主从、版本、关系、日志、索引和约束表。",
                    acceptance=[*capability["transactionBoundaries"], *capability["dataBoundaries"], "SQL 先进入 db/init，组合后由 init.sql 和 final.sql 精确表达当前初始结构。"],
                )
            else:
                append_task(
                    title=f"从{capability['name']}表生成完整 Base",
                    capability_id=capability_id,
                    kind=kind,
                    requirement_ids=capability_requirement_ids,
                    use_case_ids=capability_use_case_ids,
                    outcome=f"真实 MySQL 中的{capability['name']}表全部生成 Entity、Mapper、XML 和基础 Service。",
                    acceptance=["生成清单覆盖该能力全部物理表。", "重新生成不覆盖 manage 业务代码。", "前端不直接调用生成的 Base controller。"],
                )

        overrides = capability_plan["useCaseOverrides"]
        override_ids = [item["useCaseId"] for item in overrides]
        duplicate_overrides = sorted(item for item, count in Counter(override_ids).items() if count > 1)
        if duplicate_overrides:
            raise TaskCatalogError(f"{capability_id} duplicate use-case overrides: {', '.join(duplicate_overrides)}")
        invalid_overrides = sorted(
            use_case_id
            for use_case_id in override_ids
            if use_case_id not in use_cases or use_cases[use_case_id]["capabilityId"] != capability_id
        )
        if invalid_overrides:
            raise TaskCatalogError(f"{capability_id} invalid use-case overrides: {', '.join(invalid_overrides)}")
        override_by_id = {item["useCaseId"]: item["implementationKinds"] for item in overrides}
        for use_case in capability_use_cases:
            implementation_kinds = override_by_id.get(
                use_case["id"], capability_plan["defaultImplementationKinds"]
            )
            for kind in [*implementation_kinds, "integration-test"]:
                append_task(
                    title=_TITLE_BY_KIND[kind].format(title=use_case["title"]),
                    capability_id=capability_id,
                    kind=kind,
                    requirement_ids=use_case["requirementIds"],
                    use_case_ids=[use_case["id"]],
                    outcome=(
                        f"{use_case['title']}按定义入口完成并产生可见、可读回的业务结果。"
                        if kind != "integration-test"
                        else f"从{use_case['entry']}完整执行{use_case['title']}并保存周期验收证据。"
                    ),
                    acceptance=_implementation_acceptance(kind, use_case),
                )

    catalog_output = output_path.resolve()
    artifact = {
        "schemaVersion": 1,
        "source": {
            "architecturePath": _artifact_reference(catalog_output.parent, architecture_path),
            "architectureSha256": sha256_bytes(architecture_path.read_bytes()),
            "useCaseCatalogPath": _artifact_reference(catalog_output.parent, use_case_path),
            "useCaseCatalogSha256": sha256_bytes(use_case_path.read_bytes()),
        },
        "tasks": tasks,
    }
    catalog_output.parent.mkdir(parents=True, exist_ok=True)
    catalog_output.write_text(canonical_json(artifact), encoding="utf-8")
    summary = validate_task_catalog(catalog_output)
    return {
        **summary,
        "layeringPlan": layering_path.as_posix(),
        "output": catalog_output.as_posix(),
    }
