# FE-014 平台中心 CRUD UI

## 目标

把平台中心从通用占位/接口调试工作区升级为可操作的业务页面，覆盖平台系统、平台账号、平台角色和平台配置的核心 CRUD/状态/授权入口。

## 输入

- `docs/api.md`
- `frontend/src/api/endpoints.ts`
- `frontend/src/pages/platform/platformCenter.ts`
- 后端平台管理接口：`backend/examine-plat/src/main/java/com/unique/examine/plat/manage/controller/PlatformController.java`

## 输出

- `frontend/src/App.ts`
- `frontend/src/styles.css`
- `frontend/src/pages/platform/platformCenter.ts`
- `frontend/dist/`
- `dist/unexamine-full-deploy-20260608-171500.zip`

## 完成标准

1. 平台系统页支持列表加载、创建系统、状态启停和进入系统。
2. 平台账号页支持列表加载、创建账号、编辑账号、启停、重置密码和分配角色。
3. 平台角色页支持列表加载、创建角色、编辑角色、启停、权限目录加载和授权保存。
4. 平台配置页支持列表加载、配置回填和更新；敏感配置不把脱敏占位值自动提交。
5. 页面按钮按 `platformCenter` PageModel 的权限动作判断启用/禁用，不直接散落接口 URL。
6. 前端默认中文显示，浏览器 smoke 不出现中文乱码。

## 验证

- `npm.cmd run build`：pass。
- 浏览器 smoke：`/auth/login` 登录页和 `#/platform/systems` 平台系统页标题、表单、表格均正常显示；无中文乱码。
- 本地静态 preview 未连接后端时 `/api` 报 HTML JSON 解析错误属于预期，不作为功能失败。
