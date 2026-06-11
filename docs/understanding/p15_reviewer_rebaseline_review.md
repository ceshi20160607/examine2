# P15 reviewer 全项目可用性重整审查

审查角色：P15 reviewer

审查结论：fail

目标责任：pm、uiux、frontend、backend、test、validator

本次只做梳理，不修改代码。审查输入为 `docs/product/p15-project-rebaseline.md`、`docs/review.json`、`docs/progress.md`、`AGENTS.md`、`frontend/src/App.ts`，并补查 `.codex/state.json` 的模式与打包门禁。

## 一、总判断

必须主动推翻“系统已可用 / fullProjectDeployable=true / P14 可最终打包”的结论。

当前最有效的状态应以 P15 为准：

- `docs/review.json` 已将 P15 置为 `status=fail`、`frontendDeployable=false`、`fullProjectDeployable=false`、`packageGate=blocked`。
- `.codex/state.json` 中 `P15-project-rebaseline.status=in_progress`，`package_gate=blocked`。
- `docs/product/p15-project-rebaseline.md` 仍为 `draft`，不能作为冻结后的通过依据。
- `docs/progress.md` 和 `.codex/state.json` 仍保留 P14 accepted、P14 package path、P14 final deploy package ready 等历史结论，虽然同时记录了 P14 撤回原因。这会给 Orchestrator、validator、packager 和 PM 继续沿用 P14 包制造误导，属于 P0 治理风险。

因此 P15 reviewer 不得认可任何“P14/P13/P12 已经证明系统可用”的继承结论。P15 必须重新以普通用户、系统管理员、平台管理员连续剧本验证。

## 二、P0 风险

### P0-1 PM 过度验收和状态治理冲突

证据：

- `docs/progress.md` 记录 P14 已进入最终部署包完成、`fullProjectDeployable=true`、P14 final package ready，同时顶部又记录 P15 已撤回 P14 结论。
- `.codex/state.json` 的 `P14-prototype-concept-rework` 同时存在 `status=accepted`、`package_path`、`linux_package_path`、`retracted_at`、`retract_reason`、`package_gate=blocked`。
- `docs/review.json` 已是 P15 fail，但历史包路径仍在状态树和进度文档中保留为可见交付物。

问题：

PM 曾把“功能点、构建、局部 E2E、打包存在”误判为“普通人可自然使用”。当前文档虽已撤回，但状态治理没有把 P14 可用结论彻底降级为无效证据。

要求：

- P15 pass 前，所有 “P14 accepted / final package ready / fullProjectDeployable=true” 只能作为历史错误记录，不能作为打包、部署、验收或继续开发依据。
- PM 必须输出 P15 冻结版基线和可用性验收口径，不得再用局部 smoke 或构建通过替代用户剧本。

### P0-2 登录落点仍不能证明普通成员默认进入业务运行台

证据：`frontend/src/App.ts`

- `submitAuth` 登录后调用 `resolvePostLoginRoute`。
- `resolvePostLoginRoute` 只有在 `systems.length === 1 && !hasPlatformWorkPermission()` 时才自动 `enterSystem` 并进入 `routeAfterEnter`。
- 多系统用户直接进入 `DEFAULT_AUTHENTICATED_ROUTE`，即平台层默认路由。
- `routeAfterEnter` 对具备 `SYS_MEMBER_VIEW`、`SYS_ROLE_VIEW`、`APP_VIEW`、`MODULE_VIEW`、`FIELD_VIEW`、`PAGE_VIEW` 的成员进入系统总览，而不是普通业务运行台。
- `hasPlatformWorkPermission` 只排除部分平台权限前缀，不能覆盖所有会让普通成员误入平台空间的组合。

问题：

普通成员、系统成员、系统管理员的第一屏规则仍由权限碎片推导，未明确实现“普通业务用户登录后直接看到自己所属业务系统运行台”的产品规则。多系统成员也没有业务化选择页证据。

要求：

- 必须用真实普通账号 `che` 验证：登录后如果只有一个系统，第一屏就是“车系统 / 业务运行台”；如果多个系统，第一屏是业务系统选择，而不是平台后台。
- 普通用户不得看到平台账号、平台角色、建模配置、字段设计、发布、授权等入口。

### P0-3 系统成员开通仍暴露底层 ID 和平台账号概念

