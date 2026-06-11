# PKG-002 P13 最终部署包刷新

- taskId：PKG-002
- 标题：P13 最终部署包刷新
- 负责角色：validator
- 所属大任务/模块：P13-usability-rework / 部署包
- 任务类型：test
- 状态：pending

## 目标

仅在 P13 review 通过后刷新最终部署包，确保包内前端、后端、部署说明和 P13 验收证据一致。

## 输入文件

- `docs/review.json`
- `docs/test_runs/p13-usability-e2e.md`
- `docs/build/p13-clean-build.md`
- `frontend/dist/`
- `backend/examine-web/target/unexamine.jar`
- `docs/deploy/nginx-deploy.md` 或当前部署说明

## 输出文件或输出目录

- `dist/unexamine-full-deploy-{P13时间戳}.zip`
- `dist/unexamine-full-deploy-{P13时间戳}.tar.gz`
- `docs/phases/P13-usability-rework-acceptance.md`（由 PM/Orchestrator 在验收阶段维护，本任务只把其列为打包前置文档要求）

## 详细工作内容

1. 检查 `docs/review.json.status=pass`，且 P13 无 P0/P1 阻塞问题。
2. 使用 VAL-009 生成的最新 `frontend/dist/` 和 `backend/examine-web/target/unexamine.jar` 组装部署包。
3. 包内必须包含前端静态文件、后端 jar、启动脚本、nginx/部署说明、P13 测试记录、P13 构建记录和最新 `docs/review.json`。
4. 校验 zip 与 tar.gz 的关键文件清单，Linux 包需保留 `start.sh` 可执行语义。

## 完成状态定义

- 新部署包生成并记录路径、大小、关键文件清单。
- 包内证据对应 P13，而不是 P12 旧证据。
- 状态保持 `pending`，由执行 agent 完成后更新。

## 验收标准

- `frontend/index.html`、`frontend/assets/*`、`backend/unexamine.jar`、启动脚本和部署说明存在。
- P13 test/build/review 证据在包内可查。
- 未通过 REV-009 时不得执行本任务。

## 测试/自检要求

- 解包检查关键文件。
- 检查包内 `docs/review.json` 与工作区最新审查结论一致。

## 依赖任务

- REV-009

## 可并行关系

- 不可并行；这是 P13 串行收口任务。

## 不允许事项

- 不用 P12 最终包冒充 P13 包。
- 不跳过 TEST-011、VAL-009、REV-009。
- 不修改前后端业务代码。

## 具体实现范围

仅限最终包刷新和包清单核验。

## 不做事项

不进行新的功能开发，不修改 API、DB 或业务代码。

## 单元测试或自检要求

包清单核验和解包核验必须记录。

## 交给 test 的集成测试入口

P13 包生成后，由 PM/Orchestrator 根据 `docs/phases/P13-usability-rework-acceptance.md` 做试部署或交付验收。
