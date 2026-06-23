# P14-FE-002 系统总览、系统设置、建模配置和业务运行体验记录

状态：`done`

## 输入

- `docs/ui/p14-integrated-ui.md`
- `frontend/docs/page-contracts/P14-FE-001-shell-navigation-visual.md`

## 修改范围

- `frontend/src/App.ts`
- `frontend/src/styles.css`

## 关键变化

1. 系统总览增加角色提示：区分“正在管理这个系统”和“正在使用这个系统”。
2. 系统总览增加就绪度检查：成员、角色、业务应用、业务模块、发布状态，每项都给出待办/完成和入口。
3. 系统资料、租户、成员、部门、系统角色、字典页面增加中文说明层，解释配置对象和使用边界。
4. 成员页明确“成员是平台账号在当前系统里的身份”，避免把系统成员当独立登录账号。
5. 建模配置进一步强调业务应用、模块、字段、页面发布之间的顺序，以及普通用户只能看到授权字段。
6. 业务运行台增加“我的业务入口”说明，只显示已发布且授权的业务模块；运行列表说明数据范围和权限。

## 验证

前端构建命令：

```powershell
$env:Path='D:\Tools\node-v24.15.0-win-x64;'+$env:Path
npm.cmd run build
```

结果：pass。

Headless Chrome smoke：

```powershell
& 'C:\Program Files\Google\Chrome\Application\chrome.exe' --headless=new --disable-gpu --no-first-run --no-default-browser-check --user-data-dir="$env:TEMP\codex-chrome-p14" --dump-dom http://127.0.0.1:5176
```

结果：登录页可渲染，DOM 中包含 `登录后可以创建业务系统`、`建系统`、`用业务`、`对外开放` 等 P14 文案。

## 后续任务

P14-FE-002 不声明完整 P14 前端完成。后续必须继续执行：

- `P14-FE-003`：平台级对外应用和日志分层体验。
- `P14-TEST-001/002/003`：真实浏览器 E2E。