证据：`frontend/src/App.ts`

- `renderSystemMembers` 已新增“开通并加入本系统”入口。
- 但表单仍要求或暴露 `部门 ID`、`角色 ID`、`租户 ID`。
- 说明文案仍提到“已有平台账号会绑定，没有登录名会自动创建平台账号”。

问题：

虽然比 P14 有进步，但它仍让管理员理解平台账号、系统成员、租户、角色 ID、部门 ID 的底层模型。对普通系统管理员而言，这还不是自然的“开通成员、选择部门、勾选角色、保存”的业务操作。

要求：

- P15 pass 前，成员开通必须以业务控件呈现：部门选择、角色选择、数据范围选择、初始密码策略、开通结果。
- 不得要求用户手填任何内部 ID。

### P0-4 普通用户权限隔离缺少 P15 级真实浏览器反证

证据：`frontend/src/App.ts`

- `renderSidebar` 通过 `shouldShowNavItem` 按 layout、section、disabled 和是否存在 system context 过滤导航。
- 真正权限控制依赖 `resolveAppShellState`、`permissionStore` 和菜单授权，但本次 P15 输入没有提供最新普通用户浏览器证据。
- 当前 P15 文档明确把“普通成员仍看到平台层、建模和权限配置入口”列为 P0。

问题：

代码存在权限过滤机制不等于普通用户隔离已经成立。P15 必须用陌生普通用户账号证明页面实际不可见、不可访问、不可通过 hash 路由绕过。

要求：

- 单独执行普通业务用户验收，不得只用平台管理员或系统管理员账号代替。
- 验证普通用户不能通过侧栏、快捷卡片、URL hash 直接访问平台管理、系统建模、字段设计、权限配置、发布页面。

### P0-5 打包闸门必须继续阻断

证据：

- `docs/review.json.packageGate=blocked`。
- `.codex/state.json.phase_status.P15-project-rebaseline.package_gate=blocked`。
- `docs/product/p15-project-rebaseline.md` 明确列出“不打包条件”。

问题：

P15 仍处于重整和问题发现阶段。任何基于 P14 包路径、P14 review pass 或 P14 package ready 的继续打包，都是越过 P15 闸门。

要求：

- P15-0 至 P15-3 未闭环前，不允许生成新的最终包。
- 只有 P15 连续端到端剧本、普通用户隔离、OpenAPI、日志追踪、clean build、review 全部通过后，才能进入 P15-4 打包。

## 三、P1 风险

### P1-1 UI 仍有工程化堆叠痕迹

证据：`frontend/src/App.ts`

- `renderRuntimeModule` 在同一页面堆叠模块选择、关键字查询、打开业务表单、动态表单、创建按钮、记录列表、详情 tab。
- `renderRuntimeForm` 直接把 schema 字段渲染为表单，并在表单尾部放“新建记录 / 更新当前记录”。
- 页面组织仍接近“接口调试台 + 动态表单”，不是清晰的业务列表、新建页/抽屉、详情页、编辑流。

问题：

用户虽然能点到功能，但页面主动作、状态层级和任务路径仍不够像日常业务系统。

要求：

- 运行台必须重组为业务模块列表、记录列表、主按钮新建、独立新建/编辑区域、详情区和状态反馈。
- “新建记录”不能继续作为表单尾部动作隐藏在工程化 schema 表单里。

### P1-2 分页没有形成用户可操作闭环

证据：`frontend/src/App.ts`

- `renderDataTable` 只渲染表头、数据行和空态，没有分页组件、总数、当前页、下一页/上一页入口。
- 多处查询固定 `pageNo: 1`，如运行台记录查询固定 `pageNo: 1, pageSize: 20`。
- 成员、字典、流程、文件、导出、OpenAPI、审计等列表也大量固定 pageNo/pageSize。

问题：

后端即使返回 `PageResult`，前端也没有把分页能力交给用户。数据量一多，列表不可用。

要求：

- 所有 PageResult 列表必须显示总数、当前页、页大小、上一页、下一页、刷新当前页。
- P15 测试必须覆盖至少一类业务列表和一类管理列表的翻页。

### P1-3 退出入口存在但未完成 P15 验收

证据：`frontend/src/App.ts`

- `renderTopbar` 提供“退出”按钮。
- `logout` 调用 `AUTH-004` 后 `navigate(result.route)`。

