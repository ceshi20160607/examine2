"""读取并更新工作区断点；产品需求和业务规则不放在此通用工具中。"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def read_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def workspace_path(root: Path, name: str) -> Path:
    path = (root / name).resolve()
    if path != root and root not in path.parents:
        raise ValueError(f"路径越出工作区: {name}")
    return path


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description="简短工程断点工具")
    parser.add_argument("command", choices=("project-status", "execution-context", "execution-verify"))
    parser.add_argument("--workspace", required=True)
    parser.add_argument("--step", help="已真实验证的步骤标识")
    parser.add_argument("--evidence", help="包含真实验证结果的工程内文件")
    args = parser.parse_args()

    workspace_file = Path(args.workspace).resolve()
    root = workspace_file.parent
    config = read_json(workspace_file)
    state_path = workspace_path(root, config["state"])
    state = read_json(state_path)

    if args.command == "project-status":
        print(json.dumps({
            "phase": state["phase"], "currentStep": state["currentStep"],
            "status": state["status"], "database": state["database"],
            "tableCount": state["tableCount"], "frontend": state["frontend"],
            "userBackendAccepted": state["userBackendAccepted"],
            "verifiedChecks": len(state["verifiedChecks"]), "next": state["next"],
        }, ensure_ascii=False, indent=2))
        return

    if args.command == "execution-context":
        for key in ("requirement", "tableReference", "plan", "state"):
            path = workspace_path(root, config[key])
            if not path.is_file():
                raise FileNotFoundError(path)
            print(f"{key}: {path}")
        print(f"currentStep: {state['currentStep']} ({state['status']})")
        print(f"next: {state['next']}")
        return

    if not args.step or not args.evidence:
        parser.error("execution-verify 需要 --step 和 --evidence")
    evidence_path = workspace_path(root, args.evidence)
    if not evidence_path.is_file() or not evidence_path.read_text(encoding="utf-8").strip():
        raise ValueError("验证证据文件不存在或为空")
    if any(item["step"] == args.step for item in state["verifiedChecks"]):
        raise ValueError("该步骤已有验证记录")
    state["verifiedChecks"].append({
        "step": args.step,
        "evidence": str(evidence_path.relative_to(root)).replace("\\", "/"),
        "verifiedTime": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    })
    temporary = state_path.with_suffix(".json.tmp")
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(state_path)
    print(f"已记录真实验证: {args.step}；整体状态仍为 {state['status']}")


if __name__ == "__main__":
    main()
