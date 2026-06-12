# P15 最终部署包记录

生成时间：2026-06-12 16:10:00 +08:00

## 产物

- Windows 部署包：`dist/unexamine-full-deploy-20260612-160003-p15.zip`
- Linux 推荐部署包：`dist/unexamine-full-deploy-20260612-160003-p15.tar.gz`
- 后端 jar：`backend/examine-web/target/unexamine.jar`
- 前端目录：`frontend/dist/`

## 构建命令

后端：

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
$env:MAVEN_OPTS='-Xmx768m -XX:MaxMetaspaceSize=256m'
mvn -pl examine-web -am -DskipTests package
```

前端：

```powershell
$env:Path='D:\java\nodejs;' + $env:Path
npm.cmd run build
```

## 包内清单

- `backend/unexamine.jar`
- `backend/start.sh`
- `frontend/index.html`
- `frontend/assets/index-ah8_G69b.js`
- `frontend/assets/index-B4Yswf8F.css`
- `docs/nginx-deploy.md`
- `docs/p15-package-check.md`
- `docs/p15-ordinary-runtime-browser-e2e-20260612.md`
- `docs/progress.md`
- `docs/review.json`
- `README.txt`

## 校验结论

- 后端 package 通过。
- 前端 build 通过并生成 `dist/`。
- `tar.gz` 包内 `backend/start.sh` 权限为 `755`。
- `zip` 元数据中 `backend/start.sh` 权限为 `100755`。
- 包内前端 `index.html` 和 assets 已复核存在。

## 未完成的线上验收

本记录只说明包已生成并通过包内校验；尚未把新包替换部署到 `http://192.168.0.211:19999/` 后执行线上 nginx `/api/` 转发和真实浏览器回归。
