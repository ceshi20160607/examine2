from __future__ import annotations

import hashlib
import re
from pathlib import Path


VERSIONED = re.compile(r"^V(?P<version>\d+)__(?P<name>[a-z0-9_]+)\.sql$")


class DatabaseError(ValueError):
    pass


def _read_sql_files(root: Path) -> list[Path]:
    files = list(root.glob("V*.sql"))
    versions: list[int] = []
    for path in files:
        match = VERSIONED.fullmatch(path.name)
        if match is None:
            raise DatabaseError(f"invalid SQL file name: {path}")
        versions.append(int(match.group("version")))
    if len(versions) != len(set(versions)):
        raise DatabaseError(f"duplicate SQL version under {root}")
    return sorted(files, key=lambda path: int(VERSIONED.fullmatch(path.name).group("version")))


def _merge(files: list[Path], db_root: Path, empty_message: str) -> str:
    if not files:
        return f"-- {empty_message}\n"
    parts = []
    for path in files:
        text = path.read_text(encoding="utf-8-sig").strip()
        parts.append(f"-- source: {path.relative_to(db_root).as_posix()}\n{text}\n")
    return "\n".join(parts)


def _write_if_changed(path: Path, content: str) -> None:
    encoded = content.encode("utf-8")
    if path.is_file() and path.read_bytes() == encoded:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encoded)


def prepare_database(project_root: Path) -> dict[str, object]:
    project_root = project_root.resolve()
    db_root = project_root / "db"
    base_root = db_root / "base"
    update_root = db_root / "update"
    migration_root = db_root / "migration"
    if not db_root.is_dir():
        raise DatabaseError(f"project db directory does not exist: {db_root}")

    base_files = _read_sql_files(base_root)
    update_files = _read_sql_files(update_root)
    if not base_files:
        raise DatabaseError("db/base must contain at least one versioned SQL file")
    base_versions = {int(VERSIONED.fullmatch(path.name).group("version")) for path in base_files}
    update_versions = {int(VERSIONED.fullmatch(path.name).group("version")) for path in update_files}
    if base_versions & update_versions:
        raise DatabaseError("base and update SQL versions overlap")
    if update_versions and min(update_versions) <= max(base_versions):
        raise DatabaseError("every update SQL version must be newer than the base SQL versions")

    base_sql = _merge(base_files, db_root, "No base SQL has been designed.")
    update_sql = _merge(update_files, db_root, "No post-baseline database update exists yet.")
    all_sql = base_sql.rstrip() + "\n\n" + update_sql
    _write_if_changed(db_root / "base.sql", base_sql)
    _write_if_changed(db_root / "update.sql", update_sql)
    _write_if_changed(db_root / "all.sql", all_sql)

    migration_root.mkdir(parents=True, exist_ok=True)
    expected = {path.name for path in base_files + update_files}
    for stale in migration_root.glob("V*.sql"):
        if stale.name not in expected:
            stale.unlink()
    for source in base_files + update_files:
        _write_if_changed(migration_root / source.name, source.read_text(encoding="utf-8-sig"))

    return {
        "projectRoot": project_root.as_posix(),
        "baseFiles": len(base_files),
        "updateFiles": len(update_files),
        "migrationFiles": len(expected),
        "allSqlSha256": hashlib.sha256(all_sql.encode("utf-8")).hexdigest(),
    }
