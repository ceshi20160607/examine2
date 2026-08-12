from __future__ import annotations

import json
import tempfile
from pathlib import Path
from typing import Any

from .contract import canonical_json, require_generation_ready, sha256_bytes
from .requirements import require_promotion_receipt
from .render_backend import render_backend
from .render_frontend import render_deploy, render_frontend


class GenerationError(RuntimeError):
    pass


def _safe_relative(path: str) -> Path:
    candidate = Path(path)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise GenerationError(f"renderer produced unsafe path: {path}")
    return candidate


def _previous_generated_hashes(output_root: Path) -> dict[str, str]:
    manifest_path = output_root / "generation-manifest.json"
    if not manifest_path.exists():
        return {}
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        entries = manifest["generatedFiles"]
        if not isinstance(entries, list):
            raise TypeError("generatedFiles must be an array")
        hashes: dict[str, str] = {}
        for entry in entries:
            relative_name = entry["path"]
            digest = entry["sha256"]
            _safe_relative(relative_name)
            if not isinstance(digest, str) or len(digest) != 64:
                raise TypeError(f"invalid sha256 for {relative_name}")
            hashes[relative_name] = digest
        return hashes
    except (OSError, KeyError, TypeError, json.JSONDecodeError) as error:
        raise GenerationError(f"cannot trust existing generation manifest: {error}") from error


def _assert_safe_to_replace(target: Path, relative_name: str, previous_hashes: dict[str, str]) -> None:
    if not target.exists():
        return
    previous_hash = previous_hashes.get(relative_name)
    if previous_hash is None:
        raise GenerationError(f"refusing to overwrite untracked file in generated output: {relative_name}")
    actual_hash = sha256_bytes(target.read_bytes())
    if actual_hash != previous_hash:
        raise GenerationError(f"generated file was modified outside the generator: {relative_name}")


def render_project(contract: dict[str, Any]) -> dict[str, str]:
    rendered: dict[str, str] = {}
    for group in (render_backend(contract), render_frontend(contract), render_deploy(contract)):
        overlap = rendered.keys() & group.keys()
        if overlap:
            raise GenerationError(f"duplicate generated paths: {sorted(overlap)}")
        rendered.update(group)
    return dict(sorted(rendered.items()))


def generate_project(contract: dict[str, Any], contract_path: Path, output_root: Path) -> dict[str, Any]:
    require_generation_ready(contract, contract_path)
    require_promotion_receipt(contract, contract_path)
    output_root = output_root.resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    rendered = render_project(contract)
    previous_hashes = _previous_generated_hashes(output_root)
    changed: list[str] = []
    removed: list[str] = []
    for relative_name, content in rendered.items():
        relative = _safe_relative(relative_name)
        target = output_root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        encoded = content.encode("utf-8")
        if not target.exists() or target.read_bytes() != encoded:
            _assert_safe_to_replace(target, relative_name, previous_hashes)
            target.write_bytes(encoded)
            changed.append(relative.as_posix())

    for relative_name, previous_hash in sorted(previous_hashes.items()):
        if relative_name in rendered:
            continue
        relative = _safe_relative(relative_name)
        target = output_root / relative
        if not target.exists():
            continue
        if sha256_bytes(target.read_bytes()) != previous_hash:
            raise GenerationError(f"refusing to remove modified stale generated file: {relative_name}")
        target.unlink()
        removed.append(relative.as_posix())

    file_entries = [
        {
            "path": relative_name,
            "sha256": sha256_bytes(content.encode("utf-8")),
        }
        for relative_name, content in rendered.items()
    ]
    manifest = {
        "schemaVersion": 1,
        "generatorVersion": "0.2.0-dev",
        "projectId": contract["project"]["id"],
        "sourceSha256": contract["source"]["sha256"],
        "contractSha256": sha256_bytes(contract_path.read_bytes()),
        "generatedFiles": file_entries,
    }
    manifest_path = output_root / "generation-manifest.json"
    manifest_content = canonical_json(manifest).encode("utf-8")
    if not manifest_path.exists() or manifest_path.read_bytes() != manifest_content:
        manifest_path.write_bytes(manifest_content)
        changed.append("generation-manifest.json")
    return {"output": str(output_root), "changed": changed, "removed": removed, "manifest": manifest}


def tree_hashes(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): sha256_bytes(path.read_bytes())
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


def check_repeatable(contract: dict[str, Any], contract_path: Path) -> dict[str, Any]:
    require_generation_ready(contract, contract_path)
    require_promotion_receipt(contract, contract_path)
    with tempfile.TemporaryDirectory(prefix="template-base-a-") as first_dir, tempfile.TemporaryDirectory(prefix="template-base-b-") as second_dir:
        first = Path(first_dir)
        second = Path(second_dir)
        generate_project(contract, contract_path, first)
        generate_project(contract, contract_path, second)
        first_hashes = tree_hashes(first)
        second_hashes = tree_hashes(second)
        if first_hashes != second_hashes:
            all_paths = sorted(first_hashes.keys() | second_hashes.keys())
            differences = [path for path in all_paths if first_hashes.get(path) != second_hashes.get(path)]
            raise GenerationError(f"generation is not repeatable: {differences}")
        second_run = generate_project(contract, contract_path, first)
        if second_run["changed"]:
            raise GenerationError(f"second generation changed files: {second_run['changed']}")
        return {
            "repeatable": True,
            "fileCount": len(first_hashes),
            "treeSha256": sha256_bytes(canonical_json(first_hashes).encode("utf-8")),
        }
