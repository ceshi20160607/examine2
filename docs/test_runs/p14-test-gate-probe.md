# P14 测试闸门探测记录

状态：`probe-pass / full-e2e-pending`

## 目的

确认 P14 前端返工后是否具备进入真实 E2E 的环境条件。本记录不是 P14-TEST-001/002/003 的最终测试报告。

## 环境探测

本地后端：

- `http://127.0.0.1:18080/api/v1/ops/health`：无法连接。
- `http://127.0.0.1:8080/api/v1/ops/health`：无法连接。

部署后端：

- `http://192.168.0.211:19999/api/v1/ops/health`：返回 401，说明网关和后端可达且需要认证。
- `POST http://192.168.0.211:19999/api/v1/auth/login`，账号 `platform_admin`，密码 `123123aa`：pass，返回 accessToken。

## 前端本地联调

新增开发态代理：

- `frontend/vite.config.ts`
- `VITE_API_PROXY_TARGET=http://192.168.0.211:19999`
- 本地 Vite：`http://127.0.0.1:5177`

说明：该代理只用于本地开发。生产部署仍使用同源 `/api`，不写死局域网地址。

## Headless Chrome smoke

命令：

```powershell
& 'C:\Program Files\Google\Chrome\Application\chrome.exe' --headless=new --disable-gpu --no-first-run --no-default-browser-check --user-data-dir="$env:TEMP\codex-chrome-p14-auth" --virtual-time-budget=6000 --dump-dom $url
```

结果：pass。

DOM 中可见：

- `平台工作空间`
- `我的系统`
- `创建一个业务系统`
- `业务系统`
- `业务运行台`
- `对外应用`

## 构建

命令：

```powershell
$env:Path='D:\Tools\node-v24.15.0-win-x64;'+$env:Path
npm.cmd run build
```

结果：pass。

## 重要边界

- 本记录只能证明本地前端可渲染、部署后端可认证、开发态代理可用于后续测试。
- 本记录不能替代 P14-TEST-001/002/003。
- P14 仍未达到可打包状态，`package_gate` 必须继续保持 blocked。
- 地址栏 `accessToken/baseUrl/systemId` 预览参数已限制为 dev 模式，生产 build 不读取这些调试参数。

## 下一步

继续执行：

- `P14-TEST-001`：管理员建系统/建模/发布，普通用户使用数据。
- `P14-TEST-002`：平台级对外应用创建、授权、调用和日志追踪。
- `P14-TEST-003`：平台日志、系统日志和错误恢复。
