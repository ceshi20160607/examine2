# Open Design 原型工作流

## 目标

Open Design 用作本项目 UI/UX 的高保真原型生成和冻结工具，避免前端直接按接口拼页面。PM、UIUX、frontend、test、reviewer 必须先以原型为共同基线，再进入页面实现和验收。

## 本机连接

当前主用 Windows 原生 Open Design：

```text
Open Design App: D:\java\opendesign\Open Design\Open Design.exe
Open Design Web: http://127.0.0.1:57033/ 或 http://127.0.0.1:57029/
Open Design Daemon: http://127.0.0.1:57023
Open Design Data: C:\Users\sheji\AppData\Roaming\Open Design\namespaces\release-stable-win\data
Node.js: D:\java\nodejs\node.exe
```

Codex 全局 MCP 配置已切换为 Windows 原生 Open Design。该配置需要重启 Codex 或新开线程后才会暴露 Open Design MCP 工具。

当前 Codex MCP 注册等价于：

```powershell
codex mcp add open-design `
  --env OD_DATA_DIR="C:\Users\sheji\AppData\Roaming\Open Design\namespaces\release-stable-win\data" `
  --env OD_SIDECAR_IPC_PATH="\\.\pipe\open-design-release-stable-win-daemon" `
  --env ELECTRON_RUN_AS_NODE=1 `
  -- "D:\java\opendesign\Open Design\Open Design.exe" "D:\java\opendesign\Open Design\resources\app\prebundled\daemon\daemon-cli.mjs" mcp
```

Docker 版 Open Design 仅作为历史尝试保留，不再作为当前原型生成主路径。若看到 `http://127.0.0.1:7456/`、`docker exec open-design` 或“PATH 中未发现可用 CLI”的描述，应先确认是否误用了 Docker 版。

未成功生成 Open Design 项目前，`docs/ui/open-design-brief.md` 只能算原型输入，不得算高保真原型冻结产物。

## 阶段闸门

1. PM 输出产品目标和主流程，不允许只列功能点。
2. UIUX 输出 `docs/ui/open-design-brief.md`，内容必须能直接投喂 Open Design。
3. 使用 Open Design 生成原型项目，优先覆盖平台管理员、系统管理员、普通业务用户和平台级对外应用四条主线。
4. UIUX 导出或记录原型产物到 `docs/ui/prototypes/`，并维护 `docs/ui/prototype-traceability.md`。
5. PM 对照 `docs/product/final-user-goal.md` 验收原型。没有通过时，frontend 不得继续页面实现。
6. frontend 只能按冻结原型实现页面。实现差异必须写入 `docs/ui/prototype-implementation-diff.md`，由 PM 裁决。
7. test 必须按原型流程跑真实浏览器 E2E，不能只跑接口或局部 smoke。
8. reviewer 必须检查“原型 -> 页面 -> 测试证据”是否闭环；不闭环时 `frontendUsable=false`。

## Open Design Brief 必填内容

- 产品定位：这个系统给谁用，解决什么日常工作。
- 角色工作台：平台管理员、系统管理员、普通业务用户、外部接入管理员分别登录后看到什么。
- 信息架构：平台层、业务系统层、运行台、对外能力、日志审计的导航分组。
- 连续流程：创建业务系统 -> 建模块和字段 -> 发布菜单 -> 普通用户新增/查询/编辑数据 -> 对外应用授权 -> 日志追踪。
- 页面清单：登录、我的系统、系统总览、成员与权限、模块建模、字段设计、页面发布、运行台列表、运行台新建/编辑/详情、对外应用、日志审计。
- 页面状态：空态、加载态、错误态、无权限态、保存成功、发布成功、表单校验失败。
- 交互约束：新建/编辑使用独立页面或抽屉，不在表格表头堆大表单；列表必须有分页、筛选、批量操作和清晰主按钮。
- 文案要求：默认中文；OpenAPI、appKey、API ID 等专有名词可保留英文。
- 视觉要求：主流企业 SaaS 后台，信息密度适中，避免接口调试台、堆卡片、堆表单、过度工程化布局。

## 不通过条件

- 原型只有静态页面，没有连续用户流程。
- 普通业务用户登录后仍看到平台配置、建模、字段设计、发布、权限配置等管理员入口。
- 平台级对外应用和系统内业务模块混为一个概念。
- 页面主要由接口字段堆出来，没有任务目标、主按钮、状态反馈和错误恢复。
- frontend 在原型未冻结前继续实现最终页面或申请打包。
