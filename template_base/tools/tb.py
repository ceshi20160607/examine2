"""从最终资料和实际源码提供工程上下文，执行真实检查；不写入开发历史。"""

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


def read_json(path: Path) -> dict:
    """兼容用户维护的 UTF-8 配置文件。"""
    return json.loads(path.read_text(encoding="utf-8-sig"))


def workspace_path(root: Path, name: str) -> Path:
    """资料和源码路径必须归属于当前工程，不能借配置读取其他项目。"""
    path = (root / name).resolve()
    if path != root and root not in path.parents:
        raise ValueError(f"路径越出工作区: {name}")
    return path


def execution_context(root: Path, config: dict) -> None:
    """展示最终理解和工程流程，不恢复旧计划、状态或验收档案。"""
    for key in ("requirementSource", "requirementUnderstanding", "tableReference", "schemaReference"):
        path = workspace_path(root, config[key])
        if not path.is_file():
            raise FileNotFoundError(path)
        access = "开发方维护" if key == "requirementUnderstanding" else "原始资料，只读"
        print(f"{key} [{access}]: {path}")
    for key, relative in (("最终需求理解", config["requirementUnderstanding"]),
                          ("工程执行流程", config["framework"] + "/README.md")):
        path = workspace_path(root, relative)
        print(f"\n{key}\n{path.read_text(encoding='utf-8-sig')}")


def project_status(root: Path, config: dict) -> None:
    """只统计磁盘上的最终源码，不把文件数当成业务完成数。"""
    codegen = read_json(workspace_path(root, config["codegenConfig"]))
    backend = workspace_path(root, config["backend"])
    modules = []
    for target in codegen["targets"]:
        source = workspace_path(root, str((backend / target["sourceRoot"]).relative_to(root)))
        base = source.joinpath(*target["basePackage"].split("."))
        manage = base.parent / "manage"
        modules.append({
            "module": source.relative_to(backend).parts[0],
            "tablesDefined": len(target["tables"]),
            "entities": len(list((base / "entity").glob("*.java"))),
            "baseServices": len(list((base / "service").glob("I*Service.java"))),
            "businessServices": len(list(manage.rglob("*Service*.java"))) if manage.exists() else 0,
            "controllers": len(list(manage.rglob("*Controller.java"))) if manage.exists() else 0,
        })
    print(json.dumps({
        "modules": modules,
        "frontendPackageExists": (workspace_path(root, config["frontend"]) / "package.json").is_file(),
        "scope": "仅报告当前源码；不证明数据库、授权、HTTP 或完整产品已验收。",
    }, ensure_ascii=False, indent=2))


def execution_verify(root: Path, config: dict, target: str) -> int:
    """运行真实 Maven 检查并保留退出码，不以文档存在替代测试。"""
    maven = shutil.which("mvn.cmd") or shutil.which("mvn")
    if maven is None:
        raise FileNotFoundError("找不到 Maven，请配置 PATH")
    environment = os.environ.copy()
    java_home = config.get("javaHome")
    if java_home:
        java_path = Path(java_home)
        if not (java_path / "bin" / ("java.exe" if os.name == "nt" else "java")).is_file():
            raise FileNotFoundError(f"Java 工具链不可用: {java_path}")
        environment["JAVA_HOME"] = str(java_path)
        environment["PATH"] = str(java_path / "bin") + os.pathsep + environment.get("PATH", "")
    directory = workspace_path(root, config[target])
    pom = directory / "pom.xml"
    if not pom.is_file():
        raise FileNotFoundError(pom)
    goal = "test" if target == "generator" else "package"
    command = [maven, "-f", str(pom), goal, "-q"]
    print(f"实际执行 {target}: Maven {goal}", flush=True)
    result = subprocess.run(command, cwd=root, env=environment, check=False)
    if result.returncode == 0:
        print("目标工程检查通过；业务结果仍须通过实际入口验证。", flush=True)
    return result.returncode


def main() -> int:
    """所有命令只读取最终输入、检查当前源码或运行目标工程。"""
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="工程上下文与真实检查工具，不保存中间状态")
    parser.add_argument("command", choices=("project-status", "execution-context", "execution-verify"))
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--target", choices=("generator", "backend"), help="实际执行 Maven 检查的目标")
    args = parser.parse_args()

    workspace_file = Path(args.workspace).resolve()
    root = workspace_file.parent
    config = read_json(workspace_file)
    if args.command == "project-status":
        project_status(root, config)
    elif args.command == "execution-context":
        execution_context(root, config)
    else:
        if not args.target:
            parser.error("execution-verify 需要 --target generator 或 backend")
        return execution_verify(root, config, args.target)
    return 0


if __name__ == "__main__":
    sys.exit(main())
