# examine2 开发方案

本文约定**开发/测试/部署**所需的环境与中间件、**技术基线**（与 [README.md](../README.md) 第 13 节一致），并对各**功能域**做需求级分解，支撑排期、拆任务与 OpenAPI/测试对齐。

**读者**：后端开发、架构、测试、运维。  
**规范优先级**：[README.md](../README.md) 产品/领域/契约为先；编号决策见 [architecture-decisions.md](architecture-decisions.md)；实现细节以代码与评审结论为准。

---

## 1. 技术基线（目标态）

以下为本项目**采纳**的栈；与根 `pom.xml` 不一致之处，按**里程碑「基线收敛」**任务迁移后再以代码为准。

| 类别 | 采纳 | 不采纳 / 迁出 |
|------|------|----------------|
| 运行时 | Java 21、Maven 多模块 | — |
| 应用框架 | Spring Boot 3.x（Web、Validation、Actuator 等按模块需要引入） | — |
| Web 容器 | Undertow | — |
| 数据访问 | **Druid** 连接池、**MyBatis-Plus** | — |
| 多数据源 | dynamic-datasource（若继续需要；仅一库时可简化） | 按实际部署裁剪 |
| JSON | **Jackson**（`spring-boot-starter-json`） | **Fastjson2** 不作为基线 |
| 缓存 / 会话 | **Redis**（`spring-boot-starter-data-redis` + 连接池） | 依赖 Sa-Token 的 Redis 集成迁出 |
| 安全与登录 | **自研鉴权**（Filter / `HandlerInterceptor` + Redis 会话；Token 与 Header 形态在 OpenAPI 锁死） | **Sa-Token**、**Spring Security**（不采用，避免引入重量级安全栈） |
| 工具类 | JDK、`commons-lang3` / `commons-collections4` 等按需 | **Hutool** |
| API 文档 | Knife4j / springdoc（与现有模块对齐） | — |

**说明**

- **鉴权**：README 已约定 token + Redis 会话、`platId` / `systemId` / `tenantId`；实现上采用**自研**鉴权（如 **OncePerRequestFilter** 或 **`HandlerInterceptor`** + Redis + 统一 `Context`），**不采用 Spring Security**；须在 OpenAPI 中固定 Header/Cookie 名与 401/403 语义。
- **幂等**：与 README 第 4.2 节一致；幂等键存 Redis，TTL 可配置。
- **序列化**：对外与对内 REST 统一 **Jackson**；避免多套 JSON 库并存。

---

## 2. 运行环境

### 2.1 开发机

| 项 | 建议 |
|----|------|
| JDK | 21（与 `java.version` 一致） |
| Maven | 3.9+ |
| IDE | IntelliJ IDEA / Eclipse，启用 Lombok 注解处理 |
| Git | 与团队分支策略一致 |

### 2.2 本地依赖（容器化可选）

| 服务 | 用途 | 说明 |
|------|------|------|
| MySQL | 主库 | 版本与生产大版本对齐（如 8.0） |
| Redis | 会话、缓存、幂等、AuthContext 等 | 与生产大版本对齐（如 6.x/7.x） |

可使用 Docker Compose 提供 MySQL + Redis 的**本地 profile**（具体 `application-*.yml` 在实现阶段补全）。

### 2.3 测试 / 预发 / 生产

- **配置外置**：数据源、Redis、日志级别、线程池等通过环境变量或配置中心注入，禁止把密钥写入仓库。
- **观测**：Actuator 健康检查 + 日志聚合（选型在运维侧确定）；与 README「审计分层」一致，业务/流程/技术日志分源。

---

## 3. 中间件与基础设施

| 中间件 | 角色 | 与产品/架构的对应 |
|--------|------|-------------------|
| **MySQL** | 主业务与 flow 持久化 | 元数据、EAV、flow_record、Outbox 表等；脚本以 `doc/` 与迁移为准 |
| **Redis** | 会话、权限缓存、幂等、限流（可选） | README：token 会话、AuthContext 缓存 TTL、幂等键 TTL |
| **消息队列** | MVP **不强制** | [architecture-decisions.md](architecture-decisions.md) D8：MVP 可用 DB Outbox + 轮询；后续再引入 MQ |
| **对象存储** | 附件（若独立服务） | README 第 7 章：附件独立服务，细节以实现为准 |

