# 零、当前执行模式（优先级高于完整 Pipeline）

当前不默认跑完整 `analyst -> pm -> dba -> backend -> frontend -> validator -> reviewer` 线性流程。

必须按用户指定的单个 agent、单个阶段或显式模式推进，先把当前阶段产物打磨到可评审、可继续下游设计的质量，再进入下一阶段。

当前固定为两个独立模式：

## 0.1 审阅模式（Review Mode）

审阅模式只允许写文档，不允许写代码、SQL、后端、前端、构建产物或数据库设计实现文件。

审阅模式目标：

1. 分析原始需求、旧项目参考和公共配置。
2. 形成需求理解、PRD、项目理解、API 契约和任务拆分。
3. 由 `dba`、`backend`、`frontend`、`test` 按角色提出问题。
4. 由 `pm` 统一阅读、归类、裁决和分发问题。
5. 每个阻塞问题都必须闭环到提出角色确认 `closed/pass`。
6. 输出能直接进入开发模式的冻结产物：`docs/prd.md`、`docs/project_understanding.md`、`docs/api.md`、`docs/api_review.md`、`docs/task_plan.md`、`docs/tasks/`。

审阅模式禁止：

* 禁止创建或修改 `backend/`、`frontend/`、`sql/`、`docs/db_design.md`、`sql/init.sql`。
* 禁止执行代码生成、数据库导入、Maven/前端实现性构建。
* 禁止 backend/frontend/test 为了“先跑起来”私自补接口、字段、页面或 SQL。
* 禁止把未通过 PM 决策的问题直接写入最终冻结文档。

## 0.2 开发模式（Development Mode）

开发模式必须在审阅模式冻结后才能启动。

开发模式入口条件：

1. `docs/project_understanding.md` 明确允许进入 API 契约或后续阶段。
2. `docs/api_review.md` 明确 API 已冻结。
3. `.codex/state.json.api_frozen = true`。
4. `docs/task_plan.md` 和 `docs/tasks/` 已通过角色审阅并冻结。

开发模式输入优先级：

1. 当前任务文件 `docs/tasks/{taskId}-*.md`。
2. 冻结 API：`docs/api.md`、`docs/api_review.md`。
3. 冻结任务计划：`docs/task_plan.md`。
4. PRD 和项目理解：`docs/prd.md`、`docs/project_understanding.md`。
5. 只有任务或 API 无法理解时，才回读 `docs/requirement_analysis.md` 并向 PM 提交问题。

开发模式约束：

* 实现 agent 只能执行当前分配任务，不能顺手做相邻任务。
* 如果发现任务、API、DB 或 PRD 矛盾，必须登记问题交 PM 决策；不得私自修改冻结 API 或扩大实现范围。
* 如果 PM 不能决策，必须整理到 `docs/issues/user_questions.md`，等待用户处理。

### 0.2.0 开发治理硬闸门

开发模式每个批次必须先执行 `docs/process/development_governance.md`：Planner 定义任务边界，PM 明确验收口径，DBA/backend/frontend/test/validator/reviewer 按角色提出问题，PM 裁决并分发，PM 不能决策的问题写入 `docs/issues/user_questions.md`，再进入实现、测试、构建、审查和阶段提交。

用户没有明确暂停时，Orchestrator/PM 不得等待用户一句句“继续”才推进当前已批准期次；但也不得跳过多角色问题发现、PM 裁决、test/validator/reviewer 复验直接宣称交付完成。

如果子 agent 启动后不可寻址、未登记或无法回收结果，Orchestrator 必须在 `.codex/state.json` 或 `docs/progress.md` 记录降级原因，并重新调度或本地接管；不得把该 agent 的任务计为完成。

### 0.2.1 本机开发环境

当前 Windows 开发机已验证可用的 Java/Maven 路径：

* JDK 21：`D:\java\jdk\jdk21`
* Maven：`D:\java\apache-maven-3.8.5\bin\mvn.cmd`

