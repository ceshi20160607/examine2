# P14 Clean Build 记录

执行时间：2026-06-11 20:38

## 前端

命令：

```powershell
$env:Path='D:\java\nodejs;'+$env:Path
npm.cmd run build
```

目录：`frontend/`

结果：通过。

产物：

- `frontend/dist/index.html`
- `frontend/dist/assets/index-BoxiNZTv.css`
- `frontend/dist/assets/index-C4ikCUrv.js`

## 后端

命令：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
mvn.cmd -pl examine-web -am clean package -DskipTests
```

目录：`backend/`

结果：通过。

Reactor：

- `examine` SUCCESS
- `examine-core` SUCCESS
- `examine-plat` SUCCESS
- `examine-upload` SUCCESS
- `examine-module` SUCCESS
- `examine-flow` SUCCESS
- `examine-app` SUCCESS
- `examine-web` SUCCESS

后端产物：

- `backend/examine-web/target/unexamine.jar`

说明：本次后端为 clean package with `-DskipTests`；P14 功能验证以 API E2E、浏览器点击流和既有模块测试记录为准。
