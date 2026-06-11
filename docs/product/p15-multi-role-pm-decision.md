# P15 多角色梳理后的 PM 统一裁决

状态：`pm_decision`

时间：2026-06-12

## 参与角色

| 角色 | 输出 |
| --- | --- |
| PM | `docs/understanding/p15_pm_rebaseline_review.md` |
| UI/UX | `docs/understanding/p15_uiux_rebaseline_review.md` |
| Frontend | `docs/understanding/p15_frontend_rebaseline_review.md` |
| Backend | `docs/understanding/p15_backend_rebaseline_review.md` |
| Test | `docs/understanding/p15_test_rebaseline_review.md` |
| Reviewer | `docs/understanding/p15_reviewer_rebaseline_review.md` |

## 统一结论

六个角色结论一致：P15 当前不能进入实现验收或打包，必须先做全项目可用性重整。

P14 失败不是“少几个按钮”，而是以下几类问题同时存在：

1. 产品定位没有在页面中落实：系统仍像平台后台或工程配置台，而不是普通人可使用的业务系统。
2. 登录落点错误：普通成员应进入所属业务系统运行台，而不是先看到平台工作台。
3. 平台账号和系统成员模型暴露给用户：系统管理员不应绕到平台账号页才能开通成员。
4. 页面任务流不清：创建、编辑、列表、详情、发布、授权混在表格和表单里。
5. 基础体验缺口：退出、分页、新建主入口、空态、错误态、权限态没有形成统一规则。
6. 后端权限模型仍有 P0 风险：我的系统语义、成员上下文防伪、平台/系统权限边界、运行台字段和数据范围过滤需要补强。
7. 测试和 reviewer 没有用普通人视角推翻 PM 结论，导致过度验收。

当前裁决：

- `fullProjectDeployable=false`
- `packageGate=blocked`
- P14 包只能作为历史试跑包，不能作为当前完整可用交付
- P15 进入 `P15-1 UI/UX 重画` 前，只允许完善文档和任务，不允许继续打最终包

## P15 必须解决的 P0

| P0 | 责任角色 | 裁决 |
| --- | --- | --- |
| 普通成员登录后默认落点不确定 | backend/frontend | 后端提供可用系统和推荐落点，前端按落点进入业务运行台或业务系统选择页 |
| `my-systems` 语义可能只查 owner | backend | 必须返回账号可管理或可使用的业务系统，包含成员加入的系统 |
| 系统成员开通暴露平台账号模型 | backend/frontend/uiux | 系统内提供“开通成员账号并加入本系统”，页面使用姓名、账号、密码、部门、角色选择 |
| 请求头成员上下文可能可伪造 | backend | 所有系统内接口校验 token 账号、systemId、memberId、tenantId 一致性 |
| 普通用户权限隔离缺真实证明 | frontend/backend/test | `che` 登录后只看到车辆档案模块，直访管理页面被拦截或 403 |
| 运行台字段和数据范围读取未完全落地 | backend/frontend | 列表、详情、schema、导出、历史都按字段可见和数据范围过滤 |
| 页面仍像工程调试台 | uiux/frontend | 重画并实现业务任务流，不能继续表单表格堆叠 |

## P15 执行顺序

### P15-0：PM 重整冻结

输出：

- `docs/product/p15-project-rebaseline.md`
- `docs/product/p15-multi-role-pm-decision.md`
- `docs/issues/pm/development/p15_project_rebaseline_wenti.md`
- `docs/process/p15-agent-coordination.md`
- `docs/process/p15-role-failure-retrospective.md`

退出标准：

- P14 结论已撤回。
- P15 不打包条件明确。
- 所有角色问题已分配责任方。

### P15-1：UI/UX 重画

必须输出：

- 登录和登录后落点设计。
- 我的业务系统/选择业务系统页面。
- 系统首页。
- 成员与权限。
- 建模配置。
- 业务运行台。
- 对外应用中心和系统对外授权。
- 平台日志和系统日志。
- 通用分页、新建、空态、错误态、权限态规范。

退出标准：

- 页面级设计能完整解释“车系统 / che / 车辆档案”剧本。
- frontend 不需要临场决定页面主动作。

### P15-2：后端和前端实现

后端优先级：

1. 可用系统和推荐落点聚合。
2. `my-systems` 包含成员加入系统。
3. 系统成员开通业务化。
4. 成员上下文防伪。
5. 运行台字段可见、字段可写、数据范围和动作权限裁剪。

前端优先级：

1. 登录落点和路由守卫。
2. 普通运行壳、系统管理壳、平台管理壳分离。
3. 我的业务系统和业务运行台重做。
4. 通用 PageResult 分页。
5. 成员与权限去技术 ID。
6. 建模配置改为任务流。
7. 对外应用和日志分层。

### P15-3：真实验收

必须跑通：

1. 平台管理员登录。
2. 创建“车”系统。
3. 在车系统内开通 `che` 成员。
4. 创建车辆业务、车辆档案模块、字段、页面和菜单。
5. 发布并授权 `che`。
6. 管理员退出。
7. `che` 登录后直接进入“车系统 / 业务运行台”。
8. `che` 新增、查询、编辑车辆数据。
9. `che` 看不到平台管理、建模、字段设计、发布、权限配置。
10. 权限负向和日志追踪通过。

### P15-4：打包

只有 P15-3 和 reviewer 通过后才能执行。

## 对当前 partial 代码的裁决

当前已有 4 个 P15 草稿代码改动：

- `backend/examine-module/src/main/java/com/unique/examine/module/manage/bo/MemberInviteBO.java`
- `backend/examine-module/src/main/java/com/unique/examine/module/manage/service/impl/SystemRbacServiceImpl.java`
- `frontend/src/App.ts`
- `frontend/src/pages/system/types.ts`

裁决：

1. 方向基本正确，但不能直接视为完成。
2. 后端自动创建成员账号需要纳入 P15 后端任务，并补上下文防伪和权限边界。
3. 前端登录落点、退出和成员页调整需要等 UI/UX 冻结后重审，可能保留、调整或重做。
4. 在 P15-1 完成前，这些代码保持 `partial`，不进入完成统计，不打包。

## 不允许再发生

1. 不允许用“构建通过”替代“普通人会用”。
2. 不允许用平台管理员链路替代普通用户链路。
3. 不允许让用户手工发现 PM 应该发现的主流程问题。
4. 不允许在状态文件里同时存在“accepted”和“部署反馈失败”的可误读结论。
5. 不允许没有 UI/UX 页面设计就让 frontend 自行拼页面。
6. 不允许没有真实浏览器和普通成员账号验收就恢复 `fullProjectDeployable=true`。
