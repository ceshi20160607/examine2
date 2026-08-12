from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Callable

from .contract import load_contract, require_generation_ready, sha256_bytes
from .generator import check_repeatable


FORBIDDEN_BROWSER_MOCKS = {
    "request interception": re.compile(r"\b(?:page|context)\.route\s*\("),
    "fulfilled fake response": re.compile(r"\broute\.fulfill\s*\("),
    "mock service worker": re.compile(r"\b(?:msw|mockServiceWorker|setupWorker)\b", re.IGNORECASE),
    "fetch mock": re.compile(r"\bfetchMock\b", re.IGNORECASE),
}

RUNTIME_CHECKS = (
    "backendTests",
    "frontendBuild",
    "backendHealth",
    "frontendHealth",
    "browserJourney",
    "mysqlReadback",
)


def _read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"cannot read {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def _gate(check: Callable[[], str]) -> dict[str, Any]:
    try:
        return {"passed": True, "detail": check()}
    except Exception as error:  # gate output must report every failure in one run
        return {"passed": False, "detail": str(error)}


def _manifest_integrity(contract_path: Path, output_root: Path) -> tuple[dict[str, Any], str]:
    manifest_path = output_root / "generation-manifest.json"
    manifest = _read_json(manifest_path)
    expected_contract_hash = sha256_bytes(contract_path.read_bytes())
    if manifest.get("contractSha256") != expected_contract_hash:
        raise ValueError("generation manifest is not bound to the current contract")

    contract = load_contract(contract_path)
    if manifest.get("sourceSha256") != contract["source"]["sha256"]:
        raise ValueError("generation manifest is not bound to the current user requirement")

    entries = manifest.get("generatedFiles")
    if not isinstance(entries, list) or not entries:
        raise ValueError("generation manifest has no generated files")
    seen: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
            raise ValueError("generation manifest contains an invalid file entry")
        relative_name = entry["path"]
        relative = Path(relative_name)
        if relative.is_absolute() or ".." in relative.parts:
            raise ValueError(f"unsafe generated path: {relative_name}")
        if relative_name in seen:
            raise ValueError(f"duplicate generated path: {relative_name}")
        seen.add(relative_name)
        target = output_root / relative
        if not target.is_file():
            raise ValueError(f"generated file is missing: {relative_name}")
        if sha256_bytes(target.read_bytes()) != entry.get("sha256"):
            raise ValueError(f"generated file hash mismatch: {relative_name}")
    return manifest, sha256_bytes(manifest_path.read_bytes())


def _no_mocked_browser_journey(output_root: Path) -> str:
    e2e_root = output_root / "frontend" / "tests" / "e2e"
    specs = sorted(e2e_root.glob("*.spec.ts"))
    if not specs:
        raise ValueError("no browser journey exists")
    violations: list[str] = []
    combined = ""
    for spec in specs:
        content = spec.read_text(encoding="utf-8")
        combined += content
        for label, pattern in FORBIDDEN_BROWSER_MOCKS.items():
            if pattern.search(content):
                violations.append(f"{spec.name}: {label}")
    if violations:
        raise ValueError("browser journey contains mocked transport: " + ", ".join(violations))
    for required in ("page.goto", "page.reload", "response.url().includes('/api/')"):
        if required not in combined:
            raise ValueError(f"browser journey lacks real-chain assertion: {required}")
    return f"{len(specs)} browser journey(s), no transport mocking"


def _runtime_evidence(
    evidence_path: Path,
    contract_path: Path,
    manifest_hash: str,
) -> str:
    evidence = _read_json(evidence_path)
    if evidence.get("schemaVersion") != 1:
        raise ValueError("runtime evidence schemaVersion must equal 1")
    if evidence.get("contractSha256") != sha256_bytes(contract_path.read_bytes()):
        raise ValueError("runtime evidence is stale for the current contract")
    if evidence.get("generationManifestSha256") != manifest_hash:
        raise ValueError("runtime evidence is stale for the generated tree")
    if evidence.get("journeyMode") != "browser-ui-to-api-to-mysql":
        raise ValueError("runtime evidence does not prove the required real journey")
    if evidence.get("mocked") is not False:
        raise ValueError("runtime evidence must explicitly declare mocked=false")
    checks = evidence.get("checks")
    if not isinstance(checks, dict):
        raise ValueError("runtime evidence checks are missing")
    failed = [name for name in RUNTIME_CHECKS if checks.get(name) is not True]
    if failed:
        raise ValueError("runtime checks did not pass: " + ", ".join(failed))
    return "builds, health checks, browser journey and MySQL readback are hash-bound"


