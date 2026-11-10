"""按当前模块 SQL 生成空库结构，并单独保留已有库迁移脚本。"""

import argparse
from pathlib import Path


ROOT = Path(__file__).resolve().parent
MODULES = (
    "un-core",
    "un-plat",
    "un-sys",
    "un-examine",
    "un-work",
    "un-dashboard",
    "un-app",
)


def main() -> None:
    parser = argparse.ArgumentParser(description="生成后端数据库首装与最终 SQL")
    parser.add_argument("--refresh-init", action="store_true",
                        help="仅在首装基线冻结前或隔离空库中更新首装定义")
    args = parser.parse_args()
    sources = [ROOT / f"{name}.sql" for name in MODULES]
    parts = ["-- examine2 当前业务表结构；由调用方选定独立空库后执行。\n"]
    for source in sources:
        if source.exists():
            parts.append(f"\n-- {source.stem} 业务模块\n{source.read_text(encoding='utf-8').rstrip()}\n")
    init = ROOT / "init.sql"
    if args.refresh_init or not init.exists():
        init.write_text(
            "-- examine2 首装基线；只在独立 unexamine_r16 库执行。\n"
            "CREATE DATABASE IF NOT EXISTS unexamine_r16 CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
            "USE unexamine_r16;\n" + "".join(parts), encoding="utf-8")
    if not (ROOT / "update.sql").exists():
        (ROOT / "update.sql").write_text(
            "-- 已有数据的后续结构变更及迁移步骤写在此处；当前首装无增量迁移。\n",
            encoding="utf-8",
        )
    (ROOT / "final.sql").write_text("".join(parts), encoding="utf-8")
    print(f"当前空库结构包含 {sum(source.exists() for source in sources)} 个模块 SQL；已合成 final.sql")


if __name__ == "__main__":
    main()