问题：

实现存在不等于部署体验通过。P15 输入没有给出普通成员、系统管理员、平台管理员三类账号在真实浏览器里退出后 token 清理、页面跳转、返回保护和重新登录的证据。

要求：

- P15 E2E 必须覆盖普通成员退出、浏览器返回、重新访问系统内路由时回登录页或无权限页。

### P1-4 新建入口仍不像业务系统

证据：`frontend/src/App.ts`

- 平台系统、平台账号、平台角色、系统成员、运行台记录等多处将创建表单直接铺在列表上方。
- 运行台记录新建按钮位于动态表单尾部。

问题：

这种布局更像配置后台或调试界面。对于普通业务用户，最常见入口应是列表页主按钮“新建”，随后进入明确的新建任务区域。

要求：

- 普通业务运行台必须以主按钮或任务入口触发新建。
- 创建、编辑、详情、提交的状态必须分区清晰，避免列表和表单长期堆叠。

### P1-5 P15 测试证据不足

证据：

- 当前 `docs/review.json` 已列出 P15 requiredBeforePackage，但本轮输入没有新的 P15 测试报告。
- `docs/progress.md` 记录的大量 TEST-010、TEST-011、P14 E2E 是历史阶段证据，已被部署反馈推翻，不能作为 P15 pass 证据。

问题：

P15 的目标是推翻历史过度验收并重建连续用户剧本。历史 E2E 不能证明新问题已消失。

要求：

- test 必须输出 P15 专属测试记录，覆盖“车系统”连续剧本、普通用户权限隔离、成员开通、分页、新建、退出、OpenAPI、日志追踪。
- 测试报告必须明确失败项、未覆盖项和不可打包结论。

## 四、P15 reviewer fail 标准

任一条件成立，P15 reviewer 必须 fail：

1. PM 仍保留或引用 P14/P13/P12 的 `fullProjectDeployable=true`、accepted 或 package ready 作为当前可用依据。
2. `.codex/state.json`、`docs/progress.md`、`docs/review.json` 对当前 package gate、fullProjectDeployable、phase status 给出互相矛盾或可误读的结论。
3. `docs/product/p15-project-rebaseline.md` 未冻结，或缺少普通用户第一屏、成员开通、权限隔离、退出、分页、新建、测试和打包闸门的明确口径。
4. 普通成员登录后仍可能进入平台工作空间、平台系统列表、管理配置页，或必须理解系统 ID、租户 ID、成员 ID、角色 ID 才能完成工作。
5. 系统管理员不能在当前系统内自然开通成员，仍需要绕行平台账号管理或手填底层 ID。
6. 普通业务用户能看到或访问建模配置、字段设计、页面发布、权限配置、平台账号、平台角色、运维诊断等管理入口。
7. 运行台仍是接口驱动的表格/表单堆叠，缺少明确主动作、空态、错误恢复、详情/编辑/提交状态流。
8. PageResult 列表没有真实分页控件。
9. 新建入口仍散落在表头、表单尾部或工程化配置面板中，无法被普通用户自然识别。
10. P15 没有真实浏览器连续 E2E 证据，或只跑平台管理员/API 链路。
11. P15 clean build、部署验证、nginx `/api/` 保留转发、打包内容清单未重新证明。
12. P15-3 未通过前继续生成最终包。

## 五、P15 reviewer pass 标准

只有全部满足，P15 reviewer 才能 pass：

