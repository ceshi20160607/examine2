# P18 UI 角色流程失效事故记录

状态：`confirmed-fail`

更新时间：2026-06-13

## 1. 事故结论

用户反馈成立：`docs/ui/prototypes/p18-ui-card-effect-lowcost.html` 不是合格 UI 设计，也不是 Open Design 产物，更不是 UI/UX agent 冻结产物。

该文件由主 agent 为了低成本快速看效果手写生成，结果把“低成本”降级成了“低质量”，违反了项目多角色治理的目标。

## 2. 直接问题

- 左侧导航把平台管理、自建系统管理、系统普通用户运行台混在同一菜单里。
- 系统数量上百上千时，左侧菜单无法承载系统入口。
- Flow 和应用被合并展示，破坏同级概念。
- 日志审计没有做成可筛选、可分页、可追踪的列表。
- 内容区出现大面积无意义空白。
- 卡片高度异常，视觉层级粗糙。
- 页面仍是功能堆砌，不是可用后台设计。
- 用户截图后才能指出明显问题，说明 reviewer 闸门没有提前生效。

## 3. 角色机制为什么失效

1. Orchestrator 没有把 UI 工作交给 `uiux` 角色，而是主 agent 直接写 HTML。
2. Open Design 已接入但未作为高保真原型主路径使用；当 Open Design 模型账号不可用时，也没有把降级边界讲清楚。
3. PM 没有在展示前确认“这是草稿、不是 UI 设计”。
4. Reviewer 没有在用户看到前执行视觉和信息架构失败闸门。
5. 进度记录中多次出现“原型/效果稿”字样，容易让用户误以为这是正式设计流程产物。

## 4. 后续硬规则

1. 主 agent 手写 HTML 只能叫 `main-agent-scratch`，不得叫 UI 设计、高保真原型或可用页面。
2. UI 产物必须有来源标记：`uiux-agent`、`open-design`、`frontend-implementation` 或 `main-agent-scratch`。
3. 来源为 `main-agent-scratch` 的文件默认不得作为 frontend 实现依据。
4. 展示给用户前，Reviewer 必须检查：
   - 平台管理、系统管理、普通用户界面是否分离。
   - 系统入口是否支持大量系统，而不是堆在左侧菜单。
   - Flow 和应用是否同级分离。
   - 日志、审计、待办、消息是否有真实列表/筛选/分页/追踪结构。
   - 页面是否存在大面积无意义空白或异常拉伸卡片。
   - 是否能解释普通人如何使用。
5. Open Design 可用时，UI/UX 原型优先进入 Open Design；Open Design 账号或模型不可用时，必须先记录降级原因，再由 `uiux` agent 输出设计，不允许主 agent 顶替 UI/UX。

## 5. 三角色复盘结论

### 5.1 PM 结论

- 角色分工停留在名义上，实际交付链路被主 agent 接管。
- 缺少 UI/UX 冻结设计时，PM 不应允许任何真实页面、HTML、CSS、路由、组件进入实现。
- Open Design、低成本原型、正式 UI 的边界必须明确：低成本原型只能验证方向，Open Design 或 UI/UX 产物负责固化设计，正式 UI 才能作为 frontend 实现依据。
- 下一次 UI 通过条件必须包含 UI/UX 冻结文档、设计通过记录、页面到接口映射和连续用户主流程验收剧本。

### 5.2 UI/UX 结论

- 当前页面问题不是单纯样式差，而是信息架构错误。
- 必须拆成三个工作空间：平台管理工作台、系统管理工作台、系统普通用户运行台。
- 系统数量多时，不能把系统放进左侧导航；必须使用系统选择页、搜索、最近访问、收藏、角色/状态筛选、分页或虚拟滚动。
- Flow 和应用在平台层同级但分域：Flow 负责流程模板、实例、待办审批和流程日志；应用负责应用列表、凭证、授权范围、IP 白名单、限流和调用日志。
- 日志/审计必须是可检索、可分页、可追踪的标准列表。

### 5.3 Reviewer 结论

- `docs/ui/prototypes/p18-ui-card-effect-lowcost.html` 状态为 `fail/rejected/blocked`。
- 该文件只能判定为 `main-agent-scratch`，不是 UI/UX 冻结产物，不是 Open Design 产物，不能作为前端实现依据。
- 缺 UI/UX 冻结结论、Open Design 成功生成记录、原型流程追踪矩阵、reviewer pass、三类角色入口主流程证据时，不能给用户当正式方案看。
- 当前 UI 必须保持 `blocked(ui-design-process-invalid)`，直到 UI/UX agent 或 Open Design 重新产出并通过 reviewer。

## 6. 当前处理

- `docs/ui/prototypes/p18-ui-card-effect-lowcost.html` 标记为失败草稿。
- 后续不能基于该文件继续前端实现。
- 下一次 UI 输出必须先由 `uiux` 角色产出设计结构和视觉规范，或由 Open Design 生成可追溯原型，再由 reviewer 审查，通过后才能给用户复核。
