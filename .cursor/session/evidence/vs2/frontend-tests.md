# VS2 前端构建与单测证据

- checked_at: `2026-07-11T10:39:26+08:00`
- verdict: `pass_with_observation`

| 命令 | 结果 |
|---|---|
| `npm.cmd test` | 4 files，12 tests passed |
| `npm.cmd run build` | 应用和 E2E TypeScript/Vue typecheck、Vite production build passed |
| `npm.cmd run e2e -- vs1.spec.ts vs2.spec.ts` | 4 journeys passed，2 viewport guards intentionally skipped |

## 功能与可用性

- 平台后台具备系统治理、组织账号、平台角色、允许/拒绝权限配置和账号有效权限预览。
- 系统后台具备设置、租户、组织成员、角色权限、数据范围、访问申请审核和成员有效权限预览。
- 访问申请审核使用真实租户/角色选择器，不要求用户手工输入内部 ID。
- 管理弹窗字段具有可访问标签；系统创建和生命周期弹窗使用明确中文命令按钮。
- 全局 Drawer 注册、角色允许/拒绝互斥 watcher 的递归更新和移动只读边界均已修复。
- E2E 与应用 TypeScript 分属 Node/DOM 类型环境，生产构建会同时检查两类源码。

## 观察项

生产主依赖 chunk 为 `866.95 kB` raw / `270.84 kB` gzip，Vite 给出超过 `500 kB` 的性能告警。路由页面已按视图拆包，当前本地旅程加载和交互正常；该项不是 VS2 功能 P0/P1，但后续切片必须拆分 Ant Design/Vue 公共依赖，不能继续无界增长。
