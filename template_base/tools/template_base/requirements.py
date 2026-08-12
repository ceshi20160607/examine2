from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

from .contract import canonical_json, load_contract, require_generation_ready, sha256_bytes


HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*$")
LIST_ITEM = re.compile(r"^\s*(?:[-+*]|\d+[.)])\s+\S")
TABLE_SEPARATOR = re.compile(r"^\s*\|?(?:\s*:?-{3,}:?\s*\|)+\s*:?-{3,}:?\s*\|?\s*$")
REQUIREMENT_ID = re.compile(r"^REQ-[A-Z0-9][A-Z0-9-]*$")
DECISION_ID = re.compile(r"^DEC-[A-Z0-9][A-Z0-9-]*$")
BLOCK_ID = re.compile(r"^B\d{4,}$")


class RequirementError(ValueError):
    pass


def _strict_keys(
    value: dict[str, Any],
    path: str,
    required: set[str],
    allowed: set[str],
    errors: list[str],
) -> None:
    for key in sorted(required - value.keys()):
        errors.append(f"{path}.{key} is required")
    for key in sorted(value.keys() - allowed):
        errors.append(f"{path}.{key} is not allowed")


def _relative_path(target: Path, parent: Path) -> str:
    try:
        return Path(os.path.relpath(target, parent)).as_posix()
    except ValueError:
        return target.as_posix()


def _preview(lines: list[str]) -> str:
    compact = " ".join(line.strip() for line in lines if line.strip())
    return compact if len(compact) <= 180 else compact[:177] + "..."


def _block(kind: str, lines: list[str], start: int, end: int, headings: list[str]) -> dict[str, Any]:
    content = "\n".join(lines).rstrip() + "\n"
    return {
        "kind": kind,
        "headingPath": list(headings),
        "lineStart": start,
        "lineEnd": end,
        "sha256": sha256_bytes(content.encode("utf-8")),
        "preview": _preview(lines),
    }


def extract_markdown_blocks(content: str) -> list[dict[str, Any]]:
    lines = content.splitlines()
    blocks: list[dict[str, Any]] = []
    headings: list[str] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if not line.strip():
            index += 1
            continue

        heading = HEADING.match(line)
        if heading:
            level = len(heading.group(1))
            title = heading.group(2).strip()
            headings = headings[: level - 1]
            headings.append(title)
            blocks.append(_block("heading", [line], index + 1, index + 1, headings))
            index += 1
            continue

        if line.lstrip().startswith("```"):
            start = index
            fence = line.lstrip()[:3]
            index += 1
            while index < len(lines):
                current = lines[index]
                index += 1
                if current.lstrip().startswith(fence):
                    break
            blocks.append(_block("code", lines[start:index], start + 1, index, headings))
            continue

        if LIST_ITEM.match(line):
            start = index
            index += 1
            while index < len(lines):
                current = lines[index]
                if not current.strip() or HEADING.match(current) or current.lstrip().startswith("```"):
                    break
                index += 1
            blocks.append(_block("list", lines[start:index], start + 1, index, headings))
            continue

        if "|" in line and index + 1 < len(lines) and TABLE_SEPARATOR.match(lines[index + 1]):
            start = index
            index += 2
            while index < len(lines) and lines[index].strip() and "|" in lines[index]:
                index += 1
            blocks.append(_block("table", lines[start:index], start + 1, index, headings))
            continue

        start = index
        index += 1
        while index < len(lines):
            current = lines[index]
            if (
                not current.strip()
                or HEADING.match(current)
                or LIST_ITEM.match(current)
                or current.lstrip().startswith("```")
            ):
                break
            index += 1
        blocks.append(_block("paragraph", lines[start:index], start + 1, index, headings))

    for number, block in enumerate(blocks, start=1):
        block["id"] = f"B{number:04d}"
    return blocks


