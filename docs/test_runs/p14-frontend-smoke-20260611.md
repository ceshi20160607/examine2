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

## 未通过/待复验

- Chrome DevTools Protocol 已能打开 Vite 页面，但真实页面登录点击流未稳定进入认证态，页面正文仍停留在登录页。
- 本次不把浏览器点击流计为 pass。
- P14-PKG-001 继续阻塞，不能打包。

## 静态扫描

执行扫描：

```powershell
rg -n 'schema 摘要|schemaSummary|p11_tpl|P11导出|p11-export|授权 scope|保存Scope|/current/' frontend/src/App.ts frontend/src/router/index.ts -S
```

结果：未发现上述可见调试痕迹。
