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

**V14 / 17 已手工执行**：重新打包启动即可；`FlywayStartupConfig` 会按列是否存在自动写入 `flyway_schema_history`（V14、V23），再执行 V15+。

**启动报 `failed migration to version 14`（旧 JAR / 未登记历史）**：

1. 在 MySQL 执行 `docs/sql/manual/flyway_repair_v14_force.sql`（删除 version=14 失败记录）
2. 如需执行修复脚本，先显式指定目标库，避免误连其他环境：

```powershell
$env:JDBC_URL = "jdbc:mysql://你的数据库:3306/examine?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
$env:DB_USER = "root"
$env:DB_PASS = "你的密码"
.\scripts\db\repair-flyway-failed.ps1
```

3. 仓库根执行：`.\scripts\startup-dev.ps1`（有 `JDBC_URL` + `DB_PASS` 时会先 repair，否则跳过 repair 后打包启动）  
   或分步：`.\scripts\deploy\run-backend.ps1`
4. dev 已配置：`FlywayDevConfig`（先 repair 再 migrate）、`validate-on-migrate` 默认 **false**（可用 `EXAMINE_FLYWAY_VALIDATE_ON_MIGRATE=true` 恢复）
5. 已手工跑 `17` typed 列：`$env:EXAMINE_FLYWAY_BASELINE_VERSION = '23'`

### 方式 B：继续关 Flyway、只用手工 SQL

将 `spring.flyway.enabled` 设为 `false`，仍按 `docs/sql/README.md` 维护。

---

## 4. 构建后端

```bash
cd backend
mvn -pl examine-web -am clean package -DskipTests
```

产物：`backend/examine-web/target/unexamine-0.0.1.jar`

---

## 5. 启动后端（开发配置示例）

```bash
cd backend/examine-web
# 建议显式小堆 + SerialGC，并勿依赖系统 JAVA_TOOL_OPTIONS（易触发 G1 native OOM）
java -Xmx256m -XX:+UseSerialGC -jar target/unexamine-0.0.1.jar
```

默认 **dev** profile（见 `application.yml`）：改其中的 **MySQL URL / 账号 / Redis** 指向你的环境。

自检：

```bash
curl -s http://127.0.0.1:9999/ping
```

### 日志（Logback 滚动文件 + 控制台简要）

| 项 | dev 行为 |
|----|----------|
| 文件 | `backend/examine-web/logs/examine-web.log`，**单文件 ≤10MB**，保留约 14 天、总上限 100MB（gzip 滚动） |
| 控制台 | 仅 INFO 摘要，**不刷 SQL / TRACE** |
| TRACE 入参/出参/链路 | **默认开启**，只写入滚动文件（`examine.trace.enabled=true`） |
| MyBatis SQL | **默认开启**，只写入滚动文件（`Slf4jImpl` + mapper DEBUG） |
| 导出任务轮询 | **30s**（减少空转） |

关闭详细追踪（仍保留错误日志文件）：

```powershell
$env:EXAMINE_TRACE_ENABLED = 'false'
$env:EXAMINE_MYBATIS_LOG_IMPL = 'org.apache.ibatis.logging.nologging.NoLoggingImpl'
```

**不要**再用 `RedirectStandardOutput` 重定向到仓库内 `live.out.log`。清理历史调试文件：`.\scripts\clean-logs.ps1`。

---

## 6. 启动后端（生产 profile，仍可无 CI/CD）

**推荐：外部配置文件**（与 JAR 同目录 `config/application.yml`）：

```bash
cd backend/examine-web
mkdir -p config
cp ../../scripts/deploy/release/config/application.yml.example config/application.yml
# 编辑 MySQL / Redis / examine.openapi.signing-master-key 等
java -jar target/unexamine-0.0.1.jar
```

JAR 内已配置 `spring.config.import: optional:file:./config/`，在 `examine-web` 目录启动即可加载。

发布包中：`backend/config/application.yml.example` → 复制为 `application.yml` 后 `./unexamine.sh start`。

仍可用环境变量覆盖（可选），见 `application-prod.yml` 中的 `${SPRING_DATASOURCE_*}` 占位。

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

## 8. 可选 API 脚本核对

不要求每次修改后都跑冒烟；需要上线前快速核对时，可使用 API 脚本。

```powershell
cd tests\api
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
