# VAL-005 P9 Clean Build

## 目标

对 P9 前端改动执行 clean build，并确认构建产物可用于静态部署。

## 输入

- `frontend/`
- `docs/tasks/FE-015-system-management-ui.md`

## 输出

- `docs/build/p9-frontend-clean-build.md`
- `docs/build_report.md`
- `frontend/dist/`

## 验证命令

```powershell
cd frontend
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path tsconfig.tsbuildinfo) { Remove-Item -Force tsconfig.tsbuildinfo }
npm.cmd ci
npm.cmd run build
```

## 完成标准

- clean build 成功。
- `frontend/dist/index.html` 和 assets 文件存在。
- 报告不能把类型检查或接口验证等同可部署 UI。