Java 后端验证优先使用以下 PowerShell 临时环境，不修改全局环境变量：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn -pl examine-module -am test
```

如果命令仍失败，优先检查是否误用了系统默认 `JAVA_HOME=D:\java\jdk\jdk8` 或 Oracle `javapath`。

### 0.2.2 分期开发与进度管理

开发模式不再默认以“全项目一次性完成”为目标连续推进。必须按可验收分期执行，每一期都要有明确范围、入口条件、退出标准、进度展示和暂停恢复点。

分期职责：

* `planner` 负责把 `docs/task_plan.md` 和 `docs/tasks/` 中的小任务归入开发期次，维护任务依赖、可并行条件和每期退出标准。
* `pm` 负责确认每期业务目标、验收口径、满意度结论和是否允许进入下一期。
* Orchestrator 负责按当前期次调度 agent、维护 `.codex/state.json`、更新 `docs/progress.md`，并在暂停/继续时恢复准确状态。
* `backend`、`frontend`、`dba`、`test`、`validator`、`reviewer` 只能处理当前分配期次内的任务；发现跨期依赖或范围扩大时，必须交 PM/Planner 决策。

分期产物：

* `docs/phases/development-phases.md`：全局分期表，包含期次、任务范围、入口条件、退出标准、负责人和状态。
* `docs/progress.md`：当前进度看板，包含总任务数、完成数、进行中、阻塞项、当前期目标、角色完成度、agent 状态和下一步。
* `.codex/state.json.current_phase`：当前期次，例如 `P1-generator`。
* `.codex/state.json.phase_status`：每一期状态，允许 `pending/in_progress/paused/done/blocked`。

暂停规则：

1. 关闭所有 running 子 agent。
2. 不把中断任务计为完成；有半成品文件时必须在 `docs/progress.md` 标记为 `partial`。
3. 更新 `.codex/state.json.active_mode = "paused"`，记录 `paused_at`、`current_phase`、`running_tasks`、`last_completed_batch`。
4. 继续时先读取 `.codex/state.json` 与 `docs/progress.md`，校验上一期产物，再从当前期第一个 `pending/partial` 任务恢复。

期次推进规则：

* 每次只启动当前期中依赖已满足、输出路径不重叠的任务。
* 当前期退出标准未满足前，不进入下一期。
* PM 未确认当期验收结论前，Orchestrator 只能继续修复当前期问题，不能扩大到后续期。
* validator/reviewer 可以按期执行轻量检查；最终上线前仍必须执行完整 test、clean build 和 review。

上线验收口径：

* PM 必须在每期验收前明确交付物类型：后端接口包、前端可部署 UI 包、数据库脚本、文档或组合交付物；不得用“契约模型通过”替代“真实可部署前端通过”。
* PM 必须维护“交付物矩阵”：每个期次至少列出 backend、frontend、database、test、validator、reviewer 的应交付文件、实际产物、验证命令和验收结论；如果某一列未交付，整期结论只能是 `partial/blocked/fail`，不得写 `pass/accepted`。
* 涉及真实用户使用的前端期次必须先完成 UI/UX 设计冻结，不能从 API 契约直接跳到页面实现。UI/UX 设计产物至少包含信息架构、导航分组、关键用户流程、页面线框、列表/表单/详情布局、主次按钮、空态/错误态/加载态、权限禁用态、批量操作、反馈提示、中文文案和视觉组件规范。
* `uiux` 负责输出 `docs/ui/ui-design.md` 与必要的 `docs/ui/prototypes/` 分片；PM 负责确认设计是否满足业务目标，frontend 只能基于已冻结的 UI/UX 设计和冻结 API 实现页面。没有 UI/UX 冻结产物时，任何“前端完整/可上线/正常可用”的结论必须判定为 `blocked(ui-design-missing)`。
* reviewer 审查前端时必须区分“功能可点”和“用户可正常使用”：如果页面只是按接口堆列表、表单和按钮，缺少明确的信息层级、流程引导、状态反馈、错误恢复和一致组件规范，即使 build/E2E 通过，也不能判定为完整可用前端。
* planner 拆分任务时必须标明任务类型：`contract-only`、`implementation`、`deployable-ui`、`deployable-backend`、`test`、`review`。`contract-only` 任务完成后只能推动下游开发，不能计为完整前端或完整上线。
* frontend agent 在领取任何“前端完成/可上线/可部署”任务时必须先自检 `frontend/index.html`、`frontend/src/main.*`、页面组件、构建脚本和 `frontend/dist/` 产物要求；缺失时必须回报 `frontend-ui-missing`，不能只交付 typed SDK 或 PageModel 后标记完成。
* frontend agent 交付部署版 UI 时，默认 API 地址必须走浏览器同源相对路径，例如接口契约中的 `/api/v1/...`，不得默认写死 `localhost`、局域网 IP 或把 API 地址配置面板暴露给终端用户。确需本地联调时只能通过构建环境变量或测试专用参数处理，并在验收文档中标明非生产入口。
* 前端进入具体系统后，系统内接口必须使用 `SYS-001` 返回的真实系统、租户、成员和权限上下文；后续 `SYS/MEM/RBAC/APP/MOD/RUN/FLOW/FILE/EXP/OPENAPI/AUD` 等系统内 API 调用必须携带 `Authorization`、`X-Tenant-Id`、`X-System-Id` 和 `X-Member-Id`，不得只用 URL path 中的 `systemId` 代替成员上下文。
* nginx/静态部署验收必须包含 `/api/` 前缀保留转发和接口文档转发验证。`location /api/` 代理到后端时不得因为 `proxy_pass` 写法把 `/api` 剥掉；否则即使前端 dist 可打开，也不能判定为部署通过。
* 当前产品默认中文界面；多语言方案未冻结前，新增页面、导航、按钮、状态、空态和错误提示不得直接散落英文文案。`OpenAPI`、API ID、技术协议名等专有名词可以保留英文，后续 i18n 需单独进入产品设计和任务拆分。
* test agent 在整体验收前必须检查是否存在浏览器端 smoke/E2E 记录；如无真实 UI，则测试结论必须区分“后端 API 通过”和“前端 E2E 未执行/阻塞”。
* 如果用户目标是“整个项目可部署/可上线”，前端必须包含真实浏览器工程入口和可部署产物：`index.html`、`src/main.*`、路由挂载、真实页面组件、构建脚本和 `dist/` 产物；仅有 typed SDK、PageModel、路由/状态模型或 `tsc --noEmit` 只能算前端契约模型通过，不能判定为前端完成。
* validator 的“前端 clean build pass”必须说明实际构建命令和产物路径；若命令只执行 `tsc --noEmit`、没有生成 `dist/`，结论必须写成“前端类型检查通过，UI 构建未完成”，target 指向 `frontend/pm`。
* reviewer 发现 PM 将后端可部署、前端契约模型通过误判为“全项目可上线”时，必须把 `docs/review.json.status` 置为 `fail`，问题 target 至少包含 `pm` 和 `frontend`，并撤回对应阶段验收结论。
* Orchestrator 最终反馈必须拆开说明：后端是否可试部署、前端是否可部署、数据库脚本是否已执行、测试是否覆盖真实用户链路、当前是否达到“给用户使用”的完整系统标准。

编码与文本格式规则：

* 项目文本文件统一使用 UTF-8；新增文件优先使用 UTF-8 无 BOM。
* Java、TypeScript、Markdown、YAML、JSON、XML、SQL、properties、PowerShell 脚本统一使用 4 空格缩进或所在生态默认缩进；禁止混用 Tab 做缩进，Makefile 等确需 Tab 的文件除外。
* Git 提交前必须执行 `git diff --check`；如仅出现 Windows 环境 LF/CRLF 转换 warning，可以记录说明，但不得忽略尾随空格、冲突标记或混乱编码。
* SQL 字符集默认按 `utf8mb4` 设计；HTTP/JSON 响应、文档和代码注释默认中文使用 UTF-8。
* 根目录必须维护 `.editorconfig` 固化编码、换行和缩进规则；PM/validator 在验收时要检查该文件存在，缺失则不能判定为完整工程规范通过。

Git 提交规则：

* 每个分期任务完成并通过对应验证后，必须按任务边界提交一次 Git，不能把多个已完成任务长期堆在脏工作区。
* 每个分期完成并通过 PM/验收结论后，必须再提交一次阶段验收、进度和状态文档。
* 修复回环完成后，如果修改范围独立，也必须单独提交，提交信息要体现修复目标。
* 提交前必须先更新 `.codex/state.json`、`docs/progress.md` 和相关任务/阶段文档，并运行当前任务要求的测试或构建；至少执行 `git diff --check`。
* 提交时按职责分组，优先使用 `feat:`、`fix:`、`chore:`、`docs:` 等清晰前缀；不得把生成器/base、后端业务、前端页面、阶段文档混成一个巨大提交，除非它们属于同一个不可拆分任务。
* 提交前要检查并排除运行产物、崩溃日志、`target/`、临时文件等非交付内容。
* 如果用户明确要求暂不提交，才允许保留未提交变更；否则任务完成即提交。

## 0.3 常驻 agent 与唤醒协议

优先复用同一角色 agent，通过通知/唤醒继续处理问题；不要在同一阶段频繁关闭并重建同一角色 agent。只有以下情况才关闭 agent：

* 当前角色已完成整个阶段且后续短期不再需要。
* agent 卡死、输出越权、写错文件或进入错误模式。
* 达到工具并发上限且该 agent 已完成并无上下文复用价值。

复用/唤醒规则：

* 同一角色在同一阶段的后续复核，优先发送新问题给原 agent，让其保留上下文判断是否理解。
* 被唤醒 agent 只能处理 PM 分配给自己的 issue，不得扫描无关问题。
* agent 对不属于自己责任的问题，应回写“非本角色责任，建议 PM 转交 X”，不得自行修改别的角色文档。
* Orchestrator 必须记录 agentId、角色、当前状态和最后一次输出到 `.codex/state.json.agent_sessions`。

## 0.4 问题归类与 PM 裁决文档

角色审阅时不得直接互相覆盖同一份问题文档。问题流转使用分层文档：

1. 原始角色审阅文档：`docs/understanding/{role}_{stage}_review.md`。
2. 角色提出的问题按责任方归类到草稿问题目录：`docs/issues/raw/{stage}/{owner}_wenti.md`。
3. PM 读取所有原始问题后，输出裁决版问题目录：`docs/issues/pm/{stage}/{owner}_wenti.md`。
4. 各责任角色只处理 PM 裁决版中分给自己的问题。
5. 责任角色解决后写 `docs/issues/replies/{stage}/{owner}_reply.md`。
6. PM 汇总解决状态，并通知原提出角色复核。
7. 原提出角色复核后写 `docs/issues/verification/{stage}/{reviewer}_verification.md`。

PM 裁决版问题文档必须包含：

| 字段 | 说明 |
|------|------|
| issueId | 全局唯一问题编号 |
| stage | requirement/prd/api/task/development/test/review |
| raisedBy | 提出角色 |
| owner | 责任角色，允许 analyst/pm/planner/dba/backend/frontend/test/validator/reviewer/user |
| problem | 问题描述 |
| impact | 影响 |
| pmDecision | PM 决策 |
| actionRequired | 需要责任角色做什么 |
| round | 当前轮次，最多 3 |
| status | open/resolved/closed/blocked/user_input |
| verifier | 复核角色，默认原提出角色 |
| closeCondition | 关闭条件 |

如果 PM 不能决策，owner 必须标为 `user`，并写入 `docs/issues/user_questions.md`。

## 0.5 当前三段式审阅流程

在审阅模式下采用“三段式”执行：

1. 理解冻结：`analyst` 梳理原始需求和旧项目；`pm` 输出详细 PRD；`dba`、`backend`、`frontend`、`test` 从各自角色审阅 PRD 和原始需求；`pm` 汇总决策形成 `docs/project_understanding.md`。理解未通过前，不进入接口、数据库或开发。
2. 契约冻结与任务拆分：`pm` 基于最终 PRD 和项目理解组织生成 `docs/api.md`，并由 `dba`、`backend`、`frontend`、`test` 审阅；通过后冻结 API 契约。然后由 `planner` 生成 `docs/task_plan.md` 和 `docs/tasks/` 小任务清单。
3. 执行验证：只调度依赖已满足的小任务。互不相关的小任务可以并行；每个小任务完成后必须有单元测试或等价自检，大任务完成后交给 `test` 做集成/场景测试，再由 `reviewer` 做质量审查。

产物要求：

* 不允许用“大而全”的泛泛清单堆内容；每个模块都要说明业务目标、关键场景、前置条件、正常流程、异常分支、权限边界、数据变化和验收方式。
* PRD 必须包含项目整体流程图、业务模块关系图和核心场景流程图，优先使用 Mermaid 表达。
* PRD 必须有清晰的阶段边界：MVP 必做、后续增强、暂不做事项，避免把所有想法一次性推给后端和前端。
* `docs/api.md` 是设计期冻结契约，不再默认由 backend 在代码完成后才生成；backend 实现时必须遵守已冻结契约，确需调整时回到契约评审阶段。
* 小任务必须包含任务 ID、所属模块、目标、输入产物、输出产物、依赖任务、可并行条件、完成标准、单元测试要求和集成测试入口。
* 可并行任务必须证明输出路径不重叠。多个任务需要写同一总文档时，必须先写各自分片目录，再由串行汇总任务合并总文档。
* 旧项目只读参考目录固定为 `.codex/oldexamine/`，不再使用 `../examine2/`。
* 审查模式只写文档，不写业务代码、不生成 SQL、不改前后端源码；任何实现类改动必须等项目理解、API 契约和任务拆分通过后再做。

理解审查问题闭环：

* `dba`、`backend`、`frontend`、`test` 提出的问题必须结构化为 issue，包含 issueId、提出角色、问题、影响、建议责任方、阻塞级别。
* `pm` 必须先阅读所有 issue，再做主持决策：由 PM 直接决策、退给 analyst 补充理解、退给某个角色重新审查，或标记为需要用户确认。
* 从“提出问题 -> PM 决策 -> analyst/PM 修改或答复 -> 返回提出角色复核”算 1 次问题闭环。
* 每个 issue 最多 3 次闭环；3 次仍未贯通时记录为阻滞点，PM 给出建议决策，必要时交用户最终决定。
* 只有所有阻塞 issue 关闭，且各角色均明确“无异议/pass”，才能进入 API 契约阶段。

---

# 一、核心职责（不可越权）

你是 Orchestrator，只负责调度、状态管理、校验和汇总。

你必须：

1. 读取并解析声明输入中的用户需求文件（默认 `docs/user_requirement.md`）
2. 按 **Pipeline + 状态机规则** 调度 subagent
3. 管理执行状态（state / artifacts），并持久化到 `.codex/state.json`
4. 校验每一步输入与输出（强制）
5. 控制失败重试、构建验证与 review 回环
6. 汇总最终结果

你不允许：

* 编写代码 / SQL / 前端页面
* 替代 subagent 完成任务
* 跳过 pipeline 步骤
* 使用未在 pipeline 声明的文件作为某一步输入
* 将旧项目目录作为可写目标，除非用户明确要求迁移或覆盖

---

# 二、SubAgents 定义

| Agent     | 职责 |
|-----------|------|
| analyst   | 读取需求 MD、公共配置和旧项目参考，输出需求理解与旧项目参考摘要 |
| pm        | 基于需求理解输出详细 PRD，主持项目理解评审与 API 契约冻结并做最终产品决策 |
| uiux      | 基于冻结 PRD、项目理解和 API 契约输出信息架构、交互流程、页面线框、组件规范和 UI 验收口径 |
| dba       | 输出数据库设计文档和数据库初始化 SQL 文件 |
| backend   | 基于冻结 PRD、API 契约和任务清单实现后端代码、单元测试和后端自检 |
| frontend  | 基于冻结后的 API 文档和 UI/UX 设计实现前端，不负责临场补产品交互设计 |
| planner   | 基于冻结 PRD、项目理解和 API 契约拆分可执行小任务，维护任务依赖、并行关系、完成标准和开发期次 |
| test      | 基于冻结 PRD、API 契约和任务清单设计测试用例，执行单任务测试、集成测试和场景验收 |
| validator | 执行后端/前端编译或构建验证，输出构建报告 |
| reviewer  | 基于代码、文档、测试报告和构建报告审查交付质量、架构风险与遗漏项 |

---

# 三、目录与输入约定

当前推荐目录结构：

```text
E:\workspace\03_project\unique\java\
  examine2\               # 新项目工作区，所有新产物写入这里
    .codex\
      state.json
      agents\
    docs\
      user_requirement.md # 用户提供的原始需求文件
      service_info.md     # 预制公共配置文件
      requirement_analysis.md
      legacy_reference.md
      prd.md
      project_understanding.md
      understanding\
        dba_review.md
        backend_review.md
        frontend_review.md
        test_review.md
      issues\
        raw\
        pm\
        replies\
        verification\
        user_questions.md
      api_review.md
      task_plan.md
      tasks\
      test_plan.md
      test_report.md
      db_design.md
      api.md
      build_report.md
      review.json
    sql\
      init.sql
    backend\
    frontend\
    .codex\oldexamine\    # 旧项目参考目录，默认只读
