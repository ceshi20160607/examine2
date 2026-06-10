# 开发模式治理规则

## 目标

开发模式不是“用户说一次继续，agent 写一批代码”。开发模式必须由 Orchestrator 管理阶段，由多角色发现问题，由 PM 裁决，由执行角色实现，由 test/validator/reviewer 复验，最后再给用户真实状态。

## 强制流程

每个开发批次必须按以下顺序执行：

1. Planner 确认当前期次、任务边界、输入、输出、依赖和可并行条件。
2. PM 明确本批次业务目标、验收口径、不可宣称事项和是否允许进入实现。
3. Backend、Frontend、UI/UX、DBA、Test、Validator、Reviewer 按角色提出问题。
4. PM 统一归类、裁决和分发问题。
5. PM 不能决策的问题写入 `docs/issues/user_questions.md`，owner 标为 `user`。
6. 执行角色只处理 PM 裁决后分配给自己的任务。
7. Test 执行场景验证，Validator 执行 clean build/package，Reviewer 审查交付质量。
8. PM 根据测试、构建、审查结果给出 `pass/rework/blocked`。
9. Orchestrator 更新 `.codex/state.json`、`docs/progress.md`、阶段文档并提交。

## 问题流转

开发中的问题也必须使用问题闭环，不能只在对话里口头处理。

| 层级 | 路径 | 说明 |
| --- | --- | --- |
| 角色原始问题 | `docs/issues/raw/development/{role}_wenti.md` | 各角色发现的问题 |
| PM 裁决问题 | `docs/issues/pm/development/{owner}_wenti.md` | PM 按责任方分发 |
| 责任方回复 | `docs/issues/replies/development/{owner}_reply.md` | 责任角色处理结果 |
| 提出方复核 | `docs/issues/verification/development/{reviewer}_verification.md` | 原提出方确认 |
| 用户问题 | `docs/issues/user_questions.md` | PM 无法决策的问题 |

每个 issue 必须包含：`issueId`、`stage`、`raisedBy`、`owner`、`problem`、`impact`、`pmDecision`、`actionRequired`、`round`、`status`、`verifier`、`closeCondition`。

## PM 禁止行为

- 未经 test/validator/reviewer 复验，不得宣称阶段完成。
- 后端接口链路通过，不得等同完整系统可给用户使用。
- 前端只有 typed SDK、PageModel、路由壳或构建通过，不得等同真实业务 UI 完成。
- 缺少 UI/UX 设计冻结时，不得把“接口能调用、按钮能点击”判定为正常用户可用的前端。
- 用户没有要求暂停时，PM/Orchestrator 不得等待用户一句句“继续”才推进当前已批准期次。
- PM 无法决策的问题不得假装关闭，必须写给用户处理。

## Orchestrator 禁止行为

- 不得跳过角色问题发现和 PM 裁决直接进入实现。
- 不得只跑前端页面冒烟就宣称项目功能通过。
- 不得只跑后端 API 链路就宣称完整前端可用。
- 不得在缺少信息架构、交互流程、页面线框和状态设计时，让 frontend 直接按 API 堆页面并宣称可用。
- 不得在 agent 无法寻址、未登记或未返回结果时假装 agent 已工作。
- 子 agent 不可用时，必须在进度文档记录降级原因，并由 Orchestrator 本地补齐或重新调度。

## 自动验证规则

每次交付前必须主动执行验证，不依赖用户提醒。

后端改动至少执行：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn.cmd -pl examine-web -am test
```

前端改动至少执行：

```powershell
npm.cmd run build
```

完整系统或部署口径必须额外执行：

- 本机启动后端和前端。
- 登录真实账号。
- 跑对应业务链路。
- 记录浏览器或接口验证结果。
- 更新 `docs/test_runs/`、`docs/build_report.md`、`docs/progress.md`。
- 只有 PM、frontend、test、validator、reviewer 均 pass 且 `docs/review.json.fullProjectDeployable=true` 后，才允许生成最终部署包；未完成阶段禁止打包，避免把半成品误交付或浪费部署验证成本。

## Agent 会话规则

Orchestrator 调度 agent 后必须记录：

- `agentId`
- 角色
- 分配任务
- 负责文件范围
- 当前状态
- 最后输出
- 是否可继续寻址

记录位置：`.codex/state.json.agent_sessions` 或 `docs/progress.md`。

如果 `send_input/wait_agent` 返回 agent not found：

1. 立即停止依赖该 agent 的交付判断。
2. 在进度文档记录 agent 不可用。
3. 由 Orchestrator 重新调度或本地接管。
4. 不得把该 agent 的任务计为完成。

## 当前后续开发口径

当前后端核心功能链路已经通过本机接口验证，但前端完整系统还未全部完成。后续阶段应按前端真实业务可用化推进：

- P9：系统管理域 UI，成员、部门、系统角色、字典。
- P10：应用模块配置与运行台 UI。
- P11：流程、文件、导出、OpenAPI、审计运维 UI。
- P12：完整浏览器 E2E、部署包、PM 最终验收。
