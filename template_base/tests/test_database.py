from __future__ import annotations

import tempfile
import unittest
import sys
from pathlib import Path

BASE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.database import DatabaseError, prepare_database


class DatabasePreparationTests(unittest.TestCase):
    def test_prepares_combined_and_flyway_sql_from_versioned_sources(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory)
            (project / "db/init").mkdir(parents=True)
            (project / "db/update").mkdir(parents=True)
            (project / "db/migration").mkdir(parents=True)
            (project / "db/init/V1__identity.sql").write_text("create table identity_record (id bigint);\n", encoding="utf-8")
            (project / "db/init/V2__runtime.sql").write_text("create table runtime_record (id bigint);\n", encoding="utf-8")
            (project / "db/update/V3__audit.sql").write_text("create table audit_record (id bigint);\n", encoding="utf-8")
            (project / "db/migration/V99__stale.sql").write_text("select 1;\n", encoding="utf-8")

            result = prepare_database(project)

            self.assertEqual(2, result["initFiles"])
            self.assertEqual(1, result["updateFiles"])
            self.assertFalse((project / "db/migration/V99__stale.sql").exists())
            self.assertEqual(
                ["V1__identity.sql", "V2__runtime.sql", "V3__audit.sql"],
                sorted(path.name for path in (project / "db/migration").glob("V*.sql")),
            )
            final_sql = (project / "db/final.sql").read_text(encoding="utf-8")
            self.assertLess(final_sql.index("identity_record"), final_sql.index("runtime_record"))
            self.assertLess(final_sql.index("runtime_record"), final_sql.index("audit_record"))

    def test_rejects_update_version_that_is_not_newer_than_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            project = Path(directory)
            (project / "db/init").mkdir(parents=True)
            (project / "db/update").mkdir(parents=True)
            (project / "db/init/V2__baseline.sql").write_text("select 2;\n", encoding="utf-8")
            (project / "db/update/V1__older.sql").write_text("select 1;\n", encoding="utf-8")

            with self.assertRaisesRegex(DatabaseError, "must be newer"):
                prepare_database(project)


if __name__ == "__main__":
    unittest.main()
