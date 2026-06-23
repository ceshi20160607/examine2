# 公共配置（Build 期）

> 新项目工作区：`d:\workspace\01_project\snow\me\examine2`  
> 旧实现参考：`.oldbk/`（只读）

## 服务

| 项 | 值 |
|----|-----|
| 应用名 | unexamine |
| 后端端口 | 9999 |
| API 前缀 | `/api/v1` |
| 前端 dev | Vite `5173`，代理 `/api` → `9999` |

## 数据库（开发默认，见 `backend/examine-web/src/main/resources/application.yml`）

| 项 | 值 |
|----|-----|
| MySQL | `192.168.0.211:3306/examine1` |
| 用户 | examine / examine |
| 初始化 | 执行 `sql/init.sql` |

## 构建命令

### 后端（需 JDK 21 + Maven 3.8+）

```powershell
$env:JAVA_HOME='D:\java\jdk\jdk21'
$env:Path="$env:JAVA_HOME\bin;D:\java\apache-maven-3.8.5\bin;$env:Path"
cd backend
mvn clean package -DskipTests
java -jar examine-web/target/examine-web-*.jar
```

### 前端（需 Node 18+）

```powershell
cd frontend
npm install
npm run build
# 产物：frontend/dist/
```

### 同源部署

Nginx：`/` → `frontend/dist`，`/api/` → `http://127.0.0.1:9999/api/`（保留 `/api` 前缀）。

## 模块结构

`examine-core` · `examine-plat` · `examine-module` · `examine-flow` · `examine-upload` · `examine-app` · `examine-generator` · `examine-web`

## API 契约

冻结前以 `frontend/docs/api-contract-map.md` + `frontend/src/api/endpoints.ts` 为事实标准；汇总写入 `docs/api/`。
