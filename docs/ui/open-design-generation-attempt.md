# P16 Open Design 原型生成尝试记录

时间：2026-06-13

## 1. 本地服务检查

Docker 容器：

```text
name: open-design
image: vanjayak/open-design:latest
port: 127.0.0.1:7456->7456/tcp
```

Daemon 状态：

```json
{
    "ok": true,
    "version": "0.8.1",
    "bindHost": "0.0.0.0",
    "port": 7456,
    "installedPlugins": 401
}
```

Web 工作台可访问：`http://127.0.0.1:7456/`

## 2. 已完成输入

- 已创建 `docs/ui/open-design-brief.md`
- 已创建 `docs/ui/prototype-traceability.md`
- 已将 `docs/ui/open-design-brief.md` 中的 Open Design Prompt 填入 Web 首页原型输入框

## 3. Web 生成尝试结果

点击“运行”后，页面没有进入项目生成或原型制品页面。

检查“本地 CLI”菜单，Open Design 显示：

```text
本地 CLI · 未选择 · 默认
PATH 中未发现可用 CLI
```

## 4. PM/Validator 判定

本次未生成 Open Design 原型，不得把 `docs/ui/open-design-brief.md` 当作已冻结高保真原型。

原因不是需求输入缺失，而是 Docker 版 Open Design daemon 运行在 Linux 容器内，无法发现 Windows 宿主机上的 Codex CLI。当前 Web 工作台可打开，MCP server 可配置，但 Web 中的“运行”需要可用 agent CLI 或自带 Key 配置。

## 5. 可选解决方式

### 5.1 推荐：安装 Windows 原生 Open Design

Windows 原生 Open Design 运行在宿主机上，能扫描 Windows PATH 中的 `codex.exe`。适合通过 Web 工作台直接选择 Codex/Claude/Gemini 等 CLI 生成原型。

### 5.2 可选：Docker 内配置可用 agent 或自带 Key

在容器内安装并认证可用 agent CLI，或在 Open Design 的“自带 Key”中配置模型 Key。该方式涉及凭证和容器持久化，需要单独处理，不应在未确认凭证方案前自动执行。

### 5.3 当前可用：Codex MCP 连接 Docker Open Design

Codex 全局配置已追加 `mcp_servers.open_design`，重启 Codex 或新开线程后可通过 MCP 读取/创建 Open Design 制品。该方式适合把原型产物纳入 Open Design 项目，但不等同于 Web 工作台自动生成。

## 6. 下一步

P16 继续推进时，必须先选定一种原型生成通道：

1. Windows 原生 Open Design + Codex CLI 直接生成。
2. Docker Open Design + 自带 Key 生成。
3. Docker Open Design MCP + Codex 生成制品后导入/登记。

未选定并成功生成原型前，`frontendUsable=false`、`fullProjectDeployable=false`、`packageGate=trial_package_only_ui_rework_required` 保持不变。