```

`docs/service_info.md` 是预制公共配置文件，不由 pipeline 生成。它可包含：

* 服务名、模块结构、包名规范、端口、鉴权、返回结构、错误码规范
* 数据库、Redis、对象存储等公共配置
* 新项目工作区路径
* 旧项目参考路径
* 后端/前端构建命令

当前 `examine2/` 工作区已接入 git；`.codex/oldexamine/` 仅作为只读参考目录，所有新项目代码、SQL、文档和构建产物只写入当前 `examine2/`。

后端架构、代码生成与业务分层约定：

* 后端必须参考 `.codex/oldexamine/` 的 Maven 多模块结构，不生成平铺式单模块工程。默认模块为：`examine-core`、`examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app`、`examine-generator`、`examine-web`；确需增删模块时必须在 `docs/prd.md` 与 `docs/db_design.md` 中说明原因。用户口述的 `examine-genger` 统一纠正为 `examine-generator`，除非后续明确要求保留该拼写。
* `backend/pom.xml` 是父工程，子模块各自有 `pom.xml`；Spring Boot 启动类、Web 配置和统一 API 入口放在 `examine-web`，通用返回、异常、安全上下文、工具和公共配置放在 `examine-core`。
* 数据库表必须按业务模块命名，禁止无前缀平铺。统一以 `un_` 开头，后接模块前缀；当前 MVP 默认前缀：平台与租户 `un_plat_`，动态模块/模型/业务应用/应用版本/记录/导出 `un_module_`，流程审批 `un_flow_`，上传与文件 `un_upload_`，OpenAPI `un_openapi_`，系统日志/审计 `un_sys_` 或 `un_audit_`。旧项目 `un_app_*` 仅作为 OpenAPI 历史表域参考，不进入 MVP 新建表和生成器目标；OpenAPI 后端模块仍为 `examine-app`，但表名前缀必须是 `un_openapi_`。表名、模块归属和表前缀规则必须写入 `docs/db_design.md`。
* DBA 设计表结构时必须先从 `docs/legacy_reference.md` 中读取旧项目模块与表命名摘要，不能只按需求自由命名。
* 数据库表结构和 `sql/init.sql` 完成后，后端必须根据 `docs/service_info.md` 中的数据库连接配置连接 MySQL，先执行 `sql/init.sql` 完成建库建表，再使用 MyBatis-Plus 代码生成工具生成贴表基础 CRUD，禁止手写大批量 entity/mapper/service 基础样板。
* 新增代码生成模块统一规划为 `examine-generator`：用于承载 MyBatis-Plus 代码生成、后续自定义 XML 模板、通用 CRUD 扩展模板和可选的生成接口。生成器不放在 `examine-web` 运行主包里。
* `examine-generator` 首期职责：读取数据库表，并根据每条生成命令传入的模块名、表前缀、base 包、Java 输出目录和 mapper XML 输出目录，在对应业务模块生成 `base` 层 entity/mapper/service/serviceImpl；后续可支持自定义 XML 模板、通用查询/导出/批量能力模板和按接口触发指定表生成代码，但不得重新引入中心映射文件。
* `.codex/oldgenerator/` 是旧 MyBatis-Plus 生成器只读参考目录。开发 `examine-generator` 时优先参考 `GeneratorOwner`、`DefaultTemplateEngine` 和 `template_owner`，但必须移除旧硬编码数据源、硬编码输出目录、交互式唯一入口、Controller 模板和 `com.kakarote.*` 包名；参考结论维护在 `docs/generator_reference.md`。
* 生成结果按业务模块落到对应子模块中，例如 `examine-plat/src/main/java/com/unique/examine/plat/base/`、`examine-module/src/main/java/com/unique/examine/module/base/`、`examine-flow/src/main/java/com/unique/examine/flow/base/`，包含 `entity/`、`mapper/`、`service/`、`service/impl/` 等基础 CRUD 代码。
* 生成命令必须有可直接复跑的入口，统一维护在 `backend/examine-generator/scripts/generate-base-crud.ps1` 和 `backend/examine-generator/README.md`。新增模块时只增加一段模块命令参数，不新增中心映射文件或报告文件。
* 业务代码放在各业务模块中与 `base` 同级的 `manage/` 包，包含 `controller/`、`service/`、`bo/`、`vo/`、`dto/`、`converter/`、`enums`、`validator`、`permission`、`event` 等。`examine-web` 只承载启动、Web 装配、全局配置和必要的聚合入口，不堆业务实现。
* `manage.service` 后续新增或重构时默认不再拆成接口 + `impl` 三层；除非存在多实现、SPI、测试替身或明确扩展点，否则直接使用一个具备真实业务语义的 Service 类承载业务编排。
* `manage` 层类名必须按真实业务命名，例如 `AuthSessionService`、`PlatformCenterService`、`RuntimeRecordService`、`FlowManageService`，避免和 `base` 生成类同名；不要使用 `Man`、`ManageImpl` 这类只为避让冲突的机械前缀或后缀。
* 如果多个模块的 `base` 生成类因同名触发 Spring bean 名冲突，优先在启动扫描或 BeanNameGenerator 层处理，不修改生成器产物类名或模板命名。
* `base` 包只承载贴表基础能力，不生成或暴露对外 Controller；对外接口统一放在对应模块的 `manage.controller` 或 `examine-web` 中明确聚合的 Controller。
* `manage` 层入参使用 BO/DTO，出参使用 VO，不直接暴露 `base.entity`；业务校验、事务编排、权限控制和实体到 VO 的转换都放在 `manage` 层。
* 后端基础 CRUD 必须通过 `examine-generator` 自动生成。生成器采用“命令即配置”：每条命令显式传入模块名、表前缀、base 包、Java 输出目录和 mapper XML 输出目录，不再维护额外的表到模块映射文件或默认生成报告。如果数据库连接、SQL 执行或代码生成失败，必须记录阻塞原因并进入修复，不允许退回手写大批量 CRUD。

平台与自定义系统权限模型：

* 平台层有固化的超级管理员和必要初始化数据，用于管理平台级账号、系统创建、租户/平台配置、全局审计和平台级安全策略。
* 平台用户是全局登录主体，账号基础数据维护在平台层；只有平台用户才可以登录。
* 自定义系统内的“用户/成员”不是独立登录账号，而是平台用户在某个自定义系统中的成员扩展，包含系统内组织架构、部门、岗位、角色、状态、数据范围等上下文。
* 自定义系统创建人默认成为该自定义系统的超级管理员，拥有该系统内组织架构、角色权限、菜单、应用、模块、流程和数据范围配置权限。
* 自定义系统内权限由该系统自己的管理配置维护，包括组织架构、角色、菜单、操作、字段和数据权限；平台超级管理员不应绕过系统权限直接操作系统内业务数据，除非 PRD 明确设计审计型/运维型只读能力。
* PRD、DB 和 API 必须区分平台账号、系统成员扩展、系统内角色授权三类对象，避免把平台用户表直接当作系统内组织成员表使用。
* 前端进入自定义系统时必须调用后端系统进入接口（例如 `SYS-001`）获取真实系统、租户、成员和有效权限上下文，不能在前端伪造 member/tenant/permission 后展示系统内菜单。
* 系统创建人或系统超级管理员的聚合权限（例如 `SYS_MANAGE_ALL`）必须在前端权限判断中映射为系统内管理权限，确保系统资料、租户、成员、角色、字典、应用、模块、流程等系统配置入口可见可用；但不得把该聚合权限扩展为平台级 `PLAT_*` 或运维 `OPS_*` 权限。

长任务拆分与调度约定：

* 单个 subagent 任务预计超过 5 分钟或涉及大量文件生成时，Orchestrator 必须要求 `planner` 先拆成更小的任务，再按依赖调度；不得让一个 agent 一口气吞完整项目。
* 开发模式必须优先按 `docs/phases/development-phases.md` 的当前期次推进；除非用户明确批准，不得跨期批量启动任务。
* `pm` 必须在每期结束时给出 `pass/rework/blocked` 结论；`pass` 后才允许进入下一期。
* API 契约必须在开发前冻结：`pm` 根据 `docs/prd.md`、`docs/project_understanding.md` 和各角色评审生成 `docs/api.md`，`dba`、`backend`、`frontend`、`test` 审阅通过后才进入实现。
* backend 不再默认生成冻结版 `docs/api.md`；backend 只能在实现中遵守已冻结 API。若实现发现契约无法落地，必须输出契约变更问题并回到 API 契约评审，不得私自改接口。
* `planner` 必须输出全局任务拆分：包括 DBA、后端、前端、测试、验证和审查任务；每个任务都要有依赖、可并行标记、输入、输出、完成状态和测试要求。
* 后端实现任务默认拆成：架构骨架与父子 POM、数据库连通性检查、SQL 导入、MyBatis-Plus 代码生成、按业务模块拆分的 manage 实现、单元测试、后端 clean compile 自检。
* UI/UX 任务默认拆成：信息架构、导航与模块关系、关键业务流程、页面线框、表单/列表/详情模式、组件与视觉规范、交互状态、中文文案和可用性验收标准。
* 前端实现任务默认拆成：读取冻结版 `docs/api.md` 和 `docs/ui/ui-design.md`、API 契约解析与 typed SDK、真实浏览器工程入口、页面路由与状态、按页面/模块拆分的业务组件实现、API/页面闭环自检、浏览器 smoke/E2E、可部署 `dist/` clean build 自检。
* `test` 默认拆成：测试计划、API 用例、核心场景集成用例、异常/权限用例、回归用例、测试报告。
* 前端必须只基于冻结后的 `docs/api.md` 建 typed API SDK 和页面接口映射，并基于冻结后的 `docs/ui/ui-design.md` 实现页面；推荐输出 `frontend/docs/api-contract-map.md`。页面不得直接散落 axios/fetch 调用，不得为后端没有更新语义的接口伪造“编辑”能力。
* 前端源码目录不得混入构建产物或旁路编译文件，例如 `frontend/src/**/*.vue.js`、临时 `.d.ts`、编译后的 `.js`。
* validator 必须执行 clean 构建：后端 `clean compile`，前端删除 `dist` 和 `tsconfig.tsbuildinfo` 后重新 build；不得用增量构建的 `Nothing to compile` 作为最新源码通过的唯一依据。对于完整上线目标，前端 build 必须生成 `frontend/dist/`，并记录产物路径和核心文件清单。
* validator 必须检查 `docs/api.md` 中的错误码、枚举、状态值是否已同步到 `frontend/src/api/` 与 `frontend/docs/api-contract-map.md`；若后端/API 已新增契约而前端未同步，必须判定失败。

---

# 四、Pipeline 定义（阶段化 + IO协议）

```json
[
  {
    "step": 1,
    "agent": "analyst",
    "input": [
      "docs/user_requirement.md",
      "docs/service_info.md",
      ".codex/oldexamine/"
    ],
    "output": [
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md"
    ]
  },
  {
    "step": 2,
    "agent": "pm",
    "input": [
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/prd.md"
    ]
  },
  {
    "step": 3,
    "agent": "dba",
    "input": [
      "docs/user_requirement.md",
      "docs/prd.md",
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/dba_review.md"
    ]
  },
  {
    "step": 4,
    "agent": "backend",
    "input": [
      "docs/user_requirement.md",
      "docs/prd.md",
      "docs/requirement_analysis.md",
      "docs/service_info.md",
      "docs/legacy_reference.md"
    ],
    "output": [
      "docs/understanding/backend_review.md"
    ]
  },
  {
    "step": 5,
    "agent": "frontend",
    "input": [
      "docs/user_requirement.md",
      "docs/prd.md",
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/frontend_review.md"
    ]
  },
  {
    "step": 6,
    "agent": "test",
    "input": [
      "docs/user_requirement.md",
      "docs/prd.md",
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/test_review.md"
    ]
  },
  {
    "step": 7,
    "agent": "pm",
    "input": [
      "docs/user_requirement.md",
      "docs/prd.md",
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md",
      "docs/understanding/dba_review.md",
      "docs/understanding/backend_review.md",
      "docs/understanding/frontend_review.md",
      "docs/understanding/test_review.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/project_understanding.md",
      "docs/prd.md"
    ],
    "loop_if_fail": "analyst_or_pm"
  },
  {
    "step": 8,
    "agent": "pm",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/requirement_analysis.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/api.md"
    ]
  },
  {
    "step": 9,
    "agent": "dba",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/dba_api_review.md"
    ]
  },
  {
    "step": 10,
    "agent": "backend",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/backend_api_review.md"
    ]
  },
  {
    "step": 11,
    "agent": "frontend",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/frontend_api_review.md"
    ]
  },
  {
    "step": 12,
    "agent": "test",
    "input": [
      "docs/user_requirement.md",
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/understanding/test_api_review.md"
    ]
  },
  {
    "step": 13,
    "agent": "pm",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/understanding/dba_api_review.md",
      "docs/understanding/backend_api_review.md",
      "docs/understanding/frontend_api_review.md",
      "docs/understanding/test_api_review.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/api.md",
      "docs/api_review.md"
    ],
    "loop_if_fail": "analyst_pm_or_api"
  },
  {
    "step": 14,
    "agent": "planner",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/api_review.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/task_plan.md",
      "docs/tasks/"
    ]
  },
  {
    "step": 15,
    "agent": "dba",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/task_plan.md",
      "docs/legacy_reference.md",
      "docs/service_info.md"
    ],
    "output": [
      "sql/init.sql",
      "docs/db_design.md"
    ],
    "retry": 2
  },
  {
    "step": 16,
    "agent": "backend",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/task_plan.md",
      "sql/init.sql",
      "docs/db_design.md",
      "docs/service_info.md",
      "docs/legacy_reference.md"
    ],
    "loop_input": [
      "docs/review.json",
      "docs/test_report.md"
    ],
    "output": [
      "backend/"
    ]
  },
  {
    "step": 17,
    "agent": "frontend",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/task_plan.md",
      "docs/service_info.md"
    ],
    "loop_input": [
      "docs/review.json",
      "docs/test_report.md"
    ],
    "output": [
      "frontend/"
    ]
  },
  {
    "step": 18,
    "agent": "test",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/api.md",
      "docs/task_plan.md",
      "backend/",
      "frontend/",
      "docs/service_info.md"
    ],
    "output": [
      "docs/test_plan.md",
      "docs/test_report.md"
    ],
    "retry": 1
  },
  {
    "step": 19,
    "agent": "validator",
    "input": [
      "backend/",
      "frontend/",
      "docs/api.md",
      "docs/test_report.md",
      "docs/service_info.md"
    ],
    "output": [
      "docs/build_report.md"
    ],
    "retry": 1
  },
  {
    "step": 20,
    "agent": "reviewer",
    "input": [
      "docs/prd.md",
      "docs/project_understanding.md",
      "docs/db_design.md",
      "docs/api.md",
      "docs/task_plan.md",
      "docs/test_report.md",
      "docs/build_report.md",
      "backend/",
      "frontend/"
    ],
    "output": [
      "docs/review.json"
    ],
    "loop_if_fail": "target"
  }
]
```

---

# 五、状态机定义（核心执行引擎）

```json
{
  "INIT": "STEP_1",
  "STEP_1": "STEP_2",
  "STEP_2": "STEP_3",
  "STEP_3": "STEP_4",
  "STEP_4": "STEP_5",
  "STEP_5": "STEP_6",
  "STEP_6": "STEP_7",
  "STEP_7": "STEP_8",
  "STEP_8": "STEP_9",
  "STEP_9": "STEP_10",
  "STEP_10": "STEP_11",
  "STEP_11": "STEP_12",
  "STEP_12": "STEP_13",
  "STEP_13": "STEP_14",
  "STEP_14": "STEP_15",
  "STEP_15": "STEP_16",
  "STEP_16": "STEP_17",
  "STEP_17": "STEP_18",
  "STEP_18": "STEP_19",
  "STEP_19": "STEP_20",
  "STEP_20": "DONE",
  "UNDERSTANDING_FAIL_ANALYST": "STEP_1",
  "UNDERSTANDING_FAIL_PM": "STEP_2",
  "API_FAIL_REQUIREMENT": "STEP_1",
  "API_FAIL_PRD": "STEP_2",
  "API_FAIL_CONTRACT": "STEP_8",
  "TASK_FAIL_PLAN": "STEP_14",
  "TEST_FAIL_BACKEND": "STEP_16",
  "TEST_FAIL_FRONTEND": "STEP_17",
  "TEST_FAIL_BOTH": "STEP_16",
  "REVIEW_FAIL_BACKEND": "STEP_16",
  "REVIEW_FAIL_FRONTEND": "STEP_17",
  "REVIEW_FAIL_BOTH": "STEP_16",
  "ERROR": "END"
}
```

---

# 六、执行规则（必须严格执行）

## 1. 顺序执行

* 必须按 step 顺序执行
* 不允许跳步
* Pipeline 的理解、契约、拆分和最终验证阶段不允许并行执行 step
* 只有 `docs/task_plan.md` 中声明为“无依赖冲突、输出路径不重叠、共享契约已冻结”的小任务可以并行执行
* 只有 review 回环可按 `docs/review.json.target` 返回指定步骤
* 理解评审或 API 契约评审未通过时，必须根据 PM 决策回到 analyst、pm 或 API 契约步骤，不得越过问题继续开发

## 2. 输入来源（强约束）

每个 step 只能使用：

* pipeline 中声明的 input
* 当前回环场景下声明的 loop_input
* `.codex/state.json`
* artifacts 中已有文件

禁止：

* 使用“记忆中的内容”
* 使用未声明的文件
* 让 dba/backend/frontend/test/reviewer 直接扫描旧项目目录；旧项目内容必须先由 analyst 汇总为 `docs/legacy_reference.md`
* 在 API 契约冻结后，任何实现 agent 使用未写入 `docs/api.md` 的接口、字段、枚举或错误码

## 3. 输入校验

执行每个 step 前必须验证：

1. 所有声明 input 必须存在
2. 文件 input 内容不能为空
3. 目录 input 必须至少包含 1 个文件
4. JSON input 必须可解析
5. `docs/service_info.md` 必须存在且非空

输入缺失时：

* 若是 `docs/user_requirement.md` 缺失，终止并提示用户补充需求文件
* 若是旧项目参考目录缺失，终止并提示用户确认旧项目路径
* 其它输入缺失按 ERROR 处理

## 4. 输出校验（关键）

每个 step 完成后必须验证：

1. 所有 output 路径必须存在，且必须与 pipeline 中声明的路径完全一致
2. 若 output 是文件：文件内容不能为空
3. 若 output 是目录：目录中至少包含 1 个文件
4. 若 output 是 JSON 文件：必须可被成功解析
5. 若 output 是 Markdown 文件：必须满足对应产物的格式契约

否则判定为 **FAIL**。

## 5. Retry 机制

当 step 失败：

```text
IF 存在 retry：
   重试当前 step（最多 N 次）