---

## 4. Maven 子模块与职责（预期）

以「子模块 → 工程落位」为准；与 [README.md](../README.md) 第 2 章**领域表**互补：领域是**产品/逻辑边界**，下表是**代码归属**。

| 子模块 | 职责（预期） |
|--------|----------------|
| **`examine-app`** | **对外应用**：`appId`/`secret`、`/api/v1/**`、第三方鉴权与集成；与 README §6.2 一致。与平台登录分离，仅放对外相关代码。 |
| **`examine-web`** | **平台侧** + **可运行入口**：Spring Boot **启动类**（`@SpringBootApplication`）、主 `application*.yml` 装配；登录与会话、平台态 `systemId=0`、控制台、自建系统列表/进入系统、对内 HTTP、过滤器链、统一异常与返回。 |
| **`examine-core`** | **公共基础**：通用组件、统一上下文基础类型（如 `platId`/`systemId`/`tenantId` 载体）、常量、与具体业务域无关的可复用能力。 |
| **`examine-module`** | **无代码 / 自建应用**：元数据（模块、字段、表单、列表、字典、关系）、Action；业务侧**触发审批**时调用 flow，不替代 flow。 |
| **`examine-flow`** | **流程**：模板与实例、`flow_record`/`record_node`、待办；对内/对外 flow 能力。若仓库中**尚未**独立子模块，可先以包名落在 `examine-web` 或 `examine-module` 内，**后续再抽取**为 `examine-flow`。 |
| **`examine-upload`** | **上传与附件**（与 README「附件独立服务」策略一致时，可再拆独立服务；本模块先负责接口、校验与对接）。 |

**开发约定**：按上表划包，避免循环依赖；跨子模块用 Facade / 应用服务；**幂等、AuthContext** 等与 README §5、[ADR](architecture-decisions.md) 对齐。

---

## 5. 功能域与需求分解

以下为**需求级**条目，用于拆任务；接口以 OpenAPI 为准。

### 5.1 数据（`*_data` / 自定义字段）

**data** 在此**不**作为单独产品名「数据引擎」，而指：**凡需自定义字段、并最终落库到 `*_data`（或 EAV / 项目约定的扩展表）** 的数据总称，包括：

- **自建系统内**各业务**模块**下的**行数据**（结构由 `examine-module` 定义，值落 `*_data` 等）；
- **flow** 侧：模板/定义上需扩展的字段；**发起/办理**时从模板**复制**出的**运行时实例**相关数据（与 README 模板→实例、ADR D6 一致）；
- **用户、成员**等**基础主数据**上需扩展字段、走同一套槽位/EAV 的部分。

**与 module / flow 的分工**：`examine-module` / `examine-flow` 负责**元数据、状态机、权限**；**`*_data`** 统一承载**可自定义字段的值**。槽位、typed-value、CRUD、导入导出、查询 **DSL 白名单**（ADR D12）、权限过滤与 AuthContext 仍按 README 与 ADR。

### 5.2 平台（`examine-web` 为主）

- 账号注册/登录/登出、token 刷新；会话中 **`platId`**、当前 **`systemId`**、`tenantId`（README §1.2、§5.1）。
- 平台态控制台、自建系统列表/创建、「进入系统」与 header 中 `systemId`/`tenantId`。
- 平台级 **message / todo** 入口（聚合多系统；可见性仍按 systemId/角色，README §5.2）。

### 5.3 对外应用（`examine-app`）

- 对外应用生命周期、`/api/v1/**`、`flowId` 路由、开放 API **幂等**（README §4.2、§6.2、ADR D11/D16）。**注意**：与「平台登录」不同体系，代码放在 `examine-app`，不在 `examine-web` 混写对外鉴权。

### 5.4 自建系统内组织与权限（与 `examine-web` / `examine-module` 协同）

- 组织、成员、角色、数据权限；成员扩展字段若走 `*_data`，见 **§5.1**。
- 多租户与 AuthContext（README §5.3–§5.4、ADR D7/D15）。

### 5.5 无代码（`examine-module`）

