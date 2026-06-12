# P16 最终 UI/UX 与可用性返工任务计划

状态：`ready-for-uiux`

输入基线：

- `docs/product/final-user-goal.md`
- `docs/product/p16-final-rework-charter.md`
- `docs/review.json`
- 当前 `frontend/src/`

## 1. 任务列表

| 任务 ID | 角色 | 目标 | 输出 | 完成标准 |
| --- | --- | --- | --- | --- |
| P16-PM-001 | PM | 冻结最终目标和返工范围 | `docs/product/p16-final-rework-charter.md` | 明确不允许行为、页面范围、验收矩阵、最终包闸门 |
| P16-UIUX-001 | UI/UX | 输出页面级信息架构和导航 | `docs/ui/p16-information-architecture.md` | 平台层、系统层、运行台、对外应用、审计运维分清 |
| P16-UIUX-002 | UI/UX | 输出关键页面线框和状态 | `docs/ui/p16-page-blueprints.md` | 登录、我的系统、系统首页、成员权限、建模、运行台、对外应用、日志均有具体页面结构 |
| P16-UIUX-003 | UI/UX | 输出组件与交互规范 | `docs/ui/p16-component-guidelines.md` | 主按钮、抽屉/弹窗/详情、分页、筛选、空态、错误态、权限态、危险操作统一 |
| P16-FE-001 | Frontend | 拆分当前超大 `App.ts` | 页面与组件模块 | 路由、壳层、页面、表格、表单、抽屉、状态提示组件拆分清楚 |
| P16-FE-002 | Frontend | 重做全局壳层和登录落点 | 前端页面 | 不同用户进入对应工作空间；退出入口清晰 |
| P16-FE-003 | Frontend | 重做平台管理员页面 | 前端页面 | 我的系统、账号、角色、对外应用、平台日志可自然使用 |
| P16-FE-004 | Frontend | 重做系统管理员页面 | 前端页面 | 成员开通、建模、发布、系统授权、系统日志形成任务流 |
| P16-FE-005 | Frontend | 重做普通业务运行台 | 前端页面 | 普通用户只处理授权模块数据，列表/新建/编辑/详情/提交/分页可用 |
| P16-BE-001 | Backend | 补齐阻塞 UI 的业务聚合接口 | 后端接口 | 不需要前端拼接底层账号、成员、权限、发布状态 |
| P16-TEST-001 | Test | 跑平台管理员到普通用户连续剧本 | `docs/test_runs/p16-main-user-flow.md` | 车系统剧本完整通过，有浏览器证据 |
| P16-TEST-002 | Test | 跑对外应用连续剧本 | `docs/test_runs/p16-openapi-flow.md` | 外部应用授权、调用、日志追踪通过 |
| P16-VAL-001 | Validator | clean build/package 校验 | `docs/build/p16-clean-build-package.md` | 前端 dist、后端 jar、start.sh 权限、包清单通过 |
| P16-REV-001 | Reviewer | 最终目标基线审查 | `docs/review.json` | `frontendUsable=true`、`fullProjectDeployable=true` 只能在所有证据满足后写入 |
| P16-PKG-001 | PM/Validator | 生成最终部署包 | `dist/` | 只有 P16-REV-001 pass 后允许生成 |

## 2. 并行规则

- P16-UIUX-001、P16-UIUX-002、P16-UIUX-003 可由 UI/UX 串行完成，不得跳过。
- P16-FE-001 可以在 UI/UX 初稿后准备结构拆分，但不得改变业务页面，直到 UI/UX 冻结。
- P16-FE-002 至 P16-FE-005 只允许在 UI/UX 冻结后实施。
- P16-BE-001 只处理 UI/UX 和前端实现提出的必要聚合接口，不扩展无关功能。
- P16-TEST、P16-VAL、P16-REV 必须在实现后执行，不能用旧 P15 证据替代。

## 3. 不打包条件

任一条件成立，P16-PKG-001 不得执行：

- `docs/ui/p16-*.md` 缺失或未冻结。
- 普通用户运行台仍像配置台或表格调试页。
- 新建入口仍主要依赖表格上方常驻大表单。
- 平台级对外应用和系统内业务应用仍混淆。
- 普通用户仍能看到管理、建模、字段、发布、权限入口。
- 没有真实浏览器连续剧本。
- `docs/review.json.status` 不是 `pass`。
- `docs/review.json.frontendUsable` 不是 `true`。
- `docs/review.json.fullProjectDeployable` 不是 `true`。

## 4. 当前结论

P16 当前只允许进入 UI/UX 设计，不允许直接继续前端实现或打最终包。
