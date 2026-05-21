# 最简运行与部署（无 CI/CD）

目标：**本机或单机上一套 MySQL + Redis + 后端 JAR 能正常跑**；不讨论流水线、K8s 等。

---

## 1. 还需要开发什么？（相对 v1 清单）

`docs/development-tracker.md` 里 **A–I 与 P 项** 均已 ✅，**无阻塞上线的待开发项**。

已落地能力摘要：

| 方向 | 说明 |
|------|------|
| **EAV typed 列** | `value_num` / `value_dt`；Flyway `V23`；手工见 `docs/sql/17_module_record_data_typed_alter.sql` |
| **Web 列表筛选模板** | `ListViewsView.vue` 同页 CRUD |
| **流程图** | Web `FlowGraphDesignerView`；移动端画布 / 列表 / 预览（`temp_ver_graph_*`） |

若要**新开需求**，请追加 `docs/requirements-log.md` 并在 tracker 里加任务行。

---

## 2. 环境依赖（最少三件）

| 组件 | 说明 |
|------|------|
| **JDK** | 17+（本仓库常用 21） |
| **MySQL** | 8.x；库名例如 `examine`，字符集 `utf8mb4` |
| **Redis** | 会话与缓存；与 `application*.yml` 中密码一致 |

---

## 3. 数据库（二选一）

### 方式 A：Flyway 自动（默认已开 dev / prod）

- `spring.flyway.enabled: true`，`baseline-on-migrate: true`，**`baseline-version` 默认 `22`**（可用 `EXAMINE_FLYWAY_BASELINE_VERSION` 覆盖）。
- **空库**：从 **V1** 顺序执行到最新。
- **已有表、且等价于已执行到 V22**：无 `flyway_schema_history` 时会先 baseline 到 22，再只跑 **V23+**。
- **已手工执行过 V23**（typed 列已存在）：设 **`EXAMINE_FLYWAY_BASELINE_VERSION=23`**，避免重复 `ALTER`。

详见 **`docs/deploy/flyway-existing-db.md`**。

### 方式 B：继续关 Flyway、只用手工 SQL

将 `spring.flyway.enabled` 设为 `false`，仍按 `docs/sql/README.md` 维护。

---

## 4. 构建后端

```bash
cd backend
mvn -pl examine-web -am package -DskipTests
```

产物：`backend/examine-web/target/examine-web-0.0.1-SNAPSHOT.jar`

---

## 5. 启动后端（开发配置示例）

```bash
cd backend/examine-web
java -jar target/examine-web-0.0.1-SNAPSHOT.jar
```

默认 **dev** profile（见 `application.yml`）：改其中的 **MySQL URL / 账号 / Redis** 指向你的环境。

自检：

```bash
curl -s http://127.0.0.1:9999/ping
```

---

## 6. 启动后端（生产 profile，仍可无 CI/CD）

用环境变量覆盖敏感信息（示例）：

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://你的主机:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
export SPRING_DATASOURCE_USERNAME="root"
export SPRING_DATASOURCE_PASSWORD="你的密码"
export SPRING_DATA_REDIS_HOST="127.0.0.1"
export SPRING_DATA_REDIS_PORT="6379"
export SPRING_DATA_REDIS_PASSWORD="你的Redis密码"

java -jar examine-web/target/examine-web-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

`application-prod.yml` 里可再调端口、日志、Actuator 等。

---

## 7. 可选：Web 管理台（静态资源）

```bash
cd web/vue3
cp .env.production.example .env.production
# 编辑 VITE_API_BASE 为后端地址，例如 http://你的服务器:9999
npm ci
npm run build
```

用任意静态服务器打开 `dist/`，或用 Nginx（示例见 `docs/deploy/production.md`）。

---

## 8. 冒烟（验证「能跑通」）

```powershell
cd scripts\smoke
$env:EXAMINE_HOST = "http://127.0.0.1:9999"
$env:SMOKE_USER = "admin"
$env:SMOKE_PASS = "123123aa"
.\e2e-smoke.ps1
```

---

## 9. Windows 一键脚本

仓库根目录执行：

```powershell
.\scripts\deploy\run-backend.ps1
```

脚本会打包并启动 JAR（默认 dev profile）；首次请编辑脚本内 `JAVA_HOME` 或本机已配置 `JAVA_HOME`。
