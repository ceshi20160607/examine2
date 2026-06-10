# FE-023 Shell 导航与系统总览 UI 改造

- 所属期次：P12-uiux-frontend-rework
- 负责人：frontend
- 状态：pending

## 目标

按 `docs/ui/ui-design.md` 改造前端整体 shell、平台/系统双层导航、系统总览和页面基础布局。

## 输入

- `docs/ui/ui-design.md`
- `docs/api.md`
- `frontend/src/App.ts`
- `frontend/src/router/index.ts`
- `frontend/src/layouts/AppShell.ts`
- `frontend/src/styles.css`

## 输出

- `frontend/src/`
- `frontend/docs/page-contracts/FE-023-shell-navigation-overview-ui.md`

## 验收标准

- 平台层和系统层导航清晰分离。
- 进入系统后默认有系统总览或明确引导，不直接落入零散配置页。
- 顶部上下文显示系统、租户、成员和权限摘要。
- 主要页面框架统一为标题区、操作区、内容区、详情/反馈区。

## 自检

- `npm.cmd run build`
- 浏览器检查登录、我的系统、进入系统、系统总览。
