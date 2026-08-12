# 后端与数据架构冻结合同

## 1. 唯一调用链

普通持久化调用只能是：

```text
Controller
  -> manage/application use-case Service
      -> generated IxxxService
          -> generated ServiceImpl
              -> generated BaseMapper
                  -> Database
```

特殊持久化只能是：

```text
use-case Service
  -> named Repository Port
      -> repository/adapter/jdbc 或 custom mapper XML
```

约束：

- 生成目录拥有贴表 Entity、BaseMapper、Mapper XML、`IService` 和 `ServiceImpl`，不拥有产品语义。
- `manage/application/domain/controller` 不得 import `base.mapper`，不得出现 SQL、`JdbcTemplate`、`ResultSet`、行映射和动态表名。
- 查询 Wrapper、分页、排序和普通批量操作先通过生成的 `IService`。
- 只有锁、复杂联表、批量投影或 EAV 专用读取等 Base 无法表达的行为才建立具名 Repository。
- Controller 只处理传输、参数校验和响应转换；事务、权限、状态和副作用属于一个小型用例 Service。
- 一个用例 Service 只能产生一个清晰用户结果。出现不相关聚合、重复映射或无法解释的事务时停止编码，先复审表和聚合。
- 生成代码不得手工加入换密码、审批、发布等业务 SQL；重新生成不得覆盖业务实现。

### 1.1 当前项目的 vNext 物理隔离

只读审计已证明现有 `com.unique.examine.plat.base` 含历史手写业务 SQL，并仍被非 P1 旧功能编译依赖。为避免一边清理旧功能一边建设 P1：

- 整棵 `com.unique.examine.plat.base` 作为 legacy generated tree 冻结；P1 不得新增依赖。
- P1 干净生成根固定为 `com.unique.examine.plat.vnext.base`，XML 固定为 `mapper/vnext/base`。
- P1 用例根固定为 `com.unique.examine.plat.vnext.manage`；Web 适配根固定为 `com.unique.examine.web.vnext`。
- 新 Mapper 必须加入独立 vNext scan；新旧 Base 不得互相 import。
- 静态 Gate 只可按已接受的本隔离合同审计 vNext package；这不是对 legacy 问题的通过判定，legacy 仍保持冻结并在对应功能替换后删除。

## 2. Maven 模块与表所有权

| 模块 | 唯一职责 | 表前缀/数据所有权 |
|---|---|---|
| `examine-core` | ID、幂等、审计、Outbox、作业等通用能力 | `un_sys_*`、`un_audit_*` |
| `examine-plat` | 账号、凭据、系统、租户、成员、组织、角色、权限、会话 | `un_plat_*` |
| `examine-module` | 模块配置/发布、字段页面、运行记录、查询投影、仪表盘和数据源 | `un_module_*`，但 legacy favorite/report 表冻结 |
| `examine-flow` | Flow 定义/版本、实例、节点执行、审批历史 | `un_flow_*` |
| `examine-work` | 项目、普通任务、日报、提醒 | `un_work_*` |
| `examine-todo` | Flow/Work 等来源的统一待办投影和动作路由 | `un_todo_*` |
| `examine-openapi` | 对外应用、凭证引用、调用日志、回调 | `un_openapi_*` |
| `examine-file` | 文件对象、引用、授权下载 | `un_file_*` |
| `examine-event` | 消息模板、消息、投递偏好和日志 | `un_event_*` |
| `examine-collab` | 记录评论、提及和记录团队 | `un_collab_*` |
| `examine-ai` | 平台/系统 Agent 的策略、会话、确认和审计 | `un_ai_*`，以 scope 区分平台/系统 |
| `examine-analytics` | 只读统计适配和查询组合 | 默认不拥有业务写表 |
| `examine-web` | 启动和模块装配 | 不拥有业务表和业务 SQL |
| `examine-generator` | 构建期生成 Base | 不是运行时产品模块 |

一张表只能由一个模块写。其他模块通过 Port/API/事件读取或请求状态变化。

## 3. 平台与系统复用方式

