# P14-FE-001 壳层、导航、视觉和文案重构记录

状态：`done`

## 输入

- `docs/ui/p14-integrated-ui.md`
- `docs/tasks/P14-integrated-rework-plan.md`
- `docs/product/product-vision-and-operating-model.md`

## 修改范围

- `frontend/src/layouts/AppShell.ts`
- `frontend/src/router/index.ts`
- `frontend/src/App.ts`
- `frontend/src/styles.css`

## 关键变化

1. 导航分组从旧的工程型命名调整为平台工作空间、系统总览、系统设置、建模配置、业务运行、协同流程、对外应用、审计与运维。
2. 页面标题和核心文案区分“业务应用”和“对外应用”，不再把系统内建模应用与外部接入应用混用。
3. 系统壳层增加工作空间标识、当前系统、租户和成员上下文，避免用户不知道自己在平台层还是系统层。
4. 登录页文案从功能罗列改为“创建业务系统、配置业务模块、处理业务数据、开放外部接入”的产品表达。
5. 系统总览、建模配置、业务运行台和对外应用的空态、下一步提示和按钮文案改为中文业务语言。
6. 样式统一为 P14 UI/UX 指定的工作台气质：深青蓝主色、中性灰背景、清晰导航、紧凑表格、弱阴影和 6-8px 圆角。

## 验证

前端构建命令：

```powershell
$env:Path='D:\Tools\node-v24.15.0-win-x64;'+$env:Path
npm.cmd run build
```

结果：pass。

构建产物：

- `frontend/dist/index.html`
- `frontend/dist/assets/index-Cblz1Rpk.css`
- `frontend/dist/assets/index-Clgalksr.js`

## 后续任务

P14-FE-001 只处理壳层、导航、视觉和文案，不声明完整 P14 前端完成。后续必须继续执行：

- `P14-FE-002`：重做系统总览、系统设置、建模配置和业务运行体验。
- `P14-FE-003`：重做平台级对外应用和日志分层体验。
- `P14-TEST-001/002/003`：真实浏览器 E2E。
