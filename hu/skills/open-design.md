# Skill: open-design

## 触发

- phase = design
- ui-spec.md 已存在

## 执行方式

1. **桌面 Open Design**（你手动）— 见 `open-design/install.md`
2. **CLI/MCP** — `od mcp install cursor` 后在 Cursor 中调用

## 前置

- `gates.design_package_complete = true`（见 `rough-to-prototype` Skill L3）
- **主输入：** `docs/design/prototype-brief.md`（全量整合，禁止聊天补丁）

## 输入

- `docs/design/prototype-brief.md`（必须）
- `docs/design/ui-spec.md`
- `docs/design/config-spec.md`
- `docs/product/prd.md`（角色与剧本）
- 可选：指定 DESIGN 系统名（如 Linear）

## 输出

- `docs/design/DESIGN.md`
- `docs/design/prototypes/**/*.html`

## 完成标准

- 原型可在浏览器打开
- 覆盖 ui-spec 中 P0 页面清单
- **不** 自动设置 `design_user_approved`（必须你签字）
