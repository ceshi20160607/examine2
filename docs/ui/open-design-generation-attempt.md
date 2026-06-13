# P16 Open Design 原型生成尝试记录

时间：2026-06-13

## 0. 后续状态

该记录描述的是 Docker 版 Open Design 的失败尝试。当前主路径已切换为 Windows 原生 Open Design：

```text
Open Design App: D:\java\opendesign\Open Design\Open Design.exe
Open Design Web: http://127.0.0.1:57033/
Open Design Daemon: http://127.0.0.1:57023
Node.js: D:\java\nodejs\node.exe
```

Docker 版 `http://127.0.0.1:7456/` 和 `docker exec open-design ...` 不再作为 P16 原型生成主路径。

## 0.1 Windows 原生 MCP 调用结果

时间：2026-06-13

Codex 已通过 Windows 原生 Open Design MCP 创建当前项目原型工作区：

```text
Open Design project: examine2-p16-ui-prototype
Project name: examine2 P16 Unified Business Platform Prototype
Project dir: C:\Users\sheji\AppData\Roaming\Open Design\namespaces\release-stable-win\data\projects\examine2-p16-ui-prototype
```

已写入 Open Design 项目制品：

- `brief.md`
- `traceability.md`

已发起生成任务：

```text
runId: 347d52d1-e88d-4302-9cee-8c7a7ccc4075
agent: AMR
plugin: example-web-prototype
```

生成结果：

```text
status: failed
errorCode: AMR_AUTH_REQUIRED
error: AMR sign-in is required. Sign in to AMR Cloud again, then retry this run.
```

PM/Reviewer 判定：

- Windows 原生 Open Design MCP 链路已打通，Codex 可以创建项目、写入输入制品并发起生成。
- 当前未生成高保真 HTML 原型，原因是 AMR Cloud 需要在 Windows 版 Open Design 中重新登录。
- 不得把 `brief.md`、`traceability.md` 或本记录视为已冻结 UI 原型。
- 用户在 Open Design Windows 客户端完成 AMR 登录后，可复用 `examine2-p16-ui-prototype` 项目重新运行生成。

## 0.2 Codex 低成本替代产出

用户确认当前只有 Codex 可用，Open Design 内置模型均需要账号登录，因此 P16 不再等待 Open Design 模型生成。

Codex 已基于冻结 brief 和流程追踪矩阵直接生成自包含 HTML 原型：

```text
docs/ui/prototypes/p16-codex-high-fidelity-prototype.html
```

配套评审记录：

```text
docs/ui/prototypes/p16-codex-prototype-review.md
```

PM/Reviewer 判定：

- 该文件可以作为 P16 的高保真流程原型输入，进入 PM/UIUX/frontend/test/reviewer 评审。
- 该文件不调用真实后端接口，不代表前端 E2E 或最终可用系统完成。
- 后续 frontend 必须基于该原型补充 `docs/ui/prototype-implementation-diff.md`，再进入页面级实现、浏览器 E2E、clean build、review 和最终包闸门。

## 0.3 Codex 高级原型重做

用户要求“换高级模型再来一次，并加入项目初始需求”。当前会话不能直接切换 Open Design 模型，且 Open Design 内置模型需要账号，因此本轮由 Codex 基于完整文档重新生成高级 HTML 原型。

新增输入：

```text
docs/user_requirement.md
docs/product/final-user-goal.md
docs/prd.md
docs/ui/open-design-brief.md
docs/ui/prototype-traceability.md
```

新增产物：

```text
docs/ui/prototypes/p16-codex-advanced-prototype.html
docs/ui/prototypes/p16-codex-advanced-prototype-review.md
```

Open Design 项目同步文件：

```text
p16-codex-advanced-prototype.html
```

PM/Reviewer 判定：

- 高级原型作为当前 P16 主要评审原型，上一版 `p16-codex-high-fidelity-prototype.html` 只保留为历史版本。
- 高级原型补充了初始需求中的完整产品能力地图、流程工作台、文件/导出中心、OpenAPI、安全策略、运维体检、配置发布版本和权限预览。
- 该原型仍不是生产前端，不代表真实接口、真实 E2E 或最终打包通过。

## 0.4 用户复核驳回高级原型

用户复核后确认：高级原型仍不能冻结。

驳回原因：

- “应用”概念仍有偏差。应用应与系统、流程同级，是系统间调用、系统对外调用和外部使用项目能力的管理主体，不是系统内业务模块或模块分组。
- 左侧导航缺少折叠/展开。
- 新建、编辑、详情交互体系不一致。
- 动态字段驱动列表缺少高级筛选、表头配置、保存视图和字段权限联动。
- 权限表达未完整覆盖页面、字段、数据、导出和应用调用范围。
- 多系统登录、平台普通态/管理态、系统普通态/管理态未成为主流程。

处理结果：

```text
docs/ui/prototypes/p16-codex-advanced-prototype.html
```

仅保留为历史草稿，不再作为冻结原型输入。

新增沉淀：

```text
docs/product/p16-agent-retrospective-and-rework-rules.md
```

## 1. 本地服务检查

Docker 容器：

```text
name: open-design
image: vanjayak/open-design:latest
port: 127.0.0.1:7456->7456/tcp
```

Daemon 状态：

```json
{
    "ok": true,
    "version": "0.8.1",
    "bindHost": "0.0.0.0",
    "port": 7456,
    "installedPlugins": 401
}
```

Web 工作台可访问：`http://127.0.0.1:7456/`

## 2. 已完成输入

- 已创建 `docs/ui/open-design-brief.md`
- 已创建 `docs/ui/prototype-traceability.md`
- 已将 `docs/ui/open-design-brief.md` 中的 Open Design Prompt 填入 Web 首页原型输入框

## 3. Web 生成尝试结果

点击“运行”后，页面没有进入项目生成或原型制品页面。

检查“本地 CLI”菜单，Open Design 显示：

```text
本地 CLI · 未选择 · 默认
PATH 中未发现可用 CLI
```

## 4. PM/Validator 判定

本次未生成 Open Design 原型，不得把 `docs/ui/open-design-brief.md` 当作已冻结高保真原型。

原因不是需求输入缺失，而是 Docker 版 Open Design daemon 运行在 Linux 容器内，无法发现 Windows 宿主机上的 Codex CLI。当前 Web 工作台可打开，MCP server 可配置，但 Web 中的“运行”需要可用 agent CLI 或自带 Key 配置。

## 5. 当前解决方式

### 5.1 已采用：Windows 原生 Open Design

Windows 原生 Open Design 已运行在宿主机上，能使用宿主机环境和 Open Design 自身的 AMR 通道。适合通过 Web 工作台直接生成原型。

### 5.2 当前 Web 通道

当前 Open Design Web 显示执行通道：

```text
本地 CLI · AMR · gpt-5.4-mini
```

### 5.3 当前 MCP 通道

Codex 全局配置已切换为 Windows 原生 Open Design，重启 Codex 或新开线程后可通过 MCP 读取/创建 Open Design 制品。

## 6. 下一步

P16 继续推进时，必须使用当前 Windows 原生通道生成原型：

1. Open Design Web 工作台选择当前项目目录。
2. 使用 `docs/ui/open-design-brief.md` 中的 prompt 生成原型。
3. 将生成结果登记到 `docs/ui/prototypes/` 或通过 Open Design MCP 读取制品。

未选定并成功生成原型前，`frontendUsable=false`、`fullProjectDeployable=false`、`packageGate=trial_package_only_ui_rework_required` 保持不变。