def _user_acceptance(
    acceptance_path: Path,
    contract_path: Path,
    manifest_hash: str,
    project_id: str,
) -> str:
    acceptance = _read_json(acceptance_path)
    if acceptance.get("accepted") is not True:
        raise ValueError("user acceptance is not true")
    if acceptance.get("projectId") != project_id:
        raise ValueError("user acceptance is for another project")
    if acceptance.get("contractSha256") != sha256_bytes(contract_path.read_bytes()):
        raise ValueError("user acceptance is stale for the current contract")
    if acceptance.get("generationManifestSha256") != manifest_hash:
        raise ValueError("user acceptance is stale for the generated tree")
    for field in ("acceptedBy", "acceptedAt", "scope"):
        if not isinstance(acceptance.get(field), str) or not acceptance[field].strip():
            raise ValueError(f"user acceptance requires {field}")
    if acceptance.get("recordedBy") != "user":
        raise ValueError("acceptance must be explicitly recordedBy=user")
    return f"accepted by {acceptance['acceptedBy']} at {acceptance['acceptedAt']}"


def delivery_status(
    contract_path: Path,
    output_root: Path,
    evidence_path: Path | None = None,
    acceptance_path: Path | None = None,
) -> dict[str, Any]:
    contract_path = contract_path.resolve()
    output_root = output_root.resolve()
    evidence_path = (evidence_path or output_root / ".runtime" / "delivery-evidence.json").resolve()
    acceptance_path = (acceptance_path or contract_path.parent / "user-acceptance.json").resolve()
    contract = load_contract(contract_path)

    gates: dict[str, dict[str, Any]] = {}
    gates["contractReady"] = _gate(lambda: _contract_ready(contract, contract_path))
    gates["generationRepeatable"] = _gate(
        lambda: f"tree {check_repeatable(contract, contract_path)['treeSha256']}"
    )

    manifest: dict[str, Any] = {}
    manifest_hash = ""
    try:
        manifest, manifest_hash = _manifest_integrity(contract_path, output_root)
        gates["generatedTreeIntegrity"] = {"passed": True, "detail": f"{len(manifest['generatedFiles'])} files hash-verified"}
    except Exception as error:
        gates["generatedTreeIntegrity"] = {"passed": False, "detail": str(error)}

    gates["realJourneySource"] = _gate(lambda: _no_mocked_browser_journey(output_root))
    if manifest_hash:
        gates["runtimeEvidence"] = _gate(lambda: _runtime_evidence(evidence_path, contract_path, manifest_hash))
        gates["userAcceptance"] = _gate(
            lambda: _user_acceptance(
                acceptance_path,
                contract_path,
                manifest_hash,
                str(manifest.get("projectId", "")),
            )
        )
    else:
        gates["runtimeEvidence"] = {"passed": False, "detail": "generated tree integrity failed first"}
        gates["userAcceptance"] = {"passed": False, "detail": "generated tree integrity failed first"}

    engineering_gate_names = (
        "contractReady",
        "generationRepeatable",
        "generatedTreeIntegrity",
        "realJourneySource",
        "runtimeEvidence",
    )
    engineering_ready = all(gates[name]["passed"] for name in engineering_gate_names)
    user_accepted = gates["userAcceptance"]["passed"]
    if not gates["contractReady"]["passed"]:
        phase = "INTAKE"
    elif not gates["generationRepeatable"]["passed"] or not gates["generatedTreeIntegrity"]["passed"]:
        phase = "CONTRACT_READY"
    elif not gates["realJourneySource"]["passed"] or not gates["runtimeEvidence"]["passed"]:
        phase = "GENERATED"
    elif not user_accepted:
        phase = "ENGINEERING_READY"
    else:
        phase = "RELEASE_READY"
    return {
        "projectId": contract.get("project", {}).get("id"),
        "computedPhase": phase,
        "engineeringReady": engineering_ready,
        "userAccepted": user_accepted,
        "releaseReady": engineering_ready and user_accepted,
        "gates": gates,
        "evidencePath": str(evidence_path),
        "acceptancePath": str(acceptance_path),
    }


def _contract_ready(contract: dict[str, Any], contract_path: Path) -> str:
    require_generation_ready(contract, contract_path)
    return "valid, source-bound and has no unresolved product decisions"
