# Open Design 安装指南（Windows）

> 官方仓库：https://github.com/nexu-io/open-design  
> 官网：https://open-design.ai/

## 你要选哪种方式？

| 方式 | 适合谁 | 是否需要下载安装包 |
|------|--------|-------------------|
| **A. 桌面应用（推荐）** | 你要亲自预览、标注、改原型 | **是**，从官网/Releases 下载 Windows 安装包 |
| **B. 接入 Cursor（CLI/MCP）** | 让 Cursor Agent 调 Open Design 出图 | 需先装 Open Design CLI，再 `od mcp install cursor` |
| **C. 从源码跑** | 开发者自建 | git clone + Node 24 + pnpm |

**建议你先做 A**，亲自在桌面应用里看原型、提修改；满意后再用 B 让 Agent 按同一 `DESIGN.md` 迭代。

---

## 方式 A：桌面应用（零配置，推荐第一步）

1. 打开 https://open-design.ai/ 或 https://github.com/nexu-io/open-design/releases
2. 下载 **Windows x64** 安装包（当前版本约 0.10.x）
3. 安装后打开，应用会自动检测本机已安装的 Agent CLI（含 Cursor）
4. 在 Home 选择 skill（如 `web-prototype`、`dashboard`）和设计系统（如 Linear、Notion 风格）
5. 输入 brief（可用 `docs/design/ui-spec.md` 摘要）
6. 导出/HTML 保存到本项目 `docs/design/prototypes/`

**不需要** 先 clone 仓库。

---

## 方式 B：接入 Cursor（本项目后续自动化用）

### 步骤 1：安装 Open Design CLI

**Windows PowerShell：**

```powershell
# 官方一键安装（需 curl；Git Bash 或 Win11 自带）
curl -fsSL https://open-design.ai/install.sh | sh -s cursor
```

若 `curl` 不可用，可：

1. 从 Releases 安装桌面版（通常自带 CLI）
2. 或 clone 源码后 `pnpm tools-dev`（见方式 C）

### 步骤 2：接入 Cursor MCP

```bash
od mcp install cursor
```

验证：

```bash
od mcp install --print cursor
```

### 步骤 3：在 Cursor 中使用

在 Agent 对话中：

```
使用 open-design，按 docs/design/ui-spec.md 和 Linear 设计系统，
生成平台首页和车系统业务运行台原型，输出到 docs/design/prototypes/
```

Agent 会读 `skills/`、`DESIGN.md`，在本地生成可预览 HTML（默认预览端口见安装输出，常见 `localhost:7456`）。

---

## 方式 C：从源码（仅当 A/B 失败）

要求：Node ~24，pnpm 10.33.x

```powershell
git clone https://github.com/nexu-io/open-design.git
cd open-design
corepack enable
pnpm install
pnpm tools-dev run web
```

Windows 问题见官方 `docs/windows-troubleshooting.md`。

---

## 装好后在本项目中的第一次操作

1. 等 analyst + pm 完成 Phase 0（或你指定先做 Design）
2. uiux 写 `docs/design/ui-spec.md`
3. 你用 **桌面 Open Design** 或 **Cursor + od** 生成原型到 `docs/design/prototypes/`
4. 浏览器打开 html 预览
5. 你编辑 `docs/design/user-approval.md`：
   - 满意 → `approved: true`
   - 不满意 → 写修改意见，回到步骤 3

**仅当 `approved: true` 后，才进入 API 与开发。**

---

## 常见问题

**Q：Cursor 插件市场能直接装吗？**  
A：Open Design 不是普通 Cursor 插件；是通过 **CLI + MCP** 或 **桌面应用** 接入。Cursor 在官方支持列表中，但需你先安装 Open Design 本体。

**Q：不装能否继续开发？**  
A：**不能。** 本项目 Gate 规定 `design_user_approved` 前禁止一切开发。

**Q：DESIGN.md 放哪？**  
A：`docs/design/DESIGN.md`（品牌 token、组件规则，可与 Open Design 导出合并）