ELSE：
   进入 ERROR 状态
```

---

# 七、产物格式契约

格式契约只约束结构，不固定业务内容；具体业务内容必须根据需求文件、旧项目参考和公共配置动态生成。

## 1. `docs/requirement_analysis.md`

必须包含：

* 原始需求摘要
* 需求边界与不确定项
* 旧项目可复用能力
* 旧项目需要重构或规避的问题
* 竞品/参考资料结论（如果需求文件提供）
* 对 PRD 的输入建议

## 2. `docs/legacy_reference.md`

必须包含：

* 扫描范围
* 旧项目模块结构
* 旧项目 Maven 父子模块结构、包名结构和启动模块
* 旧项目表命名规则、表前缀与 Flyway/SQL migration 组织方式
* 可参考的实体、接口、页面、配置或脚本
* 不建议沿用的问题
* 与新项目实现相关的路径索引

## 3. `docs/prd.md`

必须包含：

* 背景
* 目标
* 用户角色
* 功能范围
* 技术架构与模块边界
* 项目整体流程图
* 业务模块关系图
* 业务场景矩阵
* 业务流程
* 页面/交互说明
* 数据规则
* 权限规则
* 异常场景
* 验收标准

细化要求：

* 项目整体流程图必须从“平台初始化 -> 租户/系统 -> 应用配置 -> 模块建模 -> 页面/权限/流程 -> 发布 -> 运行填报 -> 审批 -> 导出/OpenAPI/审计”串起全局业务闭环。
* 业务模块关系图必须体现平台中心、系统管理、应用配置、应用运行台、流程工作台、上传文件、OpenAPI、运维审计之间的关系和数据流向。
* 业务场景矩阵必须按角色和模块列出核心场景、触发条件、输入、输出、权限点、失败分支和验收点。
* 每个核心业务流程都要写正常流程、异常分支、状态变化和数据落点；不接受只列功能名。
* 页面/交互说明必须按页面列出入口、列表字段、表单字段、按钮动作、状态、空态/错误态和权限禁用态。
* 验收标准必须能直接转为后续 DBA、backend、frontend 的任务，不允许停留在“支持、完善、优化”这类不可验证表述。

## 4. `docs/project_understanding.md`

必须包含：

* PRD 与原始需求一致性结论
* 各角色理解评审摘要：DBA、backend、frontend、test
* PM 决策清单：采纳项、拒绝项、延后项和原因
* 理解审查 issue 台账：issueId、提出角色、问题、阻塞级别、责任方、PM 决策、处理轮次、当前状态、复核角色、关闭条件
* 需求/PRD 待修改项与责任归属
* 已解决的问题和仍需用户确认的问题
* API 契约生成前置条件
* 是否允许进入 API 契约阶段的结论
* 每个 issue 最多 3 次闭环的记录；超过 3 次仍未关闭的 issue 必须标记为阻滞点

配套评审文件：

* `docs/understanding/dba_review.md`：从数据模型、表前缀、索引、迁移、并发和历史兼容角度审查需求是否明确。
* `docs/understanding/backend_review.md`：从接口边界、服务职责、事务、权限、异步、错误码和实现复杂度角度审查需求是否明确。
* `docs/understanding/frontend_review.md`：从页面、交互、状态、字段、按钮、空态、错误态和权限禁用态角度审查需求是否明确。
* `docs/understanding/test_review.md`：从可测试性、验收场景、异常路径、接口用例和端到端闭环角度审查需求是否明确。

## 5. `docs/api.md`

必须包含：

* API 文档生成说明：必须说明该文档是在 PRD、项目理解和多角色契约评审后生成的设计期冻结契约
* 接口总览：按业务模块和用户场景分组
* 每个接口的请求方法、路径、鉴权、权限点、业务场景、前置条件和状态影响
* 入参：字段名、类型、是否必填、默认值、校验规则、来源、是否前端可写
* 出参：字段名、类型、含义、枚举值、空值规则、前端展示用途
* 错误码：错误码、触发条件、前端提示、是否可重试
* 枚举和状态值：编码、中文含义、流转规则、允许操作
* 前后端字段映射说明
* 数据落点和事务边界说明：不用替代 DB 设计，但要说明接口影响哪些领域对象
* 测试契约：每类接口至少列出正常、异常、权限、边界和幂等/并发测试点
* 后端实现约束：backend 必须遵守冻结契约，确需修改时回到 API 契约评审

## 6. `docs/api_review.md`

必须包含：

* DBA API 审查结论
* backend API 审查结论
* frontend API 审查结论
* test API 审查结论
* PM 最终决策：通过、退回 PRD、退回需求分析、退回 API 修改
* 冻结版本号或冻结时间
* 允许进入任务拆分阶段的结论

## 7. `docs/task_plan.md` 与 `docs/tasks/`

`docs/task_plan.md` 必须包含：

* 任务拆分原则
* 任务总览：任务 ID、名称、所属大模块、责任角色、优先级、状态
* 依赖图：必须使用 Mermaid graph/flowchart 表达任务依赖
* 并行批次：哪些任务可以同时跑，为什么可以并行
* 里程碑：理解冻结、API 冻结、DB 完成、后端模块完成、前端模块完成、测试通过、最终 review
* 风险任务和阻塞条件
* 状态汇总规则：小任务完成如何汇总为大任务完成，大任务完成如何汇总为项目完成

`docs/tasks/` 下每个小任务必须是独立 Markdown 文件，至少包含：

* 任务 ID
* 所属模块
* 责任角色
* 目标
* 输入产物
* 输出产物
* 依赖任务
* 可并行条件
* 具体实现范围
* 不做事项
* 单元测试或自检要求
* 交给 test 的集成测试入口
* 完成状态定义

## 8. `docs/db_design.md`

必须包含：

* 表与功能映射
* 模块表前缀与命名规则（统一 `un_` 开头）
* 字段说明
* 表关系
* 索引与约束
* 初始化数据
* 设计说明

## 9. `docs/test_plan.md` 与 `docs/test_report.md`

`docs/test_plan.md` 必须包含：

* 测试范围
* 测试环境
* API 用例
* 权限用例
* 业务场景用例
* 异常和边界用例
* 集成测试路径
* 回归测试清单

`docs/test_report.md` 必须包含：

* 执行命令
* 单任务测试结果
* 大任务/里程碑集成测试结果
* 失败日志摘要
* 未覆盖风险
* test 结论：pass/fail
* fail 时必须给出 target：backend/frontend/both/api/pm/planner/test

## 10. `docs/build_report.md`

必须包含：

* 后端验证命令
* 后端验证结果
* 前端验证命令
* 前端验证结果
* 失败日志摘要（如有）
* validator 结论

此外必须说明：

* 后端是否执行 clean compile，是否重新编译源码文件
* 前端是否清理旧构建产物后重新 build
* 前端是否存在 `frontend/docs/api-contract-map.md`，是否证明页面接口字段映射闭环
* 实际使用的 JDK、Maven、Node/npm 路径和版本
* 声明环境不可用时的 fallback 差异与复现前置条件

---

# 八、Review 判定机制（强制）

reviewer 输出必须是合法 JSON，且只能使用以下结构：

```json
{
  "status": "pass | fail",
  "target": "none | analyst | pm | api | planner | dba | backend | frontend | test | both",
  "issues": [
    {
      "file": "path",
      "problem": "desc",
      "suggestion": "desc"
    }
  ]
}
```

额外要求：

```text
1. docs/review.json 必须是合法 JSON
2. status = pass 时，target 必须为 none，issues 必须为空数组 []
3. status = fail 时，target 必须为 analyst/pm/api/planner/dba/backend/frontend/test/both，issues 必须至少包含 1 条问题
4. issues.file 必须指向 backend/、frontend/、docs/ 或 sql/ 下的具体文件或目录
```

---

# 九、Review 回环机制（核心）

当 `status = "fail"` 时，必须读取 `target` 并执行：

```text
target = analyst  -> analyst -> pm -> project_understanding -> api_contract -> planner -> affected implementation -> test -> validator -> reviewer
target = pm       -> pm -> project_understanding -> api_contract -> planner -> affected implementation -> test -> validator -> reviewer
target = api      -> api_contract -> api_review -> planner -> affected implementation -> test -> validator -> reviewer
target = planner  -> planner -> affected implementation -> test -> validator -> reviewer
target = dba      -> dba -> affected backend tasks -> test -> validator -> reviewer
target = backend  -> backend -> test -> validator -> reviewer
target = frontend -> frontend -> test -> validator -> reviewer
target = test     -> test -> validator -> reviewer
target = both     -> backend -> frontend -> test -> validator -> reviewer
```

回环时：

* 必须将 `docs/review.json` 作为对应 agent 的 `loop_input`
* 如果回环修改了 `docs/prd.md`、`docs/project_understanding.md` 或 `docs/api.md`，必须重新执行后续契约评审与任务拆分
* backend 只能修复 backend 相关问题；发现 API 契约需要变化时只能提交契约变更问题，不能直接改冻结版 `docs/api.md`
* frontend 只能修复 frontend 相关问题
* test 只输出新的 `docs/test_plan.md` / `docs/test_report.md`
* validator 只输出新的 `docs/build_report.md`
* reviewer 只输出新的 `docs/review.json`
* 如果实现阶段发现 `docs/api.md` 的接口、字段、枚举、状态值或错误码无法满足落地，必须回到 `API_FAIL_CONTRACT`，由 PM 组织 API 契约评审；backend/frontend 不得私自修改冻结契约。
* 如果 reviewer 发现问题本质是“冻结 API 契约与后端实现、前端 typed SDK 或 `frontend/docs/api-contract-map.md` 不一致”，必须按不一致来源判定：契约本身错则 `target = api`，后端未实现则 `target = backend`，前端未同步则 `target = frontend`，两端都错则 `target = both`。
* 最后一轮 reviewer 发现的纯契约同步缺口，若只涉及前端错误码、枚举或契约文档同步，Orchestrator 应在进入 `review_fail_limit` 前执行一次 `contract_sync` 微修复：`frontend -> test -> validator -> reviewer`。该微修复不新增业务功能，不允许修改后端或 API 契约，只用于把冻结 API 契约同步到前端类型和契约映射。

最多允许 3 次 review 循环。超过后进入 ERROR，并返回失败原因与最后一次 reviewer issues。

项目理解评审最多允许 3 次回环；API 契约评审最多允许 3 次回环。超过后进入 ERROR，并返回 PM 决策中的未决问题、各角色 fail 原因和需要用户确认的具体问题。

建议保留历史 review：

```text
docs/reviews/review-1.json
docs/reviews/review-2.json
docs/reviews/review-3.json
```

`docs/review.json` 始终代表最新审查结果。

reviewer 还必须检查以下架构项；任一不满足时必须判定 fail：

* 后端是否参考旧项目形成 Maven 多模块工程，而不是平铺单模块。
* 表名是否按模块前缀区分，是否和 `docs/db_design.md` 映射一致。
* 基础 CRUD 是否由 MyBatis-Plus 代码生成器生成，生成命令是否能直接复跑。
* `base` 与 `manage` 是否在各业务模块内分层清晰，是否避免将业务实现堆在 `examine-web`。
* validator 是否证明后端 clean compile 和前端 clean build 通过。

---

# 十、Artifacts 管理（强制）

你必须维护 `.codex/state.json`：

```json
{
  "run_id": "uuid",
  "current_step": 1,
  "status": "INIT",
  "completed_steps": [],
  "artifacts": [
    "docs/service_info.md"
  ],
  "attempts": {},
  "review_loops": 0,
  "understanding_loops": 0,
  "api_review_loops": 0,
  "api_frozen": false,
  "task_status": {},
  "artifact_hashes": {},
  "started_at": "",
  "updated_at": "",
  "last_error": ""
}
```

规则：

1. 每完成一个 step：
   * 更新 `.codex/state.json`
   * 更新 artifacts
   * 更新 attempts
   * 更新 updated_at
   * 必要时更新 artifact_hashes
   * 若 step 14 之后执行小任务，必须更新 `task_status`
2. 后续步骤必须从 `.codex/state.json` 和 artifacts 读取
3. `.codex/state.json` 是唯一状态来源，必须可重复读取
4. 不允许使用“对话内容”作为数据源
5. 失败原因必须结构化记录到 `last_error`

`task_status` 记录每个小任务状态，推荐结构：

```json
{
  "BE-PLAT-001": {
    "status": "pending | in_progress | blocked | done | failed",
    "owner": "backend",
    "depends_on": ["DB-PLAT-001"],
    "parallel_group": "backend-plat",
    "outputs": ["backend/examine-plat/..."],
    "test": "passed | failed | not_run",
    "updated_at": ""
  }
}
```

`last_error` 推荐结构：

```json
{
  "type": "missing_input | invalid_output | invalid_json | invalid_contract | build_failed | review_fail_limit | agent_error",
  "step": 3,
  "message": "错误说明",
  "artifact": "docs/api.md"
}
```

---

# 十一、SubAgent 行为约束（强制）

所有 subagent：

* 只能执行分配任务
* 只能使用声明输入
* 只能写声明输出
* 不允许调度其他 agent
* 不允许修改 pipeline
* 不允许做流程决策

若出现越权：

* Orchestrator 必须忽略越权内容
* 必须按当前 step 重新调度或进入失败重试

---

# 十二、异常处理

## 1. 输入缺失

终止并提示用户补充，或按当前 step 的 retry 规则处理。

## 2. 文件未生成

判定失败，进入 retry / error。

## 3. 产物结构不符合契约

判定失败，要求对应 subagent 按声明路径和契约重新生成。

## 4. validator 构建失败

判定失败，写入 `docs/build_report.md`，并按 retry 规则处理。重试后仍失败则进入 reviewer，由 reviewer 决定 target；若构建产物严重缺失，可直接 ERROR。

## 5. reviewer 结构错误

强制要求 reviewer 重新生成 `docs/review.json`。

## 6. 多次 review 失败

超过 3 次 review 回环后进入 ERROR。

## 7. 路径不一致

判定失败，要求 subagent 按声明路径重新生成产物。

---

# 十三、最终输出格式（必须）

```md
## 执行摘要

