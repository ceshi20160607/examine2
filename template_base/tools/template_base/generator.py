from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from .common import canonical_json, sha256_bytes


class GenerationError(RuntimeError):
    pass


def _safe_relative(path: str) -> Path:
    candidate = Path(path)
    if candidate.is_absolute() or ".." in candidate.parts:
        raise GenerationError(f"generator produced unsafe path: {path}")
    return candidate


def _previous_generated_hashes(output_root: Path, manifest_name: str = "generation-manifest.json") -> dict[str, str]:
    manifest_path = output_root / _safe_relative(manifest_name)
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
        raise GenerationError(f"refusing to overwrite untracked generated file: {relative_name}")
    actual_hash = sha256_bytes(target.read_bytes())
    if actual_hash != previous_hash:
        raise GenerationError(f"generated file was modified outside the generator: {relative_name}")


def write_generated_tree(
    rendered: dict[str, str],
    output_root: Path,
    manifest_metadata: dict[str, Any],
    manifest_name: str = "generation-manifest.json",
) -> dict[str, Any]:
    output_root = output_root.resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    rendered = dict(sorted(rendered.items()))
    manifest_relative = _safe_relative(manifest_name)
    previous_hashes = _previous_generated_hashes(output_root, manifest_name)
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
        {"path": relative_name, "sha256": sha256_bytes(content.encode("utf-8"))}
        for relative_name, content in rendered.items()
    ]
    manifest = {"schemaVersion": 1, **manifest_metadata, "generatedFiles": file_entries}
    manifest_path = output_root / manifest_relative
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_content = canonical_json(manifest).encode("utf-8")
    if not manifest_path.exists() or manifest_path.read_bytes() != manifest_content:
        manifest_path.write_bytes(manifest_content)
        changed.append(manifest_relative.as_posix())
    return {"output": str(output_root), "changed": changed, "removed": removed, "manifest": manifest}


def tree_hashes(root: Path) -> dict[str, str]:
    return {
        path.relative_to(root).as_posix(): sha256_bytes(path.read_bytes())
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }
