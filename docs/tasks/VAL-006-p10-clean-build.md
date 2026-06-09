# VAL-006 P10 Clean Build

## 目标

对 P10 前端改动执行 clean build，并复验后端包可启动，确认 P10 产物具备本机 E2E 和静态部署基础。

## 输入

- `frontend/`
- `backend/`
- `docs/tasks/FE-016-app-module-ui.md`
- `docs/tasks/FE-017-field-page-publish-ui.md`
- `docs/tasks/FE-018-runtime-record-ui.md`

## 输出

- `docs/build/p10-clean-build.md`
- `docs/build_report.md`
- `frontend/dist/`
- `backend/examine-web/target/unexamine.jar`

## 验证命令

```powershell
cd frontend
if (Test-Path dist) { Remove-Item -Recurse -Force dist }
if (Test-Path tsconfig.tsbuildinfo) { Remove-Item -Force tsconfig.tsbuildinfo }
npm.cmd ci
npm.cmd run build

$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn.cmd -pl examine-web -am -DskipTests package
```

## 完成标准

- clean build 成功。
- `frontend/dist/index.html` 和 assets 文件存在。
- 后端 package 成功或说明未改后端但 package 已复验。
- 报告不能把类型检查、PageModel 测试或单个 GET 等同 P10 可用 UI。