平台 Flow、系统 Flow 使用同一 `examine-flow` 能力并以明确 scope 隔离；平台和系统不得各建第二套引擎表。Dashboard、Work、OpenAPI 和 AI 同理。

因此下列 legacy 重复表及其 Service 全部冻结，不进入 vNext：

- `un_platform_flow_*`
- `un_platform_dashboard*`
- `un_platform_work_*`
- `un_platform_task`
- `un_platform_todo_action`
- `un_platform_openapi_*`
- `un_platform_ai_*`

平台/系统差异存在于 `scope_type/scope_id`、权限和入口，不通过复制整个聚合实现。

## 4. 关键聚合

- Platform Identity：Account + Credential。
- System Context：System + Tenant + Member + MemberTenant。
- Authorization：Role + Permission + RolePermission + MemberRole/AccountRole + DataScope + Authz snapshot。
- Session：ContextSession + RefreshToken；`SystemSwitchContext` 必须包含 system、tenant、systemMember、roles、data scope、permission snapshot 和受限模式。
- Module Config：ConfigRoot + ModuleGroup + Module + Field + Page + Action/Rule + Check + Version + PublishRecord。
- Module Runtime：Record + Value + Index/Search + Relation/Subrecord + History + RuntimeSchema。
- Flow：Definition/Version 与 Instance/NodeExecution 分开；Flow 只引用业务 record，通过 Module Port 回写流程状态。
- Work：Project + Task + DailyReport；Todo 只是投影，不拥有任务或审批事实状态。
- Dashboard：Dashboard + Version + Widget + DataSource binding，由 Module 模块持有并支持平台/系统 scope。

## 5. vNext 数据库

- vNext 只运行 `sql/migration-vnext`，目标必须是独立新数据库，例如 `examine2_vnext`。
- 禁止在已有 legacy 数据库直接切换 Flyway location 执行。
- legacy migration 只读归档；旧表是否迁移数据在功能完成后的独立迁移计划中决定。
- 每个 migration 有唯一模块 owner；同一周期共享 schema 时必须串行。
- P1 migration 只包含 P1 运行必需表。`role_draft`、发布历史等 P2 能力不得因为旧代码方便提前进入 P1。
- 默认管理员由应用启动用例通过 Base Service 幂等创建并安全哈希密码；不得在 SQL、日志、响应或 evidence 写明文密码。

## 6. 旧代码处置

| 类别 | 处置 |
|---|---|
| 生成器、Maven 模块、Java 21/Spring/MyBatis-Plus、密码/Token 算法 | 合同测试通过后复用；P1 输出进入独立 `plat.vnext.base` |
| 现有 `com.unique.examine.plat.base` | 整棵只读冻结，不作为 P1 Base；旧调用替换完成后再删除 |
| 直接 Mapper 的 Authentication/Registration/Session/Bootstrap | 冻结；P1 以新用例替换入口 |
| 超大 Plat 管理 Service、ConfigDraftService、RecordRuntimeService | 禁止追加；阶段触及时按用例替换 |
| 巨型 Flow service/repository | 保留可证明正确的纯规则；状态用例和持久化按新边界替换 |
| Work/Todo 已有 Port/Repository 分层 | 审计通过后优先复用 |
| legacy 表、测试和 evidence | 仅作参考，不定义 vNext 完成度 |

“替换”指新建小型用例、切换 Controller/页面入口并验证读回，不是在旧大文件内部继续重构。

## 7. 静态 Gate

每个被触及的后端模块必须执行 `implement-backend-module` 审计，并满足：

- 新增 boundary error 为 0。
- 被触及 use-case Service 不直接依赖 BaseMapper/JDBC。
- 每个 Base 持久化需求声明 `reuse/configure/extend/override/project_only`。
- `override/project_only` 必须引用 accepted data/API contract 和原因。
- 特殊 SQL 有具名业务目的、独立适配层和受影响 repository 测试。
- 任务级只运行受影响编译/单测；组合读回和权限正反例在周期末运行一次。