- 模块、字段、表单、列表、字典、关系；Action / ActionPolicy。
- 业务触发 flow：仅调用 **§5.6**，不内嵌审批引擎。

### 5.6 流程（`examine-flow` 或等价包）

- 模板与实例、整单/节点状态、或签、再发起（README §9、ADR D5–D7、D17–D19）。
- **对内**：`X-System-Id`/`X-Tenant-Id`；**对外**：见 **§5.3**。

### 5.7 上传（`examine-upload`）

- 附件上传、与业务/flow 的关联；存储后端按 README §7 与部署策略落地。

### 5.8 横切（`examine-core` + 各模块入口）

- **Context** 校验（README §1.3）、**幂等**（Redis）、**Outbox**（ADR D8）、**审计**（ADR D14）。

---

## 6. 迭代与里程碑（与 README 第 12 节对齐）

**排期细化（建议周期、依赖、并行）**：见 **[开发排期](development-schedule.md)**。

| 阶段 | 交付焦点 | 工程侧检查 |
|------|----------|------------|
| A | 登录、系统列表、进入系统、多租户选租户 | Redis 会话、header 校验、集成测试 |
| B | 成员/角色/数据权限、AuthContext | 缓存键与失效、接口硬过滤 |
| C | 元数据、槽位、`*_data`、CRUD（见 §5.1） | DSL 限制、列表权限 |
| D | flow 主链路、或签、再发起 | 状态机与表约束 |
| E | 对外应用、`/api/v1/**`、幂等 | 第三方鉴权、限流与审计 |

**基线收敛（横切任务）**：在阶段 A 之前或并行完成：移除 Sa-Token、Hutool、Fastjson2 依赖路径；接入 Spring Boot Redis；统一 Jackson；补充鉴权与上下文集成测试。

---

## 7. 基线迁移与风险（从当前父 POM 迁出）

| 项 | 风险 | 缓解 |
|----|------|------|
| 移除 Sa-Token | 现有登录/注解与会话耦合 | 列出所有 `StpUtil` / Sa 注解引用，逐段替换为**自研 Filter/拦截器 + Redis** |
| 移除 Hutool | 大量 `cn.hutool.*` 调用 | 按类替换：日期用 `java.time`，HTTP 用 Spring/`WebClient`，加少量 commons |
| Fastjson2 → Jackson | 反序列化差异 | 统一 DTO 与 `@JsonProperty`，回归测试 |
| Redis 未在父 POM 启用 | 会话设计依赖 Redis | 显式引入 `spring-boot-starter-data-redis`，配置连接与序列化（建议 JSON 或 JDK 可审计） |

---

## 8. 文档与契约维护

- **依据范围**：以**当前** [README.md](../README.md)、本文与 [开发排期](development-schedule.md) 为准推进；**不默认**把 `doc/*` 下历史脚本当作必须同步的规范源，除非任务明确要求**对照、迁移或生成 DDL**。
- **产出边界**：任务若仅要求「表名与用途」「需求说明」等，**只交付约定范围内的文档或列表**，**不主动**编写 SQL/改物理脚本（如 `doc/schema_v3.sql`）；需要 DDL 时由任务**显式**提出。
- **OpenAPI**：Header 名、错误码、对内/对外路径前缀与 README 一致后再冻结实现。
- **架构决策**：基线重大变更（如引入 MQ、鉴权方案重大调整）在 [architecture-decisions.md](architecture-decisions.md) 追加变更日志与条目。
- **本开发方案**：环境或基线变更时同步更新本文「技术基线」「中间件」小节。

---

## 9. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-04 | 初稿与修订：技术基线；`platId`；子模块（app=对外 / web=平台 / core=公共 / module=无代码 / flow=流程 / upload=附件）；**data**=`*_data` 自定义字段语义（§5.1），不单独称「数据引擎」 |
| 2026-04-04 | 明确：Spring Boot **启动类与主配置**在 **`examine-web`**，**`examine-app`** 仅对外应用代码 |
| 2026-04-04 | 鉴权：**自研**（Filter/拦截器 + Redis），**不采用 Spring Security** |
| 2026-04-05 | §8：补充「依据范围」与「产出边界」（不默认对齐历史 `doc/*`；仅表名/用途时不主动写 SQL） |
