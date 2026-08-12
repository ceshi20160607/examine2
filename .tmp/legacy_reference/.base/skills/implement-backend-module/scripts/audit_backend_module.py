#!/usr/bin/env python3
"""Audit Java backend source for Base/persistence boundary violations."""

from __future__ import annotations

import argparse
import json
import re
import sys
import tempfile
from pathlib import Path


SQL_PATTERN = re.compile(
    r"(?is)(?:@(Select|Insert|Update|Delete)\s*\(|\"(?:[^\"\\]|\\.)*\b(SELECT\s+(?:[^\"\\]|\\.)+?\s+FROM|INSERT\s+INTO|UPDATE\s+[A-Za-z_][A-Za-z0-9_.]*\s+SET|DELETE\s+FROM|MERGE\s+INTO)\b)"
)
JDBC_PATTERN = re.compile(
    r"\b(JdbcTemplate|NamedParameterJdbcTemplate|ResultSet|RowMapper|PreparedStatement)\b|java\.sql\."
)
USE_CASE_MARKERS = ("/manage/", "/application/", "/domain/", "/controller/")
PERSISTENCE_MARKERS = ("/repository/", "/adapter/jdbc/", "/infrastructure/persistence/", "/mapper/", "/jdbc/")


def portable(path: Path) -> str:
    return path.as_posix()


def audit(module_root: Path, source_prefix: str | None = None) -> dict[str, object]:
    module_root = module_root.resolve()
    source_root = (module_root / "src" / "main" / "java").resolve()
    if not source_root.is_dir():
        return {
            "verdict": "fail",
            "moduleRoot": portable(module_root),
            "errors": [f"Java source root does not exist: {portable(source_root)}"],
            "warnings": [],
            "filesChecked": 0,
        }

    scan_root = source_root
    normalized_prefix = None
    if source_prefix:
        normalized_prefix = source_prefix.replace("\\", "/").strip("/")
        scan_root = (source_root / normalized_prefix).resolve()
        try:
            scan_root.relative_to(source_root.resolve())
        except ValueError:
            return {
                "verdict": "fail",
                "moduleRoot": portable(module_root),
                "sourcePrefix": normalized_prefix,
                "errors": [f"Source prefix escapes Java source root: {normalized_prefix}"],
                "warnings": [],
                "filesChecked": 0,
            }
        if not scan_root.is_dir():
            return {
                "verdict": "fail",
                "moduleRoot": portable(module_root),
                "sourcePrefix": normalized_prefix,
                "errors": [f"Source prefix does not exist: {normalized_prefix}"],
                "warnings": [],
                "filesChecked": 0,
            }

    errors: list[str] = []
    warnings: list[str] = []
    java_files = sorted(scan_root.rglob("*.java"))
    if not java_files:
        errors.append(f"No Java files found under source prefix: {normalized_prefix or '.'}")
    for path in java_files:
        relative = "/" + portable(path.relative_to(source_root))
        text = path.read_text(encoding="utf-8")
        line_count = len(text.splitlines())
        in_base = "/base/" in relative
        persistence_adapter = any(marker in relative for marker in PERSISTENCE_MARKERS) or bool(
            re.search(r"(?:Repository|Store|Gateway)\.java$", path.name)
        ) or path.name.startswith("Jdbc")
        use_case_code = any(marker in relative for marker in USE_CASE_MARKERS) and not in_base
        has_sql = bool(SQL_PATTERN.search(text))
        has_jdbc = bool(JDBC_PATTERN.search(text))

        if in_base and (has_sql or has_jdbc or ".manage." in text or ".application." in text):
            errors.append(f"{relative}: generated Base leaks raw SQL/JDBC or business-layer dependencies")
        if (has_sql or has_jdbc) and not in_base and not persistence_adapter:
            errors.append(f"{relative}: SQL/JDBC is outside a repository/mapper persistence adapter")
        if use_case_code and line_count > 500:
            warnings.append(
                f"{relative}: {line_count} lines; review aggregate and use-case boundaries before expansion"
            )
        if persistence_adapter:
            sql_count = len(SQL_PATTERN.findall(text))
            if sql_count > 20:
                warnings.append(
                    f"{relative}: {sql_count} SQL statements; split by named query/aggregate responsibility"
                )

    return {
        "verdict": "fail" if errors else "pass",
        "moduleRoot": portable(module_root),
        "sourcePrefix": normalized_prefix,
        "filesChecked": len(java_files),
        "errors": errors,
        "warnings": warnings,
    }


def self_test() -> int:
    with tempfile.TemporaryDirectory(prefix="backend-boundary-") as temporary:
        root = Path(temporary) / "module"
        java_root = root / "src" / "main" / "java" / "example"
        (java_root / "base" / "entity").mkdir(parents=True)
        (java_root / "repository").mkdir(parents=True)
        (java_root / "manage").mkdir(parents=True)
        (java_root / "base" / "entity" / "Customer.java").write_text(
            "package example.base.entity; public class Customer {}\n", encoding="utf-8"
        )
        (java_root / "repository" / "CustomerRepository.java").write_text(
            'package example.repository; import org.springframework.jdbc.core.JdbcTemplate; '
            'class CustomerRepository { String q = "SELECT id FROM customer"; JdbcTemplate jdbc; }\n',
            encoding="utf-8",
        )
        service = java_root / "manage" / "CreateCustomerService.java"
        service.write_text(
            "package example.manage; class CreateCustomerService { void execute() {} }\n",
            encoding="utf-8",
        )
        positive = audit(root)
        if positive["verdict"] != "pass":
            print(json.dumps(positive, ensure_ascii=False, indent=2))
            return 1
        service.write_text(
            'package example.manage; import org.springframework.jdbc.core.JdbcTemplate; '
            'class CreateCustomerService { JdbcTemplate jdbc; String q = "SELECT id FROM customer"; }\n',
            encoding="utf-8",
        )
        negative = audit(root)
        if negative["verdict"] != "fail" or not negative["errors"]:
            print(json.dumps(negative, ensure_ascii=False, indent=2))
            return 1
        scoped = audit(root, "example/base")
        if scoped["verdict"] != "pass" or scoped["filesChecked"] != 1:
            print(json.dumps(scoped, ensure_ascii=False, indent=2))
            return 1
    print("SELF_TEST_PASS backend Base and SQL boundary cases")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("module_root", type=Path, nargs="?")
    parser.add_argument(
        "--source-prefix",
        help="audit only this path below src/main/java; use only when an accepted contract freezes the excluded legacy tree",
    )
    parser.add_argument("--json-output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.module_root is None:
        parser.error("module_root is required unless --self-test is used")
    result = audit(args.module_root.resolve(), args.source_prefix)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)
    return 1 if result["verdict"] == "fail" else 0


if __name__ == "__main__":
    sys.exit(main())
