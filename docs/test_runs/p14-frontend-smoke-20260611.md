# P14 前端烟测记录

执行时间：2026-06-11

## 已通过

- `frontend/src/App.ts` 修复普通用户总览误导到配置入口的问题。
- 新增平台工作空间 `#/platform/openapi` 对外应用中心。
- 系统内页面 `#/systems/:systemId/openapi` 收敛为“系统对外授权”。
- 对外应用表单从手填 scope 改为选择业务模块、允许动作、读写字段、数据范围、平台能力、IP 白名单和限流。
- 页面配置区不再展示可见的 schema 摘要。
- 导出页清除 `P11导出模板`、`p11-export` 等测试默认值。
- `systemPath` 不再生成 `/current/` 占位链接。
- 执行 `npm.cmd run build` 通过，生成 `frontend/dist/index.html` 与 `frontend/dist/assets/index-Syyl9-qs.js`。

## 浏览器烟测

已使用 Chrome DevTools Protocol 打开本地 Vite 服务 `http://127.0.0.1:5180/`，同源代理到 `http://192.168.0.211:19999`，并通过页面真实登录 `platform_admin / 123123aa`。

验证结果：

| 页面 | 路由 | 结果 |
| --- | --- | --- |
| 平台对外应用中心 | `#/platform/openapi` | 渲染成功，页面有 13 个按钮、13 行系统数据，标题包含“对外应用中心”。 |
| 系统对外授权 | `#/systems/2065034340583424001/openapi` | 渲染成功，存在 `openApiModuleId`、`openApiActions`、`openApiReadableFields` 控件。 |
| 业务运行台 | `#/systems/2065034340583424001/runtime` | 渲染成功，存在“刷新业务入口”和“进入”按钮，并展示业务入口行。 |

修复过程中发现并处理：

- 路由级权限在系统有效权限快照加载前可能误判 `PERM_DENIED`。
- 已调整为系统路由在权限快照加载中或 stale 时先放行页面，由页面动作权限控制按钮状态。
- 平台对外应用中心不再依赖不存在的 `LOGIN_USER` 平台权限。

## 待继续

- 当前浏览器烟测覆盖登录、路由、页面渲染和关键控件存在，不等价于完整浏览器点击创建数据。
- P14-PKG-001 仍需等待 validator/reviewer 结论后才能执行。

## 静态扫描

执行扫描：

```powershell
rg -n 'schema 摘要|schemaSummary|p11_tpl|P11导出|p11-export|授权 scope|保存Scope|/current/' frontend/src/App.ts frontend/src/router/index.ts -S
```

结果：未发现上述可见调试痕迹。
