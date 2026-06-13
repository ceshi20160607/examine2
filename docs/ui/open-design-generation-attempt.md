# P16 Open Design 原型生成尝试记录

时间：2026-06-13

## 0. 后续状态

该记录描述的是 Docker 版 Open Design 的失败尝试。当前主路径已切换为 Windows 原生 Open Design：

```text
Open Design App: D:\java\opendesign\Open Design\Open Design.exe
Open Design Web: http://127.0.0.1:57033/
Open Design Daemon: http://127.0.0.1:57023
Node.js: D:\java\nodejs\node.exe
```

Docker 版 `http://127.0.0.1:7456/` 和 `docker exec open-design ...` 不再作为 P16 原型生成主路径。

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

## 5. 当前解决方式

### 5.1 已采用：Windows 原生 Open Design

Windows 原生 Open Design 已运行在宿主机上，能使用宿主机环境和 Open Design 自身的 AMR 通道。适合通过 Web 工作台直接生成原型。

### 5.2 当前 Web 通道

当前 Open Design Web 显示执行通道：

```text
本地 CLI · AMR · gpt-5.4-mini
```

### 5.3 当前 MCP 通道

Codex 全局配置已切换为 Windows 原生 Open Design，重启 Codex 或新开线程后可通过 MCP 读取/创建 Open Design 制品。

## 6. 下一步

P16 继续推进时，必须使用当前 Windows 原生通道生成原型：

1. Open Design Web 工作台选择当前项目目录。
2. 使用 `docs/ui/open-design-brief.md` 中的 prompt 生成原型。
3. 将生成结果登记到 `docs/ui/prototypes/` 或通过 Open Design MCP 读取制品。

未选定并成功生成原型前，`frontendUsable=false`、`fullProjectDeployable=false`、`packageGate=trial_package_only_ui_rework_required` 保持不变。
