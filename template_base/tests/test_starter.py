from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


BASE_ROOT = Path(__file__).resolve().parents[1]
REPO_ROOT = BASE_ROOT.parent
sys.path.insert(0, str(BASE_ROOT / "tools"))

from template_base.generator import GenerationError
from template_base.starter import (
    check_starter_repeatable,
    generate_starter,
    load_starter,
    validate_starter,
)


class ProjectStarterTest(unittest.TestCase):
    starter_path = REPO_ROOT / "projects" / "unexamine" / "tools" / "project-starter.json"

    def test_current_starter_is_source_bound_and_repeatable(self) -> None:
        starter = load_starter(self.starter_path)
        self.assertEqual([], validate_starter(starter, self.starter_path))
        result = check_starter_repeatable(starter, self.starter_path)
        self.assertTrue(result["repeatable"])
        self.assertGreater(result["fileCount"], 20)

    def test_starter_generates_only_the_buildable_shell_without_persistence_code(self) -> None:
        starter = load_starter(self.starter_path)
        with tempfile.TemporaryDirectory(prefix="template-base-runtime-starter-") as directory:
            root = Path(directory)
            first = generate_starter(starter, self.starter_path, root)
            second = generate_starter(starter, self.starter_path, root)
            self.assertTrue(first["changed"])
            self.assertEqual([], second["changed"])
            generated_paths = {item["path"] for item in first["manifest"]["generatedFiles"]}
            self.assertIn("backend/pom.xml", generated_paths)
            self.assertIn("frontend/package.json", generated_paths)
            self.assertIn("ai/status/README.md", generated_paths)
            self.assertIn("db/init/README.md", generated_paths)
            self.assertIn("question.md", generated_paths)
            self.assertIn("tools/README.md", generated_paths)
            self.assertFalse(any(path.startswith("backend/") and "/base/" in path for path in generated_paths))
            self.assertFalse(any(path.startswith("backend/") and "/entity/" in path for path in generated_paths))
            self.assertFalse(any("Security" in path for path in generated_paths))
            application_path = next(path for path in generated_paths if path.endswith("Application.java"))
            application_text = (root / application_path).read_text(encoding="utf-8")
            self.assertNotIn("MapperScan", application_text)
            pom_text = (root / "backend" / "pom.xml").read_text(encoding="utf-8")
            self.assertIn("mybatis-plus-jsqlparser", pom_text)
            self.assertIn("require-java-21", pom_text)
            self.assertNotIn("spring-security", pom_text)
            router_text = (root / "frontend" / "src" / "router.ts").read_text(encoding="utf-8")
            self.assertIn("productRoutes", router_text)

    def test_starter_refuses_to_overwrite_hand_modified_generated_file(self) -> None:
        starter = load_starter(self.starter_path)
        with tempfile.TemporaryDirectory(prefix="template-base-runtime-dirty-") as directory:
            root = Path(directory)
            generate_starter(starter, self.starter_path, root)
            (root / "backend" / "pom.xml").write_text("manual edit", encoding="utf-8")
            with self.assertRaisesRegex(GenerationError, "modified outside the generator"):
                generate_starter(starter, self.starter_path, root)

    def test_stale_requirements_hash_blocks_generation(self) -> None:
        starter = json.loads(json.dumps(load_starter(self.starter_path)))
        starter["source"]["requirementsSha256"] = "0" * 64
        issues = validate_starter(starter, self.starter_path)
        self.assertTrue(any(issue.path == "$.source.requirementsSha256" for issue in issues))


if __name__ == "__main__":
    unittest.main()
