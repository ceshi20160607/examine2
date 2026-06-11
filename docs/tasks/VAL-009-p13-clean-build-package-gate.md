# VAL-009 P13 Clean Build 与打包闸门

- taskId：VAL-009
- 标题：P13 Clean Build 与打包闸门
- 负责角色：validator
- 所属大任务/模块：P13-usability-rework / 构建验证
- 任务类型：test
- 状态：pending

## 目标

在 TEST-011 通过后执行前端 clean build、后端 package 和契约同步检查，确认 P13 返工产物可进入审查，但不提前生成最终部署包。

## 输入文件

- `docs/test_runs/p13-usability-e2e.md`
- `docs/test_report.md`
- `frontend/`
- `backend/`
- `docs/api.md`
- `frontend/docs/api-contract-map.md`

## 输出文件或输出目录

- `docs/build/p13-clean-build.md`
- `docs/build_report.md`
- `frontend/dist/`
- `backend/examine-web/target/unexamine.jar`

## 详细工作内容

1. 清理旧 `frontend/dist/` 和前端增量缓存后执行前端生产构建。
2. 执行后端 clean package 或明确记录后端无改动但 jar 可复验的 package 命令。
3. 检查 API 错误码、枚举、状态值、字段类型与 `frontend/docs/api-contract-map.md` 未因 P13 返工发生漂移。
4. 汇总构建命令、产物路径、关键文件清单和失败日志摘要。

## 完成状态定义

- `docs/build/p13-clean-build.md` 和 `docs/build_report.md` 记录真实命令、结果和产物路径。
- P13 未经 REV-009 通过前不生成 `dist/unexamine-full-deploy-*` 最终包。
- 状态保持 `pending`，由执行 agent 完成后更新。

## 验收标准

- 前端 clean build 生成 `frontend/dist/index.html` 和 assets。
- 后端 package 生成 `backend/examine-web/target/unexamine.jar`。
- 契约同步检查未发现 P13 返工造成的 SDK/map 漂移。

## 测试/自检要求

- 记录实际 Node/npm、JDK、Maven 路径或版本。
- 若 build 失败，`docs/build_report.md` 必须给出 target，且阻止 REV-009 pass 与 PKG-002。

## 依赖任务

- TEST-011

## 可并行关系

- 不与 REV-009 并行；REV-009 需要读取本任务输出。
- 不与 PKG-002 并行；PKG-002 只能在 REV-009 pass 后执行。

## 不允许事项

- 不用 `tsc --noEmit` 替代前端生产构建。
- 不用旧 `dist/` 或旧 jar 作为 P13 构建通过证据。
- 不生成最终部署包。

## 具体实现范围

仅限 P13 构建验证、契约同步检查和构建报告。

## 不做事项

不修改前端或后端业务代码，不修改 API 契约。

## 单元测试或自检要求

前端 clean build、后端 package、契约同步检查三项均需记录。

## 交给 test 的集成测试入口

`docs/build/p13-clean-build.md` 和 `docs/build_report.md` 交给 REV-009 作为审查输入。
