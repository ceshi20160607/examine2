# Open Design × Cursor Agent 排障（Windows）

## 现象

Open Design 提示：

```text
无法启动 Cursor Agent：exit 1
stderr: ... cursor-agent.ps1 ... Write-Error ...
No version directories found in C:\Users\...\AppData\Local\cursor-agent
```

## 根因（已在本机验证）

Open Design 调用的是 **`cursor-agent` CLI**（Cursor Agent 命令行），**不是** Cursor 编辑器里的 `cursor` 命令。

| 命令 | 作用 | 你机器上的路径 |
|------|------|----------------|
| `cursor` | 打开 IDE / 编辑器 | `D:\home\cursor\cursor\resources\app\bin\cursor.cmd` |
| `cursor-agent` / `agent` | **Agent CLI**（Open Design 需要） | `%LOCALAPPDATA%\cursor-agent\` |

`cursor` 能跑通 **不代表** `cursor-agent` 正常。本机错误是：

- 版本目录实际名：`2026.06.15-03-48-54-da23e37`（含时间戳）
- 启动脚本正则只认：`YYYY.MM.DD-<git-hash>`（纯十六进制后缀）
- 匹配失败 → `No version directories found` → exit 1

这是 **Cursor CLI Windows 启动器已知 bug**（2026-06 社区已有 workaround）。

## 修复（已完成于本机 2026-06-15）

编辑以下两个文件（内容相同，**都要改**）：

- `%LOCALAPPDATA%\cursor-agent\cursor-agent.ps1`
- `%LOCALAPPDATA%\cursor-agent\agent.ps1`

找到约第 48 行：

```powershell
# 旧（错误）
$name -match '^\d{4}\.\d{1,2}\.\d{1,2}-[a-f0-9]+$'

# 新（正确）
$name -match '^\d{4}\.\d{1,2}\.\d{1,2}-.+$'
```

保存后**新开终端**验证：

```powershell
cursor-agent --version
agent --version
```

应输出类似：`2026.06.15-03-48-54-da23e37`，exit 0。

## Open Design 侧检查

1. Open Design 集成设置里 Agent 选 **Cursor**（不是 VS Code）
2. 确认 PATH 含 `%LOCALAPPDATA%\cursor-agent`
3. 修复后重启 Open Design 桌面应用
4. 再试生成原型

## 现象 B：未认证（Authentication required）

```text
Cursor Agent is not authenticated.
Run cursor-agent login, then cursor-agent status, and retry.
For automation, ensure CURSOR_API_KEY is set in the Open Design process environment.
```

### 根因

`cursor-agent` 已安装且能 `--version`，但 **未登录** 时 `agent status` 显示 `Not logged in`。  
Open Design 子进程也看不到你的登录态时，会报上述错误。

### 方案 1：浏览器登录（推荐，本机已验证）

在 **PowerShell** 执行：

```powershell
agent login
# 或
cursor-agent login
```

浏览器完成授权后验证：

```powershell
agent status
```

应显示已登录账号（本机已于 2026-06-15 登录为 `ceshi20160607@sina.com`）。

然后 **完全退出并重启 Open Design 桌面应用**，再试生成。

若浏览器未自动打开，终端会打印 `loginDeepControl` 链接，手动打开即可。

### 方案 2：API Key（给 Open Design 自动化 / 子进程读不到登录态时）

1. 打开 Cursor 控制台 → **API Keys** → 生成 User API Key  
   （文档：https://cursor.com/docs/cli/reference/authentication ）
2. 把 Key 设为 **用户环境变量**（Open Design 进程要能读到）：

```powershell
# 当前用户永久环境变量（需重启 Open Design）
[Environment]::SetEnvironmentVariable('CURSOR_API_KEY', '你的key', 'User')
```

3. 完全退出 Open Design 后重新打开（必须重启进程才加载新环境变量）
4. 终端自测：

```powershell
$env:CURSOR_API_KEY='你的key'
agent status
agent -p "say ok" --print
```

**注意：** 不要把 API Key 提交进 git 或写入本项目文件。

### 方案 1 vs 2 怎么选

| 场景 | 用 |
|------|-----|
| 本机桌面 Open Design，你已 `agent login` 且 OD 重启后仍报未认证 | 方案 2（OD 子进程常读不到 CLI 登录缓存） |
| 日常在终端用 agent | 方案 1 |
| CI / 无浏览器 | 方案 2 |

## 若仍失败

### A. 重新安装 Cursor Agent CLI

```powershell
irm 'https://cursor.com/install?win32=true' | iex
```

安装后**再次应用上面的正则修复**（安装器可能覆盖脚本）。

### B. 缺少 ripgrep

若报 `Could not find ripgrep (rg)`：

```powershell
winget install BurntSushi.ripgrep.MSVC
```

重启终端后再试 `agent`。

### C. 未登录 Cursor Agent

见上文 **现象 B**。不要只跑 `agent login` 就以为 OD 一定可用；OD 仍失败时请设 `CURSOR_API_KEY` 并重启 OD。

### D. 不走 Agent CLI，改用手动原型

在 Open Design **桌面应用**里直接选手势 + 设计系统生成 HTML，保存到 `docs/design/prototypes/`，不依赖 `cursor-agent` 也能完成 Design Gate。

## 与本项目的关系

- Design Gate 需要可预览原型 + 你在 `user-approval.md` 签字
- **不强制**必须 Open Design 调通 cursor-agent；桌面 OD 手动出图同样有效
- cursor-agent 修好后，可在 Cursor 里用 MCP：`od mcp install cursor`（可选）