def build_intake(requirements_path: Path, output_path: Path) -> dict[str, Any]:
    requirements_path = requirements_path.resolve()
    output_path = output_path.resolve()
    if not requirements_path.is_file():
        raise RequirementError(f"requirements source does not exist: {requirements_path}")
    raw = requirements_path.read_bytes()
    try:
        content = raw.decode("utf-8-sig")
    except UnicodeDecodeError as error:
        raise RequirementError("requirements source must be UTF-8") from error
    blocks = extract_markdown_blocks(content)
    if not blocks:
        raise RequirementError("requirements source has no content blocks")
    intake = {
        "schemaVersion": 1,
        "source": {
            "path": _relative_path(requirements_path, output_path.parent),
            "sha256": sha256_bytes(raw),
            "lineCount": len(content.splitlines()),
        },
        "blockCount": len(blocks),
        "blocks": blocks,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(canonical_json(intake), encoding="utf-8")
    return {"output": str(output_path), "sourceSha256": intake["source"]["sha256"], "blockCount": len(blocks)}


def load_intake(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read requirement intake: {error}") from error
    if not isinstance(value, dict):
        raise RequirementError("requirement intake must be an object")
    return value


def require_current_intake(intake: dict[str, Any], intake_path: Path) -> Path:
    if intake.get("schemaVersion") != 1:
        raise RequirementError("intake schemaVersion must equal 1")
    source = intake.get("source")
    if not isinstance(source, dict) or not isinstance(source.get("path"), str):
        raise RequirementError("intake source is invalid")
    source_path = (intake_path.parent / source["path"]).resolve()
    if not source_path.is_file():
        raise RequirementError(f"intake source does not exist: {source_path}")
    raw = source_path.read_bytes()
    if sha256_bytes(raw) != source.get("sha256"):
        raise RequirementError("requirement source changed; rebuild the intake before analysis")
    content = raw.decode("utf-8-sig")
    expected_blocks = extract_markdown_blocks(content)
    if intake.get("blocks") != expected_blocks or intake.get("blockCount") != len(expected_blocks):
        raise RequirementError("intake block index is stale or was edited")
    return source_path


def read_source_blocks(intake_path: Path, requested_ids: list[str]) -> dict[str, Any]:
    intake_path = intake_path.resolve()
    intake = load_intake(intake_path)
    source_path = require_current_intake(intake, intake_path)
    if not requested_ids:
        raise RequirementError("at least one --block is required")
    by_id = {block["id"]: block for block in intake["blocks"]}
    unknown = [block_id for block_id in requested_ids if block_id not in by_id]
    if unknown:
        raise RequirementError(f"unknown source blocks: {unknown}")
    lines = source_path.read_text(encoding="utf-8-sig").splitlines()
    selected = []
    for block_id in requested_ids:
        block = by_id[block_id]
        selected.append({
            "id": block_id,
            "kind": block["kind"],
            "headingPath": block["headingPath"],
            "lineStart": block["lineStart"],
            "lineEnd": block["lineEnd"],
            "text": "\n".join(lines[block["lineStart"] - 1:block["lineEnd"]]),
        })
    return {"source": str(source_path), "blocks": selected}


def load_scope(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read requirement scope: {error}") from error
    if not isinstance(value, dict):
        raise RequirementError("requirement scope must be an object")
    return value


def resolve_scope(scope_path: Path) -> tuple[dict[str, Any], Path, dict[str, Any], list[dict[str, Any]], list[dict[str, Any]]]:
    scope_path = scope_path.resolve()
    scope = load_scope(scope_path)
    errors: list[str] = []
    _strict_keys(
        scope,
        "$",
        {"schemaVersion", "id", "intake", "selectors", "deferredRationale"},
        {"schemaVersion", "id", "intake", "selectors", "deferredRationale"},
        errors,
    )
    if scope.get("schemaVersion") != 1:
        errors.append("scope schemaVersion must equal 1")
    if not isinstance(scope.get("id"), str) or not scope["id"].strip():
        errors.append("scope id is required")
    if not isinstance(scope.get("deferredRationale"), str) or not scope["deferredRationale"].strip():
        errors.append("scope deferredRationale is required")

    intake_info = scope.get("intake")
    if not isinstance(intake_info, dict):
        raise RequirementError("scope intake must be an object")
    _strict_keys(
        intake_info,
        "intake",
        {"path", "sha256", "requirementsSha256"},
        {"path", "sha256", "requirementsSha256"},
        errors,
    )
    intake_relative = intake_info.get("path")
    if not isinstance(intake_relative, str) or not intake_relative:
        raise RequirementError("scope intake.path is required")
    intake_path = (scope_path.parent / intake_relative).resolve()
    intake = load_intake(intake_path)
    require_current_intake(intake, intake_path)
    if intake_info.get("sha256") != sha256_bytes(intake_path.read_bytes()):
        errors.append("scope is stale for the current intake")
    if intake_info.get("requirementsSha256") != intake.get("source", {}).get("sha256"):
        errors.append("scope is stale for the requirement source")

    selectors = scope.get("selectors")
    if not isinstance(selectors, dict):
        raise RequirementError("scope selectors must be an object")
    _strict_keys(
        selectors,
        "selectors",
        {"includeHeadingPaths", "includeBlocks", "excludeBlocks"},
        {"includeHeadingPaths", "includeBlocks", "excludeBlocks"},
        errors,
    )
    heading_paths = selectors.get("includeHeadingPaths")
    if not isinstance(heading_paths, list):
        errors.append("selectors.includeHeadingPaths must be an array")
        heading_paths = []
    normalized_prefixes: list[tuple[str, ...]] = []
    for index, raw_prefix in enumerate(heading_paths):
        if not isinstance(raw_prefix, list) or not raw_prefix or any(not isinstance(item, str) or not item.strip() for item in raw_prefix):
            errors.append(f"selectors.includeHeadingPaths[{index}] must be a non-empty string array")
            continue
        normalized_prefixes.append(tuple(raw_prefix))
    if len(normalized_prefixes) != len(set(normalized_prefixes)):
        errors.append("selectors.includeHeadingPaths must not contain duplicates")

    all_coverable = [block for block in intake["blocks"] if block["kind"] != "heading"]
    known_ids = {block["id"] for block in all_coverable}
    include_blocks = selectors.get("includeBlocks")
    exclude_blocks = selectors.get("excludeBlocks")
    for name, values in (("includeBlocks", include_blocks), ("excludeBlocks", exclude_blocks)):
        if not isinstance(values, list):
            errors.append(f"selectors.{name} must be an array")
            continue
        if any(not isinstance(value, str) or value not in known_ids for value in values):
            errors.append(f"selectors.{name} contains an unknown or heading block")
        if len(values) != len(set(values)):
            errors.append(f"selectors.{name} must not contain duplicates")
    include_set = set(include_blocks) if isinstance(include_blocks, list) else set()
    exclude_set = set(exclude_blocks) if isinstance(exclude_blocks, list) else set()
    if include_set & exclude_set:
        errors.append("selectors includeBlocks and excludeBlocks overlap")

    prefix_hits = {prefix: 0 for prefix in normalized_prefixes}
    selected: list[dict[str, Any]] = []
    deferred: list[dict[str, Any]] = []
    for block in all_coverable:
        heading = tuple(block["headingPath"])
        matched = False
        for prefix in normalized_prefixes:
            if heading[: len(prefix)] == prefix:
                prefix_hits[prefix] += 1
                matched = True
        included = (matched or block["id"] in include_set) and block["id"] not in exclude_set
        (selected if included else deferred).append(block)
    missing_prefixes = [list(prefix) for prefix, count in prefix_hits.items() if count == 0]
    if missing_prefixes:
        errors.append(f"scope heading selectors match no blocks: {missing_prefixes}")
    if not selected:
        errors.append("scope selects no requirement blocks")
    if errors:
        raise RequirementError("\n".join(errors))
    return scope, intake_path, intake, selected, deferred


def build_analysis_plan(
    intake_path: Path,
    output_path: Path,
    max_blocks: int = 12,
    max_lines: int = 200,
    scope_path: Path | None = None,
) -> dict[str, Any]:
    intake_path = intake_path.resolve()
    output_path = output_path.resolve()
    if max_blocks < 1 or max_lines < 1:
        raise RequirementError("max-blocks and max-lines must be positive")
    intake = load_intake(intake_path)
    require_current_intake(intake, intake_path)
    all_coverable = [block for block in intake["blocks"] if block["kind"] != "heading"]
    scope_info: dict[str, Any] | None = None
    deferred_count = 0
    if scope_path is None:
        coverable = all_coverable
    else:
        resolved_scope_path = scope_path.resolve()
        scope, scoped_intake_path, scoped_intake, coverable, deferred = resolve_scope(resolved_scope_path)
        if scoped_intake_path != intake_path or scoped_intake != intake:
            raise RequirementError("scope references another requirement intake")
        deferred_count = len(deferred)
        scope_info = {
            "path": _relative_path(resolved_scope_path, output_path.parent),
            "sha256": sha256_bytes(resolved_scope_path.read_bytes()),
            "id": scope["id"],
            "selectedBlockCount": len(coverable),
            "deferredBlockCount": deferred_count,
        }
    packets: list[dict[str, Any]] = []
    current: list[dict[str, Any]] = []
    current_key: tuple[str, ...] | None = None

    def flush() -> None:
        nonlocal current
        if not current:
            return
        packets.append({
            "id": f"RWP-{len(packets) + 1:04d}",
            "headingPath": current[0]["headingPath"],
            "blockIds": [block["id"] for block in current],
            "lineStart": current[0]["lineStart"],
            "lineEnd": current[-1]["lineEnd"],
        })
        current = []

    for block in coverable:
        heading_key = tuple(block["headingPath"][:2])
        proposed_lines = block["lineEnd"] - (current[0]["lineStart"] if current else block["lineStart"]) + 1
        if current and (
            heading_key != current_key
            or len(current) >= max_blocks
            or proposed_lines > max_lines
        ):
            flush()
        if not current:
            current_key = heading_key
        current.append(block)
    flush()

    plan = {
        "schemaVersion": 2 if scope_info is not None else 1,
        "intake": {
            "path": _relative_path(intake_path, output_path.parent),
            "sha256": sha256_bytes(intake_path.read_bytes()),
            "requirementsSha256": intake["source"]["sha256"],
        },
        "packetCount": len(packets),
        "packets": packets,
    }
    if scope_info is not None:
        plan["scope"] = scope_info
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(canonical_json(plan), encoding="utf-8")
    return {
        "output": str(output_path),
        "packetCount": len(packets),
        "blockCount": len(coverable),
        "deferredBlockCount": deferred_count,
    }


def _load_current_plan(plan_path: Path) -> tuple[dict[str, Any], Path, dict[str, Any]]:
    try:
        plan = json.loads(plan_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read analysis plan: {error}") from error
    if not isinstance(plan, dict) or plan.get("schemaVersion") not in {1, 2}:
        raise RequirementError("analysis plan schemaVersion must equal 1 or 2")
    intake_info = plan.get("intake")
    if not isinstance(intake_info, dict) or not isinstance(intake_info.get("path"), str):
        raise RequirementError("analysis plan intake is invalid")
    intake_path = (plan_path.parent / intake_info["path"]).resolve()
    intake = load_intake(intake_path)
    require_current_intake(intake, intake_path)
    if sha256_bytes(intake_path.read_bytes()) != intake_info.get("sha256"):
        raise RequirementError("analysis plan is stale for the current intake")
    if intake["source"]["sha256"] != intake_info.get("requirementsSha256"):
        raise RequirementError("analysis plan is stale for the requirement source")
    packets = plan.get("packets")
    if not isinstance(packets, list) or plan.get("packetCount") != len(packets):
        raise RequirementError("analysis plan packets are invalid")
    planned_blocks: list[str] = []
    packet_ids: set[str] = set()
    for packet in packets:
        if not isinstance(packet, dict) or not isinstance(packet.get("id"), str):
            raise RequirementError("analysis plan contains an invalid packet")
        if packet["id"] in packet_ids:
            raise RequirementError(f"duplicate analysis packet: {packet['id']}")
        packet_ids.add(packet["id"])
        block_ids = packet.get("blockIds")
        if not isinstance(block_ids, list) or not block_ids:
            raise RequirementError(f"analysis packet has no blocks: {packet['id']}")
        planned_blocks.extend(block_ids)
    if plan["schemaVersion"] == 1:
        if "scope" in plan:
            raise RequirementError("version 1 analysis plan cannot contain scope")
        coverable = [block["id"] for block in intake["blocks"] if block["kind"] != "heading"]
    else:
        scope_info = plan.get("scope")
        if not isinstance(scope_info, dict):
            raise RequirementError("version 2 analysis plan requires scope")
        scope_relative = scope_info.get("path")
        if not isinstance(scope_relative, str):
            raise RequirementError("analysis plan scope path is invalid")
        scope_path = (plan_path.parent / scope_relative).resolve()
        if sha256_bytes(scope_path.read_bytes()) != scope_info.get("sha256"):
            raise RequirementError("analysis plan is stale for the requirement scope")
        scope, scoped_intake_path, scoped_intake, selected, deferred = resolve_scope(scope_path)
        if scoped_intake_path != intake_path or scoped_intake != intake:
            raise RequirementError("analysis plan scope references another intake")
        if scope_info.get("id") != scope.get("id"):
            raise RequirementError("analysis plan scope id is stale")
        if scope_info.get("selectedBlockCount") != len(selected) or scope_info.get("deferredBlockCount") != len(deferred):
            raise RequirementError("analysis plan scope counts are stale")
        coverable = [block["id"] for block in selected]
    if planned_blocks != coverable:
        raise RequirementError("analysis plan does not partition every source block exactly once")
    return plan, intake_path, intake


def merge_analysis_fragments(plan_path: Path, fragment_paths: list[Path], output_path: Path) -> dict[str, Any]:
    plan_path = plan_path.resolve()
    output_path = output_path.resolve()
    plan, intake_path, intake = _load_current_plan(plan_path)
    if not fragment_paths:
        raise RequirementError("at least one analysis fragment is required")
    plan_hash = sha256_bytes(plan_path.read_bytes())
    packet_by_id = {packet["id"]: packet for packet in plan["packets"]}
    submitted: set[str] = set()
    requirements: list[dict[str, Any]] = []
    decisions: list[dict[str, Any]] = []
    coverage: list[dict[str, Any]] = []
    for raw_path in fragment_paths:
        fragment_path = raw_path.resolve()
        try:
            fragment = json.loads(fragment_path.read_text(encoding="utf-8-sig"))
        except (OSError, json.JSONDecodeError) as error:
            raise RequirementError(f"cannot read analysis fragment {fragment_path}: {error}") from error
        if not isinstance(fragment, dict) or fragment.get("schemaVersion") != 1:
            raise RequirementError(f"analysis fragment schemaVersion must equal 1: {fragment_path}")
        source = fragment.get("source")
        if not isinstance(source, dict) or source.get("planSha256") != plan_hash:
            raise RequirementError(f"analysis fragment is stale for the plan: {fragment_path}")
        referenced_plan = source.get("planPath")
        if not isinstance(referenced_plan, str) or (fragment_path.parent / referenced_plan).resolve() != plan_path:
            raise RequirementError(f"analysis fragment references another plan: {fragment_path}")
        packet_id = fragment.get("packetId")
        if packet_id not in packet_by_id:
            raise RequirementError(f"analysis fragment has unknown packetId: {packet_id}")
        if packet_id in submitted:
            raise RequirementError(f"analysis packet was submitted more than once: {packet_id}")
        submitted.add(packet_id)
        allowed_blocks = set(packet_by_id[packet_id]["blockIds"])
        for collection_name in ("requirements", "decisions"):
            values = fragment.get(collection_name)
            if not isinstance(values, list):
                raise RequirementError(f"{fragment_path} {collection_name} must be an array")
            for item in values:
                if not isinstance(item, dict) or not set(item.get("sourceBlocks", [])) <= allowed_blocks:
                    raise RequirementError(f"{fragment_path} {collection_name} references blocks outside {packet_id}")
        fragment_coverage = fragment.get("coverage")
        if not isinstance(fragment_coverage, list):
            raise RequirementError(f"{fragment_path} coverage must be an array")
        if {item.get("sourceBlock") for item in fragment_coverage if isinstance(item, dict)} != allowed_blocks:
            raise RequirementError(f"{fragment_path} does not cover exactly the blocks in {packet_id}")
        requirements.extend(fragment["requirements"])
        decisions.extend(fragment["decisions"])
        coverage.extend(fragment_coverage)

    missing_packets = sorted(packet_by_id.keys() - submitted)
    if missing_packets:
        raise RequirementError(f"analysis packets have no fragment: {missing_packets}")
    analysis_source = {
        "intakePath": _relative_path(intake_path, output_path.parent),
        "intakeSha256": sha256_bytes(intake_path.read_bytes()),
        "requirementsSha256": intake["source"]["sha256"],
    }
    if plan["schemaVersion"] == 2:
        scope_path = (plan_path.parent / plan["scope"]["path"]).resolve()
        analysis_source.update({
            "scopePath": _relative_path(scope_path, output_path.parent),
            "scopeSha256": sha256_bytes(scope_path.read_bytes()),
            "scopeId": plan["scope"]["id"],
        })
    analysis = {
        "schemaVersion": plan["schemaVersion"],
        "source": {
            **analysis_source,
        },
        "requirements": requirements,
        "decisions": decisions,
        "coverage": coverage,
        "contractBindings": [],
    }
    summary = validate_analysis(analysis, output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(canonical_json(analysis), encoding="utf-8")
    return {"output": str(output_path), "packetCount": len(submitted), **summary}


def load_analysis(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read requirement analysis: {error}") from error
    if not isinstance(value, dict):
        raise RequirementError("requirement analysis must be an object")
    return value


def validate_analysis(analysis: dict[str, Any], analysis_path: Path) -> dict[str, Any]:
    errors: list[str] = []
    _strict_keys(
        analysis,
        "$",
        {"schemaVersion", "source", "requirements", "decisions", "coverage", "contractBindings"},
        {"schemaVersion", "source", "requirements", "decisions", "coverage", "contractBindings"},
        errors,
    )
    schema_version = analysis.get("schemaVersion")
    if schema_version not in {1, 2}:
        errors.append("schemaVersion must equal 1 or 2")
    source = analysis.get("source")
    if not isinstance(source, dict):
        raise RequirementError("analysis source must be an object")
    base_source_keys = {"intakePath", "intakeSha256", "requirementsSha256"}
    scoped_source_keys = {"scopePath", "scopeSha256", "scopeId"}
    required_source_keys = base_source_keys | scoped_source_keys if schema_version == 2 else base_source_keys
    _strict_keys(source, "source", required_source_keys, required_source_keys, errors)
    intake_relative = source.get("intakePath")
    if not isinstance(intake_relative, str) or not intake_relative:
        raise RequirementError("analysis source.intakePath is required")
    intake_path = (analysis_path.parent / intake_relative).resolve()
    intake = load_intake(intake_path)
    require_current_intake(intake, intake_path)
    if source.get("intakeSha256") != sha256_bytes(intake_path.read_bytes()):
        errors.append("analysis is not bound to the current intake")
    if source.get("requirementsSha256") != intake.get("source", {}).get("sha256"):
        errors.append("analysis is not bound to the current requirements source")

    blocks = intake.get("blocks", [])
    block_ids = {block.get("id") for block in blocks if isinstance(block, dict)}
    all_coverable = {block.get("id") for block in blocks if isinstance(block, dict) and block.get("kind") != "heading"}
    deferred_block_count = 0
    if schema_version == 2:
        scope_relative = source.get("scopePath")
        if not isinstance(scope_relative, str) or not scope_relative:
            raise RequirementError("analysis source.scopePath is required")
        scope_path = (analysis_path.parent / scope_relative).resolve()
        scope, scoped_intake_path, scoped_intake, selected, deferred = resolve_scope(scope_path)
        if source.get("scopeSha256") != sha256_bytes(scope_path.read_bytes()):
            errors.append("analysis is not bound to the current requirement scope")
        if source.get("scopeId") != scope.get("id"):
            errors.append("analysis scope id is stale")
        if scoped_intake_path != intake_path or scoped_intake != intake:
            errors.append("analysis scope references another intake")
        coverable = {block["id"] for block in selected}
        deferred_block_count = len(deferred)
        block_ids = coverable
    else:
        coverable = all_coverable

    requirements = analysis.get("requirements")
    if not isinstance(requirements, list) or not requirements:
        errors.append("requirements must contain at least one atomic requirement")
        requirements = []
    requirement_ids: set[str] = set()
    pending_requirement_ids: set[str] = set()
    requirement_decisions: dict[str, set[str]] = {}
    requirement_states: dict[str, tuple[str, str]] = {}
    for index, item in enumerate(requirements):
        prefix = f"requirements[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{prefix} must be an object")
            continue
        _strict_keys(
            item,
            prefix,
            {"id", "title", "statement", "type", "priority", "origin", "status", "sourceBlocks", "acceptanceCriteria", "decisionIds"},
            {"id", "title", "statement", "type", "priority", "origin", "status", "sourceBlocks", "acceptanceCriteria", "decisionIds"},
            errors,
        )
        requirement_id = item.get("id")
        if not isinstance(requirement_id, str) or not REQUIREMENT_ID.fullmatch(requirement_id):
            errors.append(f"{prefix}.id has invalid format")
            continue
        if requirement_id in requirement_ids:
            errors.append(f"duplicate requirement id: {requirement_id}")
        requirement_ids.add(requirement_id)
        for field in ("title", "statement"):
            if not isinstance(item.get(field), str) or not item[field].strip():
                errors.append(f"{prefix}.{field} is required")
        if item.get("type") not in {"functional", "quality", "constraint", "non-goal"}:
            errors.append(f"{prefix}.type is invalid")
        if item.get("priority") not in {"must", "should", "could"}:
            errors.append(f"{prefix}.priority is invalid")
        if item.get("origin") not in {"explicit", "proposed"}:
            errors.append(f"{prefix}.origin is invalid")
        if item.get("status") not in {"confirmed", "pending"}:
            errors.append(f"{prefix}.status is invalid")
        if item.get("status") == "pending":
            pending_requirement_ids.add(requirement_id)
        requirement_states[requirement_id] = (str(item.get("origin")), str(item.get("status")))
        refs = item.get("sourceBlocks")
        if not isinstance(refs, list) or not refs:
            errors.append(f"{prefix}.sourceBlocks must not be empty")
        elif any(ref not in block_ids for ref in refs):
            errors.append(f"{prefix}.sourceBlocks contains an unknown block")
        elif len(refs) != len(set(refs)):
            errors.append(f"{prefix}.sourceBlocks must not contain duplicates")
        criteria = item.get("acceptanceCriteria")
        if item.get("type") != "non-goal" and (not isinstance(criteria, list) or not criteria or any(not isinstance(value, str) or not value.strip() for value in criteria)):
            errors.append(f"{prefix}.acceptanceCriteria must contain testable statements")
        decision_ids = item.get("decisionIds")
        if not isinstance(decision_ids, list):
            errors.append(f"{prefix}.decisionIds must be an array")
            decision_ids = []
        elif any(not isinstance(value, str) or not DECISION_ID.fullmatch(value) for value in decision_ids):
            errors.append(f"{prefix}.decisionIds contains an invalid id")
        elif len(decision_ids) != len(set(decision_ids)):
            errors.append(f"{prefix}.decisionIds must not contain duplicates")
        requirement_decisions[requirement_id] = set(decision_ids)
        if item.get("origin") == "proposed" and not decision_ids:
            errors.append(f"{prefix} is proposed and must reference a decision")

    decisions = analysis.get("decisions")
    if not isinstance(decisions, list):
        errors.append("decisions must be an array")
        decisions = []
    decision_ids: set[str] = set()
    open_decisions: set[str] = set()
    decision_states: dict[str, tuple[str, str | None]] = {}
    for index, item in enumerate(decisions):
        prefix = f"decisions[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{prefix} must be an object")
            continue
        _strict_keys(
            item,
            prefix,
            {"id", "question", "sourceBlocks", "options", "status", "resolution", "resolvedBy"},
            {"id", "question", "sourceBlocks", "options", "status", "resolution", "resolvedBy"},
            errors,
        )
        decision_id = item.get("id")
        if not isinstance(decision_id, str) or not DECISION_ID.fullmatch(decision_id):
            errors.append(f"{prefix}.id has invalid format")
            continue
        if decision_id in decision_ids:
            errors.append(f"duplicate decision id: {decision_id}")
        decision_ids.add(decision_id)
        if not isinstance(item.get("question"), str) or not item["question"].strip():
            errors.append(f"{prefix}.question is required")
        refs = item.get("sourceBlocks")
        if not isinstance(refs, list) or not refs or any(ref not in block_ids for ref in refs):
            errors.append(f"{prefix}.sourceBlocks must reference known blocks")
        options = item.get("options")
        if not isinstance(options, list) or len(options) < 2:
            errors.append(f"{prefix}.options must contain at least two choices")
        elif any(not isinstance(value, str) or not value.strip() for value in options):
            errors.append(f"{prefix}.options must contain non-blank strings")
        elif len(options) != len(set(options)):
            errors.append(f"{prefix}.options must not contain duplicates")
        status = item.get("status")
        if status == "open":
            open_decisions.add(decision_id)
            if item.get("resolution") is not None or item.get("resolvedBy") is not None:
                errors.append(f"{prefix} is open and cannot have a resolution")
        elif status == "resolved":
            if not isinstance(item.get("resolution"), str) or not item["resolution"].strip():
                errors.append(f"{prefix}.resolution is required")
            if item.get("resolvedBy") not in {"user", "explicit-source"}:
                errors.append(f"{prefix}.resolvedBy must be user or explicit-source")
        else:
            errors.append(f"{prefix}.status is invalid")
        decision_states[decision_id] = (str(status), item.get("resolvedBy"))

    for requirement_id, refs in requirement_decisions.items():
        unknown = refs - decision_ids
        if unknown:
            errors.append(f"{requirement_id} references unknown decisions: {sorted(unknown)}")
        origin, status = requirement_states[requirement_id]
        if status == "confirmed" and any(decision_states.get(ref, ("open", None))[0] != "resolved" for ref in refs):
            errors.append(f"{requirement_id} is confirmed but still references an open decision")
        if origin == "proposed" and status == "confirmed" and any(
            decision_states.get(ref, ("open", None))[1] != "user" for ref in refs
        ):
            errors.append(f"{requirement_id} was proposed by an Agent and requires a user-resolved decision")

    dispositions = analysis.get("coverage")
    if not isinstance(dispositions, list):
        errors.append("coverage must be an array")
        dispositions = []
    coverage_by_block: dict[str, int] = {}
    covered_requirement_ids: set[str] = set()
    for index, item in enumerate(dispositions):
        prefix = f"coverage[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{prefix} must be an object")
            continue
        _strict_keys(
            item,
            prefix,
            {"sourceBlock", "classification", "requirementIds", "rationale"},
            {"sourceBlock", "classification", "requirementIds", "rationale"},
            errors,
        )
        block_id = item.get("sourceBlock")
        if block_id not in coverable:
            errors.append(f"{prefix}.sourceBlock must reference a non-heading block")
            continue
        coverage_by_block[block_id] = coverage_by_block.get(block_id, 0) + 1
        classification = item.get("classification")
        if classification not in {"requirement", "context", "duplicate", "superseded", "engineering-setting"}:
            errors.append(f"{prefix}.classification is invalid")
        refs = item.get("requirementIds")
        if not isinstance(refs, list):
            errors.append(f"{prefix}.requirementIds must be an array")
            refs = []
        unknown = set(refs) - requirement_ids
        if unknown:
            errors.append(f"{prefix} references unknown requirements: {sorted(unknown)}")
        covered_requirement_ids.update(refs)
        if classification == "requirement" and not refs:
            errors.append(f"{prefix} classifies a requirement without requirementIds")
        if classification != "requirement" and refs:
            errors.append(f"{prefix} non-requirement coverage cannot bind requirementIds")
        if classification != "requirement" and (not isinstance(item.get("rationale"), str) or not item["rationale"].strip()):
            errors.append(f"{prefix}.rationale is required")
        if classification == "requirement" and not isinstance(item.get("rationale"), str):
            errors.append(f"{prefix}.rationale must be a string")
    missing_coverage = sorted(coverable - coverage_by_block.keys())
    duplicate_coverage = sorted(block for block, count in coverage_by_block.items() if count != 1)
    if missing_coverage:
        errors.append(f"source blocks lack coverage: {missing_coverage[:20]}" + ("..." if len(missing_coverage) > 20 else ""))
    if duplicate_coverage:
        errors.append(f"source blocks have duplicate coverage: {duplicate_coverage[:20]}")
    uncovered_requirements = sorted(requirement_ids - covered_requirement_ids)
    if uncovered_requirements:
        errors.append(f"requirements are not linked from source coverage: {uncovered_requirements}")

    bindings = analysis.get("contractBindings")
    if not isinstance(bindings, list):
        errors.append("contractBindings must be an array")
        bindings = []
    seen_targets: set[str] = set()
    for index, item in enumerate(bindings):
        prefix = f"contractBindings[{index}]"
        if not isinstance(item, dict) or not isinstance(item.get("target"), str) or not item["target"].strip():
            errors.append(f"{prefix}.target is required")
            continue
        _strict_keys(
            item,
            prefix,
            {"target", "requirementIds"},
            {"target", "requirementIds"},
            errors,
        )
        if item["target"] in seen_targets:
            errors.append(f"duplicate contract binding target: {item['target']}")
        seen_targets.add(item["target"])
        refs = item.get("requirementIds")
        if not isinstance(refs, list) or not refs:
            errors.append(f"{prefix}.requirementIds must not be empty")
        elif set(refs) - requirement_ids:
            errors.append(f"{prefix} references unknown requirements")
        elif len(refs) != len(set(refs)):
            errors.append(f"{prefix}.requirementIds must not contain duplicates")

    if errors:
        raise RequirementError("\n".join(errors))
    return {
        "requirementCount": len(requirement_ids),
        "decisionCount": len(decision_ids),
        "openDecisionCount": len(open_decisions),
        "pendingRequirementCount": len(pending_requirement_ids),
        "coveredBlockCount": len(coverage_by_block),
        "deferredBlockCount": deferred_block_count,
    }


def expected_contract_targets(contract: dict[str, Any]) -> set[str]:
    targets = {"project"}
    for module in contract["modules"]:
        module_id = module["id"]
        targets.add(f"module:{module_id}")
        targets.update(f"action:{module_id}:{action}" for action in module["actions"])
        targets.update(
            f"field:{module_id}:{field['code']}"
            for field in module["fields"]
            if field["type"] != "id"
        )
        targets.update(f"rule:{module_id}:{rule['id']}" for rule in module["businessRules"])
    return targets


def promotion_receipt_path(contract_path: Path) -> Path:
    return contract_path.with_suffix(".promotion.json")


def _validate_contract_bindings(analysis: dict[str, Any], contract: dict[str, Any]) -> int:
    requirement_by_id = {item["id"]: item for item in analysis["requirements"]}
    bindings = {item["target"]: item["requirementIds"] for item in analysis["contractBindings"]}
    expected = expected_contract_targets(contract)
    missing = sorted(expected - bindings.keys())
    unknown = sorted(bindings.keys() - expected)
    if missing or unknown:
        messages = []
        if missing:
            messages.append(f"missing contract bindings: {missing}")
        if unknown:
            messages.append(f"unknown contract bindings: {unknown}")
        raise RequirementError("; ".join(messages))
    for target, requirement_ids in bindings.items():
        invalid = [
            requirement_id
            for requirement_id in requirement_ids
            if requirement_by_id[requirement_id]["status"] != "confirmed"
            or requirement_by_id[requirement_id]["type"] == "non-goal"
        ]
        if invalid:
            raise RequirementError(f"{target} is bound to non-confirmed or non-goal requirements: {invalid}")
    return len(expected)


def require_promotion_receipt(contract: dict[str, Any], contract_path: Path) -> dict[str, Any]:
    contract_path = contract_path.resolve()
    receipt_path = promotion_receipt_path(contract_path)
    try:
        receipt = json.loads(receipt_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"formal contract has no valid promotion receipt: {receipt_path}: {error}") from error
    if not isinstance(receipt, dict):
        raise RequirementError("contract promotion receipt must be an object")
    expected_keys = {
        "schemaVersion", "projectId", "contractSha256", "analysisPath",
        "analysisSha256", "requirementsSha256", "boundTargetCount",
    }
    if set(receipt) != expected_keys:
        raise RequirementError("contract promotion receipt has invalid fields")
    if receipt.get("schemaVersion") != 1 or receipt.get("projectId") != contract["project"]["id"]:
        raise RequirementError("contract promotion receipt identifies another contract")
    if receipt.get("contractSha256") != sha256_bytes(contract_path.read_bytes()):
        raise RequirementError("contract changed after promotion")
    analysis_relative = receipt.get("analysisPath")
    if not isinstance(analysis_relative, str) or not analysis_relative:
        raise RequirementError("contract promotion receipt has no analysisPath")
    analysis_path = (receipt_path.parent / analysis_relative).resolve()
    analysis = load_analysis(analysis_path)
    if receipt.get("analysisSha256") != sha256_bytes(analysis_path.read_bytes()):
        raise RequirementError("requirement analysis changed after contract promotion")
    summary = validate_analysis(analysis, analysis_path)
    if summary["openDecisionCount"] or summary["pendingRequirementCount"]:
        raise RequirementError("promoted contract analysis is no longer decision-complete")
    if receipt.get("requirementsSha256") != analysis["source"]["requirementsSha256"]:
        raise RequirementError("contract promotion receipt is stale for the requirement source")
    if contract["source"]["sha256"] != receipt["requirementsSha256"]:
        raise RequirementError("contract source differs from its promoted analysis")
    bound_target_count = _validate_contract_bindings(analysis, contract)
    if receipt.get("boundTargetCount") != bound_target_count:
        raise RequirementError("contract promotion receipt has an invalid binding count")
    return receipt


def promote_contract(analysis_path: Path, candidate_path: Path, output_path: Path) -> dict[str, Any]:
    analysis_path = analysis_path.resolve()
    candidate_path = candidate_path.resolve()
    output_path = output_path.resolve()
    analysis = load_analysis(analysis_path)
    summary = validate_analysis(analysis, analysis_path)
    if summary["openDecisionCount"] or summary["pendingRequirementCount"]:
        raise RequirementError(
            f"contract promotion blocked: {summary['openDecisionCount']} open decisions, "
            f"{summary['pendingRequirementCount']} pending requirements"
        )
    contract = load_contract(candidate_path)
    require_generation_ready(contract, candidate_path)

    intake_path = (analysis_path.parent / analysis["source"]["intakePath"]).resolve()
    intake = load_intake(intake_path)
    requirements_source_path = require_current_intake(intake, intake_path)
    if contract["source"]["sha256"] != intake["source"]["sha256"]:
        raise RequirementError("candidate contract is not bound to the analyzed requirement source")

    bound_target_count = _validate_contract_bindings(analysis, contract)

    contract["source"]["path"] = _relative_path(requirements_source_path, output_path.parent)
    require_generation_ready(contract, output_path)
    encoded = canonical_json(contract)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    write_contract = True
    if output_path.exists():
        try:
            existing = json.loads(output_path.read_text(encoding="utf-8-sig"))
        except (OSError, json.JSONDecodeError) as error:
            raise RequirementError(f"refusing to overwrite unreadable existing contract: {output_path}: {error}") from error
        if existing != contract:
            raise RequirementError(f"refusing to overwrite different existing contract: {output_path}")
        write_contract = False
    if write_contract:
        output_path.write_text(encoded, encoding="utf-8")
    receipt_path = promotion_receipt_path(output_path)
    receipt = {
        "schemaVersion": 1,
        "projectId": contract["project"]["id"],
        "contractSha256": sha256_bytes(output_path.read_bytes()),
        "analysisPath": _relative_path(analysis_path, receipt_path.parent),
        "analysisSha256": sha256_bytes(analysis_path.read_bytes()),
        "requirementsSha256": intake["source"]["sha256"],
        "boundTargetCount": bound_target_count,
    }
    receipt_path.write_text(canonical_json(receipt), encoding="utf-8")
    return {
        "output": str(output_path),
        "receipt": str(receipt_path),
        "projectId": contract["project"]["id"],
        "requirementCount": summary["requirementCount"],
        "boundTargetCount": bound_target_count,
    }
