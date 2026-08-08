# VS3 前端构建与单测证据

- checked_at: `2026-07-15T11:45:00+08:00`
- verdict: `pass_with_observation`

| 命令 | 结果 |
|---|---|
| `npm.cmd test -- --run` | 5 files，16 tests passed |
| `npm.cmd run build` | Vue/TypeScript typecheck 和 Vite production build passed |
| `npm.cmd run e2e -- tests/e2e/vs3.spec.ts --workers=1` | 3 projects，4 effective journeys passed，5 intentional project skips |
| `npm.cmd run e2e -- tests/e2e/vs3.spec.ts --project=desktop --grep "compact desktop boundary" --workers=1` | 系统 Edge，1 targeted journey passed；`1199/1200/1277/1278` 均无重叠或横向溢出 |

## 覆盖行为

- 配置模型单测覆盖递归 AND/OR 条件回读、按字段类型约束的固定默认值、默认值来源、关系过滤和子表配置 round-trip。
- 设计器通过可视表单配置字段、页面、动作和规则，不要求管理员编辑原始 JSON。
- 抽屉内字典弹窗、下拉列表和消息层级完成浏览器回归；弹层不会被抽屉遮挡。
- 无成员申请和管理员审批控件补齐可访问名称，申请前确保切换到平台上下文。
- 通用后台响应式单测覆盖窄屏只读与桌面可编辑两态；配置工作台的 `<768`、`768-1199`、`>=1200` 布局由 Playwright 旅程验证，并单独断言 `1199/1200/1277/1278` 临界宽度。
- 前端提供版本差异入口；规则重开保留 effect target；手机配置页不暴露检查或发布命令。

## 保留观察

生产主 chunk 为约 `637.53 kB` raw / `190.81 kB` gzip，Vite 仍报告超过 500 kB。该项登记为 P2 性能债，不阻断 VS3 功能门禁；进入更多业务切片前应按路由和公共依赖拆包。
