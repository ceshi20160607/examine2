# P16 Codex 高级原型评审记录

状态：`advanced-prototype-ready-for-pm-uiux-review`

更新时间：2026-06-13

## 1. 生成说明

本版原型由 Codex 当前会话直接生成，没有调用 Open Design AMR 或其它需要账号的外部模型。

本版输入不再只基于 `docs/ui/open-design-brief.md`，而是合并读取：

- `docs/user_requirement.md`
- `docs/product/final-user-goal.md`
- `docs/prd.md`
- `docs/ui/open-design-brief.md`
- `docs/ui/prototype-traceability.md`

## 2. 产物

- 高级原型：`docs/ui/prototypes/p16-codex-advanced-prototype.html`
- 上一版基础原型：`docs/ui/prototypes/p16-codex-high-fidelity-prototype.html`

## 3. 相比上一版增强

- 增加初始需求中的完整产品能力地图：平台中心、系统配置中心、业务运行台、流程工作台、文件/导出中心、开放集成、运维审计。
- 增加系统管理员视角的流程与导出配置、发布检查、权限预览和配置版本语义。
- 增加普通业务用户视角的待办、附件、导出任务、数据范围、错误态和不可见配置入口说明。
- 增加审批人视角：待办、流程图、审批历史、同意/拒绝操作。
- 增加集成管理员视角：appKey/secret、scope、字段动作授权、IP 白名单、限流、幂等和 requestId。
- 增加运维审计视角：数据库、Redis、文件存储、密钥、nginx `/api` 转发、版本和系统体检。
- 增加“初始需求能力覆盖”矩阵，避免只证明车辆 MVP，而忽略完整产品目标。

## 4. 仍然不是最终前端

该文件仍是可点击 HTML 原型，不是生产前端代码，不调用真实后端接口，也不能替代浏览器 E2E。

前端实现前，PM/UIUX/frontend/test/reviewer 必须先评审：

- 普通用户是否能自然理解“登录后去哪里、能做什么、不能看什么”。
- 平台层、系统层、运行台、平台级对外应用是否清楚分离。
- 初始需求中的流程、文件、导出、OpenAPI、运维审计是否在信息架构中有入口和边界。
- 哪些能力进入 P16 实现，哪些明确作为后续增强。

## 5. 下一步

PM/UIUX 评审通过后，frontend 应基于该高级原型拆分页面级任务，并补充 `docs/ui/prototype-implementation-diff.md`。未完成真实前端实现、真实浏览器连续剧本、clean build、review 和部署验证前，`frontendUsable=false`、`fullProjectDeployable=false` 继续保持。

## 6. 本机浏览器验证

已使用本机 Chrome 打开 `p16-codex-advanced-prototype.html` 并验证：

- 登录页和产品能力地图可加载。
- 普通用户 `che` 可进入“车 / 业务运行台”。
- 审批人可进入“流程工作台 / 我的待办”。
- 运维审计可进入“运维体检”。
- 原型追踪页可显示“初始需求能力覆盖”和 `P16-FLOW-013`。