- step1(analyst)：完成/失败/未执行
- step2(pm)：完成/失败/未执行
- step3-6(角色理解评审)：完成/失败/未执行
- step7(pm项目理解决策)：完成/失败/未执行
- step8-13(API契约生成与评审)：完成/失败/未执行
- step14(planner任务拆分)：完成/失败/未执行
- step15(dba)：完成/失败/未执行
- step16(backend)：完成/失败/未执行
- step17(frontend)：完成/失败/未执行
- step18(test)：完成/失败/未执行
- step19(validator)：完成/失败/未执行
- step20(reviewer)：完成/失败/未执行

## 产物

- docs/requirement_analysis.md
- docs/legacy_reference.md
- docs/prd.md
- docs/project_understanding.md
- docs/understanding/
- docs/api.md
- docs/api_review.md
- docs/task_plan.md
- docs/tasks/
- sql/init.sql
- docs/db_design.md
- backend/
- frontend/
- docs/test_plan.md
- docs/test_report.md
- docs/build_report.md
- docs/review.json

## 结果

任务完成 / 失败

## 如失败

- 失败步骤
- 原因
- reviewer issues（如有）
```

---

# 十四、设计原则（最终版）

* 一切以 **Pipeline + 状态机** 为准
* 一切以 **文件（artifacts）** 为数据源
* 一切以 **格式契约 + 校验规则** 判断成功/失败
* 旧项目只读参考，新项目产物只写入当前 `examine2/`
* 当前工作区已接入 git，不从旧项目开分支承载重构
* 不依赖 LLM 主观判断
* 保证流程 **可重复执行 / 可回溯 / 可控**
# 2026-06-09 补充规则：PM 全系统验收口径

* 本机全系统验证必须区分“后端/接口功能链路通过”和“前端完整业务 UI 可用”。即使 AUTH、平台、系统、成员/RBAC、字典、模块/运行态、流程、文件、OpenAPI、审计/运维等接口链路全部通过，也只能说明后端核心功能可验收；若对应业务模块没有真实页面、表单、列表、详情和浏览器流程验证，PM 不得宣称完整系统已完成。
* PM/Orchestrator 每次给出阶段状态时，必须拆开说明：后端功能状态、前端页面状态、部署包状态、测试覆盖状态、剩余模块和下一阶段计划。不能用一个笼统的 `pass/accepted` 覆盖这些维度。
* 用户要求“本机启动前后端，跑整个项目功能”时，验证对象是系统业务链路，不是只跑前端构建或页面冒烟；但若要判定“给用户使用的完整系统”，还必须补充真实浏览器 UI 流程。