1. PM 明确撤回 P14/P13/P12 可用结论，并在 `.codex/state.json`、`docs/progress.md`、`docs/review.json` 中保持唯一一致状态：P15 之前的包不可作为最终交付。
2. `docs/product/p15-project-rebaseline.md` 冻结，且回答清楚：系统给谁用、登录第一屏是什么、普通成员如何只处理自己系统的数据、管理员如何开通成员、用户如何退出、列表如何分页、在哪里新建业务数据、出错后如何恢复。
3. UI/UX 输出 P15 页面级设计，覆盖平台工作空间、业务系统选择、系统总览、成员与权限、建模配置、业务运行台、对外应用、日志追踪，并包含主次按钮、空态、错误态、加载态、权限禁用态和中文文案。
4. 前端按 P15 UI 重组，不再以 API 模块或 schema 表单堆叠作为主要体验。
5. 后端提供或确认“开通系统成员账号并加入当前系统”的业务语义接口，前端不要求用户手填平台账号 ID、角色 ID、部门 ID、租户 ID。
6. 普通成员 `che` 登录后直接进入“车系统 / 业务运行台”，只能看到被授权模块、字段和数据范围。
7. 平台管理员完成连续剧本：创建车系统、开通 che、配置模块字段页面菜单、发布、授权、退出。
8. che 完成连续剧本：登录、进入运行台、新增车辆记录、查询、编辑、提交、退出；同时无法访问管理入口。
9. 对外应用连续剧本通过：创建平台级对外应用、授权车系统数据、外部调用成功、按 requestId 查日志。
10. 所有关键列表显示分页信息并可翻页。
11. test 输出 P15 专属真实浏览器 E2E 和失败恢复记录。
12. validator 输出 P15 clean build 和部署包验证记录。
13. reviewer 复核 P15 证据后再允许 package gate 从 blocked 变为 allowed。

## 六、P15 审查清单

### PM / 治理

- [ ] `docs/product/p15-project-rebaseline.md` 从 draft 变为冻结版。
- [ ] `.codex/state.json` 不再让 P14 同时表现为 accepted 和当前可用。
- [ ] `docs/progress.md` 明确 P14/P13/P12 包均为历史失效证据。
- [ ] `docs/review.json` 与状态文件的 `fullProjectDeployable`、`packageGate`、`current_phase` 一致。
- [ ] PM 输出 P15 不打包条件和 pass 条件。

### UI/UX

- [ ] 登录后第一屏按用户画像和权限定义清楚。
- [ ] 多系统普通成员看到业务系统选择页，不是平台后台。
- [ ] 普通成员运行台有清晰业务模块入口、空态、主动作。
- [ ] 新建、编辑、详情、提交不是同页表单堆叠。
- [ ] 成员开通页面不暴露底层 ID。
- [ ] 分页、退出、错误恢复有设计稿和中文文案。

### Frontend

- [ ] 登录落点不再只用“单系统且无平台权限”作为普通用户判断。
- [ ] 普通用户可见导航由真实菜单、权限点、系统成员上下文共同决定。
- [ ] hash 直达管理路由时能拦截或显示无权限。
- [ ] 所有 PageResult 列表有分页组件。
- [ ] 运行台新建入口改为主按钮/独立任务区。
- [ ] 成员开通使用选择控件，不要求手填部门 ID、角色 ID、租户 ID。
- [ ] 退出后 token、上下文、权限和系统状态清理，并返回登录态。

### Backend

- [ ] 成员开通接口具备业务语义：创建或复用登录账号、加入当前系统、分配部门/角色/租户、返回可理解结果。
- [ ] 普通成员进入系统接口返回真实 `currentTenant/currentMember/permissions/menus`。
- [ ] 后端拒绝普通成员访问平台管理、建模、权限配置等接口。
- [ ] 数据范围和字段权限在运行台读写接口生效。

### Test

- [ ] P15 测试不能复用 P14 pass 作为通过依据。
- [ ] 执行平台管理员到普通成员的连续“车系统”浏览器剧本。
- [ ] 单独执行 che 普通成员权限隔离剧本。
- [ ] 覆盖退出、分页、新建、编辑、提交、无权限、无模块、登录失败。
- [ ] 覆盖 OpenAPI 授权、调用和 requestId 日志追踪。

### Validator / Package Gate

- [ ] 前端 clean build 重新执行并记录产物路径。
- [ ] 后端 clean package 重新执行并记录产物路径。
- [ ] 部署验证包含 nginx `/api/` 前缀保留转发。
- [ ] P15 review pass 前不生成新的最终包。
- [ ] 包内不包含过期 P14 “可用”结论作为当前证明。

## 七、当前 reviewer 结论

P15 当前必须 fail。

原因不是单点 bug，而是“系统可用”结论仍缺少普通用户视角的连续反证。现有代码和文档显示，P15 正在纠偏，但登录落点、成员开通、普通用户隔离、运行台 UI、分页、新建入口、退出验证、测试证据和打包闸门仍没有形成可交付闭环。

在上述 P0/P1 清单关闭前，禁止恢复 `fullProjectDeployable=true`，禁止允许最终打包。
