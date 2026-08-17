from __future__ import annotations

import json
import os
import re
from pathlib import Path
from typing import Any

from .common import canonical_json, sha256_bytes
from .rules import validate_rule


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


def build_analysis_plan(
    intake_path: Path,
    output_path: Path,
    max_blocks: int = 12,
    max_lines: int = 200,
) -> dict[str, Any]:
    intake_path = intake_path.resolve()
    output_path = output_path.resolve()
    if max_blocks < 1 or max_lines < 1:
        raise RequirementError("max-blocks and max-lines must be positive")
    intake = load_intake(intake_path)
    require_current_intake(intake, intake_path)
    coverable = [block for block in intake["blocks"] if block["kind"] != "heading"]
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
        "schemaVersion": 1,
        "intake": {
            "path": _relative_path(intake_path, output_path.parent),
            "sha256": sha256_bytes(intake_path.read_bytes()),
            "requirementsSha256": intake["source"]["sha256"],
        },
        "packetCount": len(packets),
        "packets": packets,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(canonical_json(plan), encoding="utf-8")
    return {
        "output": str(output_path),
        "packetCount": len(packets),
        "blockCount": len(coverable),
        "deferredBlockCount": 0,
    }


def _load_current_plan(plan_path: Path) -> tuple[dict[str, Any], Path, dict[str, Any]]:
    try:
        plan = json.loads(plan_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read analysis plan: {error}") from error
    if not isinstance(plan, dict) or plan.get("schemaVersion") != 1:
        raise RequirementError("analysis plan schemaVersion must equal 1")
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
    if "scope" in plan:
        raise RequirementError("partial requirement scope is forbidden; analyze the full source")
    coverable = [block["id"] for block in intake["blocks"] if block["kind"] != "heading"]
    if planned_blocks != coverable:
        raise RequirementError("analysis plan does not partition every source block exactly once")
    return plan, intake_path, intake


def validate_analysis_fragment(fragment_path: Path) -> dict[str, Any]:
    fragment_path = fragment_path.resolve()
    try:
        fragment = json.loads(fragment_path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read analysis fragment {fragment_path}: {error}") from error
    rule_issues = validate_rule(fragment, "requirement-fragment.schema.json")
    if rule_issues:
        raise RequirementError("\n".join(str(issue) for issue in rule_issues))
    source = fragment["source"]
    plan_path = (fragment_path.parent / source["planPath"]).resolve()
    plan, _, _ = _load_current_plan(plan_path)
    if source["planSha256"] != sha256_bytes(plan_path.read_bytes()):
        raise RequirementError(f"analysis fragment is stale for the plan: {fragment_path}")
    packet = next((item for item in plan["packets"] if item["id"] == fragment["packetId"]), None)
    if packet is None:
        raise RequirementError(f"analysis fragment has unknown packetId: {fragment['packetId']}")
    if fragment_path.stem != fragment["packetId"]:
        raise RequirementError(f"analysis fragment filename must equal packetId: {fragment_path}")
    allowed_blocks = set(packet["blockIds"])
    for collection_name in ("requirements", "decisions"):
        for item in fragment[collection_name]:
            if not isinstance(item, dict) or not set(item.get("sourceBlocks", [])) <= allowed_blocks:
                raise RequirementError(f"{fragment_path} {collection_name} references blocks outside {fragment['packetId']}")
    covered = {
        item.get("sourceBlock")
        for item in fragment["coverage"]
        if isinstance(item, dict)
    }
    if covered != allowed_blocks or len(fragment["coverage"]) != len(allowed_blocks):
        raise RequirementError(f"{fragment_path} does not cover exactly the blocks in {fragment['packetId']}")
    return {
        "valid": True,
        "packetId": fragment["packetId"],
        "requirementCount": len(fragment["requirements"]),
        "decisionCount": len(fragment["decisions"]),
        "coveredBlockCount": len(covered),
    }


def collect_analysis_fragments(plan_path: Path, fragment_paths: list[Path], output_path: Path) -> dict[str, Any]:
    plan_path = plan_path.resolve()
    output_path = output_path.resolve()
    plan, intake_path, intake = _load_current_plan(plan_path)
    if not fragment_paths:
        raise RequirementError("at least one analysis fragment is required")
    plan_hash = sha256_bytes(plan_path.read_bytes())
    packet_by_id = {packet["id"]: packet for packet in plan["packets"]}
    submitted: set[str] = set()
    fragments: list[dict[str, Any]] = []
    candidate_requirement_count = 0
    candidate_decision_count = 0
    covered_block_count = 0
    for raw_path in fragment_paths:
        fragment_path = raw_path.resolve()
        summary = validate_analysis_fragment(fragment_path)
        fragment = json.loads(fragment_path.read_text(encoding="utf-8-sig"))
        source = fragment["source"]
        if source["planSha256"] != plan_hash or (fragment_path.parent / source["planPath"]).resolve() != plan_path:
            raise RequirementError(f"analysis fragment references another plan: {fragment_path}")
        packet_id = summary["packetId"]
        if packet_id in submitted:
            raise RequirementError(f"analysis packet was submitted more than once: {packet_id}")
        submitted.add(packet_id)
        candidate_requirement_count += summary["requirementCount"]
        candidate_decision_count += summary["decisionCount"]
        covered_block_count += summary["coveredBlockCount"]
        fragments.append({
            "packetId": packet_id,
            "path": _relative_path(fragment_path, output_path.parent),
            "sha256": sha256_bytes(fragment_path.read_bytes()),
            "candidateRequirementCount": summary["requirementCount"],
            "candidateDecisionCount": summary["decisionCount"],
            "coveredBlockCount": summary["coveredBlockCount"],
        })

    missing_packets = sorted(packet_by_id.keys() - submitted)
    if missing_packets:
        raise RequirementError(f"analysis packets have no fragment: {missing_packets}")
    collection = {
        "schemaVersion": 1,
        "source": {
            "planPath": _relative_path(plan_path, output_path.parent),
            "planSha256": plan_hash,
            "requirementsSha256": intake["source"]["sha256"],
        },
        "fragmentCount": len(fragments),
        "fragments": sorted(fragments, key=lambda item: item["packetId"]),
    }
    rule_issues = validate_rule(collection, "requirement-fragment-index.schema.json")
    if rule_issues:
        raise RequirementError("\n".join(str(issue) for issue in rule_issues))
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(canonical_json(collection), encoding="utf-8")
    return {
        "output": str(output_path),
        "packetCount": len(submitted),
        "candidateRequirementCount": candidate_requirement_count,
        "candidateDecisionCount": candidate_decision_count,
        "coveredBlockCount": covered_block_count,
    }


def load_analysis(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise RequirementError(f"cannot read requirement analysis: {error}") from error
    if not isinstance(value, dict):
        raise RequirementError("requirement analysis must be an object")
    return value


def validate_analysis(analysis: dict[str, Any], analysis_path: Path) -> dict[str, Any]:
    errors = [str(issue) for issue in validate_rule(analysis, "requirement-analysis.schema.json")]
    _strict_keys(
        analysis,
        "$",
        {"schemaVersion", "source", "requirements", "decisions", "coverage"},
        {"schemaVersion", "source", "requirements", "decisions", "coverage"},
        errors,
    )
    schema_version = analysis.get("schemaVersion")
    if schema_version != 1:
        errors.append("schemaVersion must equal 1")
    source = analysis.get("source")
    if not isinstance(source, dict):
        raise RequirementError("analysis source must be an object")
    base_source_keys = {"intakePath", "intakeSha256", "requirementsSha256"}
    required_source_keys = base_source_keys
    allowed_source_keys = base_source_keys | {"fragmentIndexPath", "fragmentIndexSha256"}
    _strict_keys(source, "source", required_source_keys, allowed_source_keys, errors)
    fragment_keys = {"fragmentIndexPath", "fragmentIndexSha256"} & source.keys()
    if fragment_keys and fragment_keys != {"fragmentIndexPath", "fragmentIndexSha256"}:
        errors.append("source.fragmentIndexPath and source.fragmentIndexSha256 must appear together")
    elif fragment_keys:
        fragment_index_relative = source["fragmentIndexPath"]
        if not isinstance(fragment_index_relative, str) or not fragment_index_relative:
            errors.append("source.fragmentIndexPath must be a non-blank string")
        else:
            fragment_index_path = (analysis_path.parent / fragment_index_relative).resolve()
            try:
                fragment_index = json.loads(fragment_index_path.read_text(encoding="utf-8-sig"))
            except (OSError, json.JSONDecodeError) as error:
                errors.append(f"cannot read source fragment index: {error}")
            else:
                rule_issues = validate_rule(fragment_index, "requirement-fragment-index.schema.json")
                errors.extend(str(issue) for issue in rule_issues)
                if source["fragmentIndexSha256"] != sha256_bytes(fragment_index_path.read_bytes()):
                    errors.append("analysis is not bound to the current fragment index")
                if fragment_index.get("source", {}).get("requirementsSha256") != source.get("requirementsSha256"):
                    errors.append("fragment index is not bound to the current requirements source")
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
