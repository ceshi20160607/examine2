# P14 前端浏览器烟测记录

执行时间：2026-06-11

账号：`platform_admin / 123123aa`

前端服务：`http://127.0.0.1:5181/`

后端代理：`http://192.168.0.211:19999`

## 已通过

- `frontend/src/App.ts` 修复普通用户总览误导到配置入口的问题。
- 新增平台工作空间 `#/platform/openapi` 对外应用中心。
- 系统内页面 `#/systems/:systemId/openapi` 收敛为“系统对外授权”。
- 对外应用表单从手填 scope 改为选择业务模块、允许动作、读写字段、数据范围、平台能力、IP 白名单和限流。
- 页面配置区不再展示可见的 schema 摘要。
- 导出页清除 `P11导出模板`、`p11-export` 等测试默认值。
- `systemPath` 不再生成 `/current/` 占位链接。
- 运行台模块页的模块下拉在 URL 直接进入时能显示当前模块。
- 运行台已有记录更新按钮从“保存当前详情”改为“更新当前记录”，避免和新增记录混淆。

## 浏览器点击流

已使用浏览器真实打开本地 Vite 服务，并通过页面完成以下点击链路：

| 步骤 | 路由/动作 | 结果 |
| --- | --- | --- |
| 登录 | `#/auth/login` 填写 `platform_admin / 123123aa` 并点击登录 | 成功进入 `#/platform/my-systems` |
| 平台对外应用中心 | 打开 `#/platform/openapi` | 页面标题包含“对外应用中心”，存在 12 个可用“配置对外授权”按钮 |
| 进入系统授权 | 点击第一条“配置对外授权” | 成功进入 `#/systems/2063994726481473538/openapi`，页面标题为“系统对外授权” |
| 授权表单 | 检查表单字段 | 存在 `openApiModuleId`、`openApiActions`、`openApiReadableFields`、`openApiWritableFields`、数据范围、平台能力、IP 白名单和限流；未出现“授权 scope/保存Scope”旧文案 |
| 运行台入口 | 打开 `#/systems/2065034340583424001/runtime` | 页面显示业务运行台且存在唯一可用“进入”按钮 |
| 进入模块 | 点击“进入” | 成功进入 `#/systems/2065034340583424001/runtime/modules/2065034341111906305` |
| 新增业务记录 | 填写运行态字段并点击“新建记录” | 列表和详情回显 `浏览器客户186160`，请求号 `req_20260611_ccb2b9dfdf3f4875` |
| 运行台体验修复复验 | 刷新后重新登录并打开模块页 | 模块下拉选中 `2065034341111906305`，按钮文案为“更新当前记录”，旧“保存当前详情”已消失 |

## 静态扫描

执行扫描：

```powershell
rg -n 'P11|p11|LOGIN_USER|授权 scope|保存Scope|/current/|schema 摘要|schemaSummary' frontend/src/App.ts frontend/src/router/index.ts frontend/src/layouts/AppShell.ts -S
```

结果：未发现上述可见调试痕迹。

## 构建

执行命令：

```powershell
$env:Path='D:\java\nodejs;'+$env:Path
npm.cmd run build
```

结果：通过，生成 `frontend/dist/index.html`、`frontend/dist/assets/index-BoxiNZTv.css`、`frontend/dist/assets/index-C4ikCUrv.js`。

## 待复核

- 本记录和 `p14-integrated-api-e2e-20260611.md` 合起来覆盖 API 主链路、浏览器登录、平台级对外应用入口、系统级授权表单、运行台模块进入和页面新增业务数据。
- P14-PKG-001 仍需等待 reviewer 确认 `docs/review.json.status=pass` 且 `fullProjectDeployable=true` 后才能执行。
