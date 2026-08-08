# examine2 设计与工程契约包

## 1. Meta

- package_id: `DESIGN-EXAMINE2-V1`
- linked_requirement_package: `REQ-EXAMINE2-V1`
- owner: `architect`
- product_owner: `product`
- uiux_owner: `uiux`
- data_owner: `dba`
- project_controller: `pm`
- node_acceptor: `leader`
- reviewers: `backend, frontend, test, ops`
- status: `accepted`
- version: `1.0`
- updated_at: `2026-07-10`
- implementation_allowed: `true`（仅按已验收纵向切片和任务范围）

本文是当前项目 S2 的唯一主设计产出。它把已验收需求转成产品信息架构、交互、模块、数据、API、安全、任务、测试和发布契约。本文通过 Leader Gate 前，`backend/**`、`frontend/**`、`sql/**` 和 `release/**` 仍禁止创建。

## 2. 设计目标、非目标和原则

### 2.1 设计目标

1. 让 12 类用户角色通过四套独立工作壳完成 52 条真实旅程，而不是只看到静态页面或通用 CRUD。
2. 让无代码配置的草稿、检查、发布、运行快照、版本和回滚形成闭环。
3. 让系统、租户、成员、角色、字段、动作、数据范围和 OpenAPI scope 在后端形成最终权限边界。
4. 让动态业务数据可查询、可索引、可迁移、可追溯，避免整条记录只存 JSON。
5. 让每个关键动作具备事务、幂等、状态、读回、副作用、审计和失败恢复。
6. 让数据库先行和 MyBatis-Plus 生成基础代码成为可重复工程流程，生成代码不冒充业务完成。
7. 让开发按可运行纵向切片推进，每个切片均有浏览器、API、数据库和负例证据。
8. 最终形成可部署、可升级、可备份恢复、可由用户整体验收的完整系统。

### 2.2 非目标

- 不实现微服务、消息队列、Quartz、原生移动 App 或自研流程画布底层。
- 不把对外应用作为模块或业务数据的父级。
- 不把 `examine-web`、`examine-core` 或代码生成器变成业务功能堆放处。
- 不用原型截图、接口 200、编译通过、生成 CRUD 或 agent 结论代替角色旅程完成。
- 不在 S2 编写业务代码、执行生产迁移或设置用户验收状态。

### 2.3 不可破坏原则

- 平台身份和系统成员身份分离；系统业务请求必须持有服务端建立的系统/租户上下文。
- 运行态只读发布快照；历史实例不被后续配置修改污染。
- 业务事实由所属模块保存；Todo、Message、Dashboard 只保存用户工作项或读模型。
- 高风险写入、AI 写入、审批、导入和外部调用必须经过正常权限、校验、幂等和审计。
- 所有敏感配置只保存 `SecretRef` 或密文，不写入文档、日志和普通 API 回包。

## 3. 已关闭的工程决策

| decision_id | 决定 | 理由和约束 | 状态 |
|---|---|---|---|
| ENG-001 | Vue 3 + TypeScript + Vite + Pinia + Vue Router；Ant Design Vue 为基础组件；TanStack Table v8 为表格状态引擎；Vue Flow 为流程画布；ECharts 为图表；Monaco 只用于高级结构化配置；Lucide Vue 为通用图标 | 企业后台控件完整；表格视觉仍由项目掌控；不采用 AG Grid 的许可和视觉绑定；不手写画布底层 | CLOSED |
| ENG-002 | Java 21、Maven、Spring Boot `3.5.16`、Undertow、MyBatis-Plus/Generator `3.5.16`、Flyway、MySQL 8、Redis | Spring Boot 4 已不再提供 Undertow；3.5.16 同时满足 Undertow、Java 21 和成熟 Boot 3 生态；使用 `mybatis-plus-spring-boot3-starter` | CLOSED |
| ENG-003 | 动态数据使用主记录、类型化字段值、索引值、关系、子表、附件引用和历史快照；配置草稿规范化存储，发布时编译不可变 JSON 快照 | 兼顾字段扩展、常用筛选索引、历史解释、运行稳定和配置发布效率；业务运行数据禁止整记录 JSON 化 | CLOSED |
| ENG-004 | 业务作业以数据库记录为事实源，Redis Streams/消费组承载队列，Redis 锁承载租约；Spring 本地轮询发现到期任务；有限重试和死信状态；跨模块事件用事务 outbox | 满足只用 Redis、不用 MQ/Quartz；数据库可恢复，Redis 可加速；进程崩溃后可重新派发 | CLOSED |
| ENG-005 | 移动使用端与 Web 共用 Vue 应用，采用响应式路由和可安装 PWA；移动端提供登录、列表卡片替代、表单、详情、审批、附件/扫码/拍照；复杂配置只在桌面后台开放 | 完成移动使用目标，不制造第二套业务实现 | CLOSED |
| ENG-006 | 文件存储使用 `ObjectStorage` SPI；开发可用本地文件，测试/生产可用 S3 兼容存储；发布包包含后端 jar、前端 dist、配置模板、迁移、启停/体检/备份/恢复脚本、文档和校验和 | 同一业务接口适配本地与对象存储；发布与回滚材料不后补 | CLOSED |
| ENG-007 | SSO、消息渠道、对象存储、AI 模型均采用 provider SPI；先实现本地认证和必要空实现，再按纵向切片补齐 OIDC/SAML/OAuth2/LDAP/企业微信/钉钉、通知渠道和模型 provider；未配置能力显示明确禁用原因 | 最终范围不删除，未配置外部依赖时系统仍可启动、诊断和完成其他旅程 | CLOSED |

### 3.1 版本和来源策略

- Java/Maven 依赖由 Maven dependency management 锁定；前端依赖由 lockfile 锁定，不使用浮动 `latest`。
- 小版本升级必须通过构建、迁移、集成、浏览器和回滚检查，不能只改版本号。
- 关键选型依据：Spring Boot 3.5 Undertow 官方 API/参考、MyBatis-Plus 官方 Boot 3 starter/Generator、Vue 官方 Vite/Vitest 工具链、TanStack Table v8 Vue 适配、Vue Flow 官方 Vue 3 画布能力。

## 4. 产品信息架构和四套工作壳

### 4.1 壳与路由

| shell_id | 角色和上下文 | 根路由 | 顶级入口 | 管理边界 |
|---|---|---|---|---|
| SHELL-PLATFORM-RUNTIME | 平台 Root/Admin/Member，平台上下文 | `/platform` | 工作台、Flow、应用、工作、AI、待办、消息；系统切换、创建系统、个人信息 | 只显示平台能力；不得直接呈现系统业务记录 |
| SHELL-PLATFORM-ADMIN | 有平台后台权限的平台角色 | `/platform/admin` | 平台信息、系统、组织账号、角色、仪表盘、身份源/模型/存储/安全/配额、日志/健康 | 未授权入口不挂载；返回平台工作台保持平台上下文 |
| SHELL-SYSTEM-RUNTIME | 有效 systemMember，可选 tenant | `/systems/:systemId` | 工作台、模块组、Flow、应用、工作、AI、待办、消息；租户/系统切换、个人信息 | 所有菜单、字段、动作和数据按当前权限快照重绘 |
| SHELL-SYSTEM-ADMIN | 有系统后台权限的 systemMember | `/systems/:systemId/admin` | 系统/租户、组织、角色、模块、Flow、应用、仪表盘、字典、工作、AI、数据源、日志 | 只管理当前系统/租户；不能进入平台治理或其他系统 |

认证路由独立于四套壳：`/auth/login`、`/auth/register`、`/auth/forgot`、`/auth/reset`、`/auth/sso/callback`。无有效会话时不加载业务壳数据。

### 4.2 角色入口和主任务表面

| 角色 | 默认入口 | 主任务表面 | 关键状态/拒绝 |
|---|---|---|---|
| 未认证用户 | 登录；注册后进入首个系统初始化 | 登录、注册建系统、找回/重置 | 锁定、验证码/MFA、SSO 失败、会话失效 |
| 平台 Root/Admin | 平台工作台，可进入平台后台 | 系统治理、组织角色、授权、配置、日志和运维 | 权限不足、依赖未配置、系统生命周期影响检查 |
| 平台 Member | 平台工作台 | 平台 Flow/工作/待办/消息/AI、申请或切换系统 | 后台入口隐藏；系统无成员时进入申请页 |
| 系统 Owner/Admin | 系统运行壳，可进入系统后台 | 初始化、组织权限、模块/页面/流程/数据源/仪表盘/应用发布 | 草稿、检查失败、发布、回滚、禁用原因 |
| 租户 Admin | 当前系统/租户 | 租户成员、角色和授权业务 | 不可跨租户或修改系统级禁区 |
| 系统 Member/Approver | 系统运行壳 | 业务列表/详情/表单、工作、待办、消息、审批、AI | 无权限字段、失效来源、重复审批、上下文过期 |
| 外部系统 | `/openapi/v1` | scope 内记录、文件、流程调用和状态查询 | 验签、时钟、防重放、幂等、限流、scope 拒绝 |
| Ops/Auditor | 平台后台日志/健康或发布工具 | 部署、诊断、备份、恢复、审计 | 业务数据写入仍需业务授权；密钥不回显 |

### 4.3 主路由树

```text
/auth/*
/platform/workbench
/platform/flows
/platform/applications
/platform/work
/platform/ai
/platform/todos
/platform/messages
/platform/admin/{systems,organization,roles,dashboards,settings,logs,health}
/systems/:systemId/workbench
/systems/:systemId/modules/:groupCode/:moduleCode
/systems/:systemId/{flows,applications,work,ai,todos,messages}
/systems/:systemId/admin/{system,tenants,organization,roles,modules,flows,applications,dashboards,dictionaries,work,ai,data-sources,logs}
/profile
/no-member/:systemId
```

路由中的 `systemId` 只是定位信息，不是可信权限来源。进入系统必须先调用切换接口建立 context，随后后端比较路径、context 和成员事实。

## 5. UI 与交互契约

### 5.1 视觉和密度

- 采用安静、紧凑、面向重复操作的企业管理视觉；不是营销页、卡片墙或接口调试台。
- 桌面顶栏固定 52px；局部导航、筛选、表格和右侧工作区尺寸稳定，不因状态标签或按钮文本跳动。
- 页面区段不套装饰卡片；卡片只用于重复业务项、移动列表、弹窗和确需边界的工具。
- 状态语义统一：灰为次要/停用，蓝为进行，橙为待关注，绿为成功，红为异常/阻断；颜色之外必须有文本或图标含义。
- 图标按钮使用 Lucide 图标并提供 tooltip；主命令可用图标加文本；不手画可替代的 SVG。

### 5.2 标准页面模式

| pattern_id | 用途 | 强制结构 | 强制行为 |
|---|---|---|---|
| UI-LIST-DETAIL | 模块、Flow、应用、工作、待办、消息 | 标题/上下文、紧凑筛选、保存视图、表格、分页、右侧详情工作区 | 整行打开详情并保留筛选/分页/滚动；行内只放差异动作；详情有专属摘要、动作和 tabs |
| UI-ADMIN-TREE | 组织、权限、模块组、字典等后台 | 左树/分类 + 右表格或表单 + 版本/发布状态 | 选中反馈明确；关键删除先做引用/影响检查；保存后读回 |
| UI-CONFIG-STUDIO | 页面、Flow、打印、仪表盘 | 资源面板 + 中央工作区 + 属性面板 + 检查/预览/发布 | 草稿自动保存或明确保存；发布前检查；未发布修改不影响运行态；可比较/回滚版本 |
| UI-RUNTIME-FORM | 动态新建/编辑/详情 | schema 驱动分组、字段、子表、附件、关联、审批/历史 | 错误定位字段并保留输入；字段显示/只读/必填按规则和权限合并；保存后返回真实读回 |
| UI-WORKBENCH | 工作台/统计 | 受控网格、KPI、趋势、待办、列表、异常状态 | 组件按权限过滤；数据源部分失败单独显示；钻取保留上下文 |
| UI-STATE | 无权限、空、失败、禁用、失效来源 | 页面内状态或专属结果页 | 给出原因、requestId 和允许的恢复动作；禁止只 toast “已响应” |

### 5.3 表格、详情和配置状态

- 表格由 TanStack Table v8 管理排序、筛选、分页、列顺序/宽度/显隐/固定、选择和虚拟化状态；服务端是分页/排序/筛选事实源。
- 表格查询状态进入 URL；个人列偏好和保存视图进入服务端，刷新/返回不丢失。
- 右侧详情在大屏占 42%-62% 可调宽度；窄屏使用整页详情；关闭后恢复列表上下文。
- 关键配置状态统一为 `DRAFT -> CHECKING -> CHECK_FAILED | PUBLISHED -> DISABLED`；回滚产生新的发布版本，不修改旧版本。
- 异步动作统一显示 `QUEUED/RUNNING/SUCCEEDED/PARTIAL/FAILED/CANCELLED`，并提供任务详情、错误、结果文件和允许的重试/取消动作。

### 5.4 移动与可访问性

- 断点以布局能力定义：小于 768px 使用移动导航和列表卡片替代宽表；768-1199px 使用紧凑桌面；1200px 以上使用完整工作区。
- 移动端覆盖认证、工作台、业务列表/搜索、表单/草稿、详情、审批、待办、消息、附件、扫码/拍照和失败重试。
- PWA service worker 只缓存带 hash 的静态资源和明确允许的公共元数据，不缓存认证、业务 API、文件下载和敏感响应；退出或 context 切换清理用户级本地缓存。
- 页面/字段/流程/权限/打印/仪表盘等复杂设计器在移动端不开放编辑，只提供必要状态查看和回到业务入口。
- 所有可操作元素可通过键盘访问；焦点可见；错误有文本并关联字段；关键动作不依赖 hover；触控目标不小于 40px。

### 5.5 原型使用边界

`docs/design/prototypes/index.html` 只用于入口和功能覆盖比对。实现必须重新验证四套壳、真实权限、数据读回、错误、空态、移动布局和整行详情行为，不复制原型中静态表格、泛化抽屉或假成功交互。

## 6. 后端架构和模块边界

### 6.1 总体形态

采用 Maven 多模块的模块化单体。单进程便于当前团队开发、事务和交付；模块通过公开 facade、命令/查询对象和 domain event 协作，禁止跨模块直接写表。未来只有容量或组织边界证明必要时才拆服务。

```text
examine-parent
  examine-core
  examine-plat
  examine-module
  examine-flow
  examine-upload
  examine-app
  examine-work
  examine-todo
  examine-message
  examine-ai
  examine-generator
  examine-web
```

### 6.2 模块职责和依赖

| module | 职责 | 非职责 | 表前缀 | 允许依赖 |
|---|---|---|---|---|
| `examine-core` | API 结果、异常、request/trace、上下文接口、权限接口、审计接口、事件/outbox、作业基础设施、通用值对象 | 具体产品 controller、系统/模块/流程业务 | `un_sys_*`, `un_audit_*` | 第三方基础库，不依赖业务模块 |
| `examine-plat` | 账号认证、系统/租户、成员/组织、角色/权限/数据范围、上下文切换、平台设置/配额/身份源 | 业务模块记录、Flow、对外应用 | `un_plat_*` | core |
| `examine-module` | 模块组、模块、字段、页面、动作、规则、菜单、字典、发布快照、运行记录/关系/子表/协作、导入导出/打印配置 | Flow 实例、文件二进制、外部凭证 | `un_module_*` | core；通过 facade 使用 upload |
| `examine-flow` | Flow 定义/画布/版本/模拟/发布、实例/节点/审批任务、业务回写编排 | 业务记录事实、统一待办收件箱 | `un_flow_*` | core；调用 module/app 公开 facade |
| `examine-upload` | 文件元数据、分片、存储、预览、引用和下载授权 | 业务记录和模板归属 | `un_upload_*` | core |
| `examine-app` | 平台/系统应用、SecretRef、scope、签名、限流、幂等、回调和调用日志 | 模块导航父级、业务数据事实 | `un_openapi_*` | core；调用 owner facade |
| `examine-work` | 项目、普通任务、日报、日历、统计和工作配置 | Flow 通用定义、统一消息箱 | `un_work_*` | core；调用 upload/todo/message facade |
| `examine-todo` | 归一化当前用户待办/提醒、动作委派、状态和计数 | Flow 审批事实、业务对象事实 | `un_todo_*` | core；通过 source handler 回调 owner |
| `examine-message` | 当前用户消息、模板、渠道、投递、已读/归档、计数 | 可执行审批事实、来源业务事实 | `un_message_*` | core |
| `examine-ai` | 会话、模型/provider、策略范围、脱敏、人工确认、tool 调用和 AI 审计 | 绕过 owner service 直接写业务表 | `un_ai_*` | core；调用 owner tool facade |
| `examine-generator` | 从冻结 schema 生成 base entity/mapper/service/XML 和生成报告 | 运行期 API、业务 controller | 无 | MyBatis-Plus Generator；不被 web 运行依赖 |
| `examine-web` | 启动、Undertow、过滤器/拦截器、异常装配、OpenAPI 文档、模块 wiring、静态资源入口 | 任何产品业务 service/controller | 无 | 所有运行模块 |

### 6.3 包结构和公开边界

```text
com.unique.examine.{domain}
  api/                 # 跨模块公开 facade、command、query、event contract
  base/                # 生成：entity、mapper、service、service.impl
  manage/
    controller/        # HTTP 入口，仅使用 BO/DTO/VO
    service/           # 业务编排、事务、权限、状态和副作用
    bo|dto|vo|enums/
    internal/          # 模块内实现，不可跨模块引用
```

- ArchUnit 检查 `web` 无业务包、业务模块不依赖 `web/generator`、其他模块不引用 `base.entity`。
- 跨模块查询和命令只调用 `api` facade；跨模块异步副作用通过 outbox event；禁止跨模块 mapper 和数据库外键级联写。
- 同一模块多表业务动作由 manage service 事务完成；外部存储操作采用“先暂存、事务绑定、失败补偿”而不是跨资源假事务。

## 7. 数据设计

### 7.1 通用字段和隔离

- 主键使用 64 位雪花 ID，API 一律以字符串传输；业务编码另设唯一键。
- 系统业务表至少有 `system_id`；多租户数据有非空 `tenant_id`；动态记录再有 `module_id`。单租户仍使用默认 tenant，避免双模型。
- 平台/系统共用的组织、角色、权限和配置表必须有 `scope_type=PLATFORM|SYSTEM`；SYSTEM 行必须带 `system_id`，租户级绑定再带 `tenant_id`。数据库 CHECK/唯一约束和 service 同时禁止不合法组合。
- 可修改表统一有 `version` 乐观锁、`created_by/at`、`updated_by/at`；可删除事实使用 `deleted_at/deleted_by` 软删。
- 时间以 UTC 写数据库，API 使用 ISO-8601，界面按用户时区显示。
- 所有唯一约束包含隔离维度和软删策略；所有列表查询的首个索引列包含 `system_id/tenant_id`。
- 不在文档或数据库普通字段保存明文密码、token、Secret、模型 Key；只保存哈希、密文和 SecretRef/version。

### 7.2 核心表域

| 数据域 | 核心表 | 关键约束/生命周期 |
|---|---|---|
| 系统基础 | `un_sys_outbox_event`, `un_sys_job`, `un_sys_job_attempt`, `un_sys_idempotency`, `un_sys_sequence`, `un_sys_feature_flag` | outbox 事务写入；job 状态机；幂等唯一键；序号行锁/号段；开关版本化 |
| 审计 | `un_audit_operation`, `un_audit_change`, `un_audit_security` | append-only；记录 actor/context/source/requestId/traceId/result；大表按月归档 |
| 身份/平台 | `un_plat_account`, `un_plat_credential`, `un_plat_refresh_token`, `un_plat_mfa`, `un_plat_system`, `un_plat_tenant`, `un_plat_member`, `un_plat_department`, `un_plat_member_department`, `un_plat_role`, `un_plat_permission`, `un_plat_role_permission`, `un_plat_member_role`, `un_plat_data_scope`, `un_plat_context_session`, `un_plat_access_request`, `un_plat_quota`, `un_plat_identity_provider`, `un_plat_identity_mapping` | 注册事务原子创建账号/系统/默认租户/owner 成员/角色；context session 服务端建立；角色和权限有版本 |
| 无代码配置 | `un_module_group`, `un_module_definition`, `un_module_field`, `un_module_action`, `un_module_page`, `un_module_menu`, `un_module_rule`, `un_module_dictionary`, `un_module_dictionary_item`, `un_module_config_version`, `un_module_publish_record`, `un_module_permission` | draft 规范化编辑；发布编译不可变 snapshot/checksum；回滚创建新发布版本；编码创建后不可随意改 |
| 动态运行数据 | `un_module_record`, `un_module_record_value`, `un_module_record_index`, `un_module_record_relation`, `un_module_sub_record`, `un_module_sub_value`, `un_module_record_team`, `un_module_comment`, `un_module_record_history`, `un_module_saved_view` | 归属 system+tenant+module；记录绑定 schema_version；一个业务保存动作同事务；常用筛选写 index；历史保留快照/差异 |
| 导入导出/打印 | `un_module_import_batch`, `un_module_import_row`, `un_module_export_task`, `un_module_print_template`, `un_module_print_version`, `un_module_print_record` | 导入先预检再确认；行级结果；满足条件可回滚；大导出异步；打印使用历史版本 |
| Flow | `un_flow_definition`, `un_flow_draft`, `un_flow_version`, `un_flow_node`, `un_flow_edge`, `un_flow_binding`, `un_flow_instance`, `un_flow_node_instance`, `un_flow_approval_task`, `un_flow_action_log`, `un_flow_timer` | 发布版本不可改；实例保存定义/节点/权限/业务快照；审批任务唯一处理；父子流程状态可追踪 |
| 文件 | `un_upload_file`, `un_upload_part`, `un_upload_reference`, `un_upload_preview` | 内容哈希去重可配置；引用阻止误删；分片状态和预览作业可恢复；存储 key 不暴露物理路径 |
| 对外应用 | `un_openapi_application`, `un_openapi_secret_version`, `un_openapi_scope`, `un_openapi_grant`, `un_openapi_idempotency`, `un_openapi_rate_bucket`, `un_openapi_callback`, `un_openapi_call_log` | 平台/系统 context 明确；Secret 版本/轮换/吊销；请求唯一幂等；调用日志脱敏归档 |
| 工作 | `un_work_project`, `un_work_task`, `un_work_task_member`, `un_work_daily_report`, `un_work_calendar_item`, `un_work_config`, `un_work_stat_snapshot` | 列表/看板/日历是同一任务；状态乐观锁；指派/到期/审核产生 side effect |
| 待办 | `un_todo_item`, `un_todo_action_log` | recipient+context+source 唯一；打开/处理前重查来源权限；来源完成后关闭而非复制业务状态 |
| 消息 | `un_message_template`, `un_message_item`, `un_message_delivery`, `un_message_preference` | recipient 隔离；业务去重键；消息、投递和已读状态分开；来源链接再次鉴权 |
| AI | `un_ai_provider`, `un_ai_model`, `un_ai_policy`, `un_ai_session`, `un_ai_message`, `un_ai_tool_call`, `un_ai_confirmation`, `un_ai_usage_log` | provider SecretRef；策略和权限快照；高风险 tool 需确认；保留脱敏请求/响应摘要和用量 |
| 仪表盘/KPI/数据源 | `un_module_data_source`, `un_module_data_source_version`, `un_module_dashboard`, `un_module_dashboard_version`, `un_module_dashboard_widget`, `un_module_dashboard_permission`, `un_module_dashboard_cache`, `un_module_kpi`, `un_module_kpi_target`, `un_module_kpi_calculation` | 数据源、仪表盘均发布版本；缓存带权限/版本键；KPI 计算可解释来源 |

### 7.3 动态字段值模型

`un_module_record_value` 每行表示一个记录字段值，包含 `field_id`、`field_version`、`value_type`、`ordinal` 以及互斥的 `string_value/text_value/decimal_value/integer_value/date_value/datetime_value/boolean_value/json_value/display_value`。`json_value` 只允许地址、富文本结构、受控组件配置等不参与核心筛选的有界复杂值。

`un_module_record_index` 只保存发布配置中声明为可筛选、排序、唯一或统计的字段，包含规范化 string/decimal/datetime/bool/reference 值和可选哈希。发布检查生成索引计划；新增索引字段通过后台回填任务生效；查询编译器只接受结构化 AST 和已发布字段，不拼接用户 SQL。

关系和子表独立建模：记录关联进入 `record_relation`，子表拥有 `sub_record/sub_value`，文件进入 upload reference，团队和评论有独立权限。禁止将这些对象塞进主记录 JSON 后再全表扫描。

### 7.4 配置版本和迁移

1. 管理员编辑规范化 draft 表，保存仅影响草稿。
2. 发布检查校验编码唯一、字段类型、页面引用、权限、流程/打印/数据源依赖、索引计划和迁移影响。
3. 发布编译 canonical JSON snapshot，写 `config_version`、checksum、发布人和影响报告，并原子更新 active version。
4. 运行端按 active version 缓存并渲染；记录保存 version id；流程、打印和重大动作另存所需快照。
5. 字段类型不兼容变更创建迁移计划和预演报告；后台回填成功后才允许发布。旧值和旧版本在保留期内可解释。
6. 回滚复制目标历史快照形成新版本，保留完整审计，不修改历史版本。

### 7.5 Flyway 和 SQL 边界

- `sql/` 只保存版本化 Flyway migration、开发种子和显式恢复脚本；应用启动不自动创建缺失业务表。
- migration 一经合并不得修改；修正使用新版本。破坏性变更采用 expand -> migrate -> contract，并要求备份和单独确认。
- DDL 先冻结，再运行 generator；schema、生成报告和代码必须在同一开发任务中有证据。
- 不使用 H2 代替 MySQL 做数据库契约测试；CI 优先 Testcontainers MySQL/Redis，本地可用隔离测试库 profile。

## 8. 认证、上下文、权限和安全

### 8.1 认证会话

- 密码使用 Argon2id（Bouncy Castle 实现）和每用户随机 salt；参数可升级，登录成功时渐进重哈希。
- 浏览器 access token 为高熵随机不透明值，只保存 SHA-256 哈希和短 TTL 会话；refresh token 同样只存哈希，轮换并检测重复使用。生产通过 Secure/HttpOnly/SameSite Cookie 传递，并校验 Origin/CSRF token。
- OpenAPI 使用 app secret version + HMAC-SHA256 canonical request，包含 timestamp、nonce、body hash；允许时钟偏差、nonce 防重放、幂等键和限流均服务端执行。
- 登录、刷新、找回、改密、MFA、SSO、强退和失败限制均写 security audit；错误不泄露账号是否存在或密钥细节。

### 8.2 上下文

1. 登录只建立 platform account session。
2. `POST /api/v1/context/systems/{systemId}:switch` 查询有效 member/tenant/roles/data scope，生成服务端 `contextSessionId` 和 permission snapshot version。
3. 系统请求从安全 cookie/token 取 context id，再从 Redis/数据库加载事实；路径 systemId/tenantId 必须与 context 匹配。
4. 无成员时只允许 no-member 申请接口；禁用、过期、角色变更时 context 立即失效或版本不匹配并要求刷新。
5. 切换系统/租户后前端清空旧菜单、缓存、请求、详情和表格状态，再加载新 `/me/context` 和路由清单。

### 8.3 权限求值

权限键采用 `domain.resource.action`，并组合：壳/菜单、页面、动作、字段可见、字段可编辑、数据范围、OpenAPI scope。有效权限由角色 grant/deny、成员绑定、租户/系统状态、字段/页面发布版本和 feature flag 计算；显式 deny 优先。

- Controller/Interceptor 校验上下文和粗粒度 action；manage service 校验对象状态和字段；mapper query scope compiler 注入 system/tenant/data scope。
- 写入时请求 DTO 先按字段权限过滤并拒绝越权字段，不能静默忽略。
- `PermissionExplanation` 返回来源、规则、snapshot version 和拒绝原因，但不暴露敏感策略内部值。
- 权限变更递增版本并失效 Redis 快照；前端权限仅用于体验，后端始终重查。

### 8.4 安全基线

- SQL 只使用参数绑定和结构化筛选 AST；排序/字段来自发布 schema 白名单；禁止前端提交 SQL/SpEL/任意脚本。
- 输出按字段权限和脱敏策略投影；日志、导出、OpenAPI、AI 和消息模板都经过相同脱敏服务。
- 上传校验扩展名、MIME、大小、配额和内容安全状态；下载使用授权后的短时 token，不暴露磁盘路径。
- 管理写入、批量动作、外部调用、AI tool call、恢复和密钥轮换均限流并记录审计。
- 配置模板只含占位符；启动检查默认密钥、数据库、Redis、存储和必要目录，失败给出明确诊断。

## 9. API 和跨层契约

### 9.1 API 分区和统一格式

| API zone | 路径 | 身份 | 用途 |
|---|---|---|---|
| Auth/Context | `/api/v1/auth/*`, `/api/v1/context/*`, `/api/v1/me/*` | browser account/session | 认证、个人信息、系统/租户切换、有效权限 |
| Platform | `/api/v1/platform/*` | platform context + platform permission | 平台工作台和后台治理 |
| System | `/api/v1/systems/{systemId}/*` | system/tenant context + member permission | 系统运行和后台配置 |
| OpenAPI | `/openapi/v1/*` | application signature + scope | 外部记录、文件、流程和状态调用 |
| Operations | `/management/*` | 独立运维网络/凭证 | health/readiness/version/metrics，不混入业务 API |

成功回包：`{ "code": "OK", "message": "", "data": ..., "requestId": "...", "traceId": "..." }`。失败回包增加 `errors[]`，每项含稳定 error code、field/path 和可读 message。使用真实 HTTP status；401/403/404/409/422/429/5xx 语义不折叠为 200。

- ID 以字符串传输；分页为 `page,size,sort,filter`，响应含 `items,page,size,total`；最大 size 由配置限制。
- mutation 返回业务对象最新读回或 `jobId`，禁止只返回“操作成功”。
- `Idempotency-Key` 对 OpenAPI 写入、注册、审批、导入确认、导出创建、AI 确认和高风险内部命令强制；请求摘要不一致返回 409。
- 乐观并发使用 `version` 或 `If-Match`；冲突返回当前摘要和重新加载提示，不做最后写入覆盖。
- 所有 API 写 OpenAPI 规范；Controller 不暴露 generated entity。

### 9.2 关键端点族

| domain | 端点族 | 关键结果 |
|---|---|---|
| Auth/Context | `/auth/register|login|refresh|logout|forgot|reset|mfa`, `/context/platform:switch`, `/context/systems`, `/context/tenants`, `/me/context` | 原子注册、会话轮换、上下文和权限快照 |
| Platform admin | `/platform/systems|accounts|roles|settings|logs|health` | 生命周期、角色权限、配置版本、日志和健康读回 |
| System admin | `/systems/{id}/tenants|members|roles|modules|dictionaries|dashboards|data-sources` | 当前系统内配置草稿、检查、发布、版本和回滚 |
| Runtime module | `/systems/{id}/runtime/modules/{moduleCode}/records` | schema、列表、详情、保存、批量动作、历史/协作/附件 |
| Flow | `/systems/{id}/flows`, `/flow-instances`, `/approval-tasks` | 设计/模拟/发布、实例、审批和副作用读回 |
| Jobs/files | `/jobs`, `/files`, `/imports`, `/exports`, `/prints` | 任务状态、错误、结果文件、引用和权限 |
| Work/Todo/Message | `/work/*`, `/todos/*`, `/messages/*` | 统一工作事实、可执行待办、消息读状态 |
| App/OpenAPI | `/applications/*`, `/openapi/v1/*` | scope、Secret 轮换、验签、调用和回调日志 |
| AI | `/ai/sessions`, `/ai/confirmations`, `/admin/ai/*` | 策略内解析、人工确认、owner 写入和审计 |

### 9.3 关键用户动作跨层合同

| contract_id | 用户动作 | Frontend | Backend/Data | Permission/State | Side effects/Readback |
|---|---|---|---|---|---|
| CTR-001 | 注册首个系统 | 注册表单、防重复提交、初始化进度 | 一事务创建 account/system/default tenant/owner member/role/binding；写幂等和审计 | 匿名限流；邮箱/手机号/系统 code 唯一 | 返回会话和初始化状态，切换后读 `/me/context` |
| CTR-002 | 系统切换 | 取消旧请求并清空旧 store/route/cache | 校验 member/tenant/roles，创建 context session 和 permission snapshot | 无成员转申请；禁用说明 | 切换日志；返回完整壳、菜单和 context 摘要 |
| CTR-003 | 配置发布 | 显示检查报告/影响/版本，失败可定位 | 校验依赖并编译 snapshot，事务切换 active version | publish 权限；DRAFT/CHECKING/PUBLISHED/FAILED | 缓存失效、audit、受影响管理员 message；运行态读回版本 |
| CTR-004 | 动态记录保存 | schema 表单、字段错误、草稿、并发冲突 | 事务写 record/value/index/relation/sub/attachment ref/team/history | action+field+data scope+version | event/outbox、可选 Flow、audit；返回按权限投影详情 |
| CTR-005 | 审批处理 | 审批面板、字段权限、二次确认 | task 条件更新防重复；流程状态推进并回写 owner | 当前处理人、节点权限和 task version | todo 关闭、新 todo/message、flow/audit log；返回流程和业务状态 |
| CTR-006 | 导入/导出 | 上传/范围/字段选择、任务详情和错误下载 | import 预检行结果，确认后分批事务；export 按权限快照异步 | 独立动作/字段/脱敏权限 | Redis job、result file、message、audit；可回滚批次规则 |
| CTR-007 | OpenAPI 写入 | 不适用 | canonical 验签、nonce、scope、幂等、限流后调用 owner facade | app/secret/scope/status/context | 调用日志、owner audit/event；返回相同幂等结果 |
| CTR-008 | AI 写入 | 展示结构化计划、低置信度补充和确认差异 | 保存 policy/model/prompt/permission snapshot；确认后调用 owner command | 当前用户原权限 + AgentPolicyScope；高风险强确认 | owner 正常副作用 + AI usage/audit；返回 owner 真实读回 |
| CTR-009 | 文件上传绑定 | 分片进度、失败重试、预览 | 暂存文件 -> 安全检查 -> 业务事务绑定 reference；失败补偿 | 文件和目标对象双重权限/配额 | preview job、audit；返回 file metadata/reference 状态 |
| CTR-010 | 部署/恢复 | 运维脚本/报告，不在业务 UI 假执行 | 预检、备份、迁移、启动、readiness、恢复演练 | 独立 ops 权限和人工确认 | 版本/校验和/迁移/备份/健康/回滚报告 |

## 10. Flow、事件、作业和副作用

### 10.1 Flow 状态

- Definition：`DRAFT -> CHECKING -> CHECK_FAILED | PUBLISHED -> DISABLED`；旧发布版本不可修改。
- Instance：`CREATED -> RUNNING -> WAITING -> COMPLETED | REJECTED | TERMINATED | FAILED`。
- Approval task：`PENDING -> PROCESSING -> APPROVED | REJECTED | RETURNED | TRANSFERRED | CANCELLED | EXPIRED`；通过 `where status=PENDING and version=?` 保证只能处理一次。
- 实例保存 flow version、node/edge、approver rule、field permission 和必要业务快照；父子流程用显式 parent instance/node 关系回传状态。
- 模拟器只读取草稿和模拟输入，不写真实业务、待办或消息；发布检查覆盖不可达节点、无审批人、循环/超时、表达式字段、外部调用和回写权限。

### 10.2 Outbox event

业务事务在 owner 模块写 `un_sys_outbox_event`，包含 event id/type/version、aggregate、context、payload、trace、dedupe key 和状态。提交后 dispatcher 以租约领取并调用本进程 handler；成功标记 published，失败有限重试后进入 dead 状态并告警。handler 必须以 event id 幂等。

适用事件：记录变更、流程通过/拒绝、文件就绪、导入/导出完成、任务指派/到期、配置发布。事件只传稳定 ID 和必要快照，不传 Secret 或大对象；消费者通过 owner facade 读事实。

### 10.3 Redis 作业

1. API 事务创建 `un_sys_job(QUEUED)`，提交后将 job id 写入 Redis Stream；Redis 保存热状态、锁、重试时间和消费状态，数据库保存可恢复/可归档的作业事实和审计记录。
2. consumer group 领取，数据库 CAS 改 `RUNNING` 并记录 worker/lease；长任务续租和更新进度。
3. 成功写结果/文件并改 `SUCCEEDED/PARTIAL`；可重试错误按指数退避重新入队；不可重试或超过上限改 `FAILED`。
4. worker 崩溃由本地扫描器配合 Redis 分布式锁找出过期 lease 并恢复；Redis 丢失时从数据库 QUEUED/expired RUNNING 重建队列。
5. 取消只对声明可取消且未进入不可逆提交阶段的任务生效；每次尝试写 attempt 和 traceId。

禁止无限重试、只在 Redis 保存任务事实、在 HTTP 请求中同步执行大导入/导出/预览/统计。

### 10.4 Todo 和 Message

- Todo 保存 recipient、context、sourceType/sourceId、actionCode、dueAt、route hint 和状态；执行前由 source handler 重查权限/状态，Todo 不自行修改 owner 表。
- Message 保存 recipient、context、category、template version、source、dedupe key 和 read/archive；来源跳转先重查权限，失效时展示“来源不可用”而不是泄露对象。
- 同一业务事件可同时产生 Todo 和 Message，但各自用稳定 dedupe key 防重复。平台消息不能携带直接进入系统业务的已授权假设。

### 10.5 关键失败模式

| flow | 失败/并发 | 系统处理 | 用户可见结果 | 证据 |
|---|---|---|---|---|
| 注册首个系统 | 账号/系统 code 冲突、任一步骤失败、重复提交 | 唯一约束 + 单事务回滚 + idempotency result；不留半系统 | 字段级冲突或可重试错误，携带 requestId | 表数量/关系读回、回滚断言、重复键相同结果 |
| 登录/切换 | 密码错误、账号/成员禁用、角色刚变更、伪造 systemId | 失败限流；context 不创建或立即失效；路径/context 双校验 | 明确登录或成员状态，不泄露敏感细节 | security audit、跨系统拒绝、旧 context 失效 |
| 配置发布 | 引用缺失、迁移预演失败、并发发布、缓存刷新失败 | 检查失败保持 draft；active version CAS；缓存按版本读取，刷新可重试 | 定位检查项和影响，不出现假发布 | version/checksum/readback、并发冲突、运行态仍读旧版 |
| 记录保存 | 字段越权、唯一冲突、乐观锁、附件绑定失败、Flow 启动失败 | 校验前置；数据库事务回滚；文件暂存补偿；Flow/event 按已声明原子或 outbox 边界执行 | 保留输入，定位字段/冲突；返回当前版本 | 多表回滚、独立读回、补偿和 outbox 证据 |
| 审批 | 重复点击、非处理人、任务过期、回写业务失败 | task CAS；权限/快照重查；同事务回写或整体失败；副作用幂等 | 当前真实任务/业务状态和失败原因 | 并发只成功一次、todo/message/audit 去重 |
| 作业 | Redis 暂停、worker 崩溃、有限重试耗尽、结果文件失败 | DB 重建队列、lease 恢复、退避重试、FAILED/dead 告警；结果原子挂接 | 进度、最后错误、允许的重试/取消/下载 | 故障注入、重启恢复、attempt 和 trace 链 |
| 文件 | 分片缺失、校验失败、恶意类型、存储暂时失败、孤儿文件 | 拒绝或续传；暂存隔离；有限重试；孤儿清理作业 | 精确文件/分片错误，不生成可用引用 | 存储与 metadata/readback、越权下载拒绝 |
| OpenAPI | 错签、过期/重放、scope 越权、超限、回调失败 | 请求进入 owner 前拒绝；幂等结果复用；回调有限重试并留日志 | 稳定错误码、requestId/traceId，不回显签名细节 | nonce/幂等/限流/调用日志正负例 |
| AI | provider 超时、低置信度、策略/权限变化、确认过期、owner 写入失败 | 降级或需补充；确认时重查权限；不直接写 owner 表；失败保留审计摘要 | 可重试/补充/重新确认，绝不报告假写入 | policy/permission snapshot、owner 读回、无越权写 |
| 部署/恢复 | preflight/迁移/readiness 失败、备份不可用 | 阻断切流；保留旧制品；执行补偿或已演练恢复；记录版本 | 运维报告明确停在哪一步和恢复动作 | clean install、upgrade、rollback、restore 报告 |

## 11. 代码生成与手写边界

### 11.1 Schema-first 流程

1. DBA 为一个纵向切片冻结 migration、表、索引、状态和 owner module。
2. 在隔离数据库执行 Flyway并读回 schema。
3. `examine-generator` 使用 MyBatis-Plus `FastAutoGenerator` 按 module/table prefix 生成 base。
4. 生成报告记录 schema version、命令参数、模板 version、输出文件和 checksum。
5. Backend 只在 manage/api 编写业务；生成目录可删除重生且 diff 可解释。

### 11.2 边界矩阵

| 范围 | generated plumbing | handwritten behavior | 禁止 | 验证 |
|---|---|---|---|---|
| `base/entity` | 表字段、主键、版本/逻辑删除标记 | 无 | 手写业务方法、API annotation | 与 schema 字段一致、可重生 |
| `base/mapper` + XML | BaseMapper 和机械映射 | 复杂 owner query 写 `manage/internal/repository` XML | 跨模块表写、用户 SQL 拼接 | MySQL 集成测试和 SQL review |
| `base/service` | 单表基础 service/impl | 事务编排、权限、状态、幂等、读回 | controller 直接调用生成 service 完成业务 | 业务测试必须经过 manage service |
| `manage/controller` | 无 | BO/DTO/VO、API、校验、调用 service | 暴露 entity、业务逻辑塞 controller | OpenAPI contract/test |
| `manage/service` | 无 | 权限、事务、状态、owner facade、event/audit | 绕过 owner 写其他模块表 | 允许/拒绝、回滚、副作用测试 |
| `examine-web` | 无 | wiring/filter/interceptor/exception only | 产品 controller/service | ArchUnit |

生成命令必须显式给出 backend root、module、table prefix、base package、source root、mapper root、dry-run/execute。覆盖已有 generated 文件前先校验标记；任何手工补丁必须转为 generator 模板或独立 handwritten mapper。

## 12. 测试与质量设计

### 12.1 证据层级

| level | 内容 | 工具/环境 | 通过条件 |
|---|---|---|---|
| Static | 编码、格式、类型、依赖和架构边界 | Maven compiler/Checkstyle/ArchUnit；ESLint/Vue TSC | 无违规；模块依赖和 base/manage 边界通过 |
| Unit | 规则、状态机、权限合并、查询 AST、签名/脱敏 | JUnit 5；Vitest | 正常、边界、失败分支覆盖；关键状态机 mutation coverage |
| Module integration | manage service、MyBatis、Flyway、Redis job/outbox | Testcontainers MySQL/Redis；必要时隔离本地 profile | 真 MySQL 写入/读回、事务回滚、索引查询、幂等和恢复通过 |
| API contract | HTTP status/envelope/OpenAPI、认证/权限/字段投影 | Spring Boot integration + generated OpenAPI checks | 正负权限、错误码、并发和幂等一致 |
| Frontend component | schema 表单、表格、状态、route/store | Vitest + Testing Library | loading/empty/deny/error/success/readback 均覆盖 |
| Journey E2E | 52 条角色旅程按切片累积 | Playwright，真实 backend/MySQL/Redis | 真实点击、网络、数据库和副作用闭环；禁止 mock 作为验收证据 |
| Visual/accessibility | 四套壳、列表详情、设计器、移动替代布局 | Playwright 截图 + axe；1440x900、1280x720、390x844 | 无重叠/裁切/空白画布；键盘和错误可感知 |
| Security/performance | 隔离、越权、重放、注入、文件、速率、容量 | 集成/攻击用例、k6/JMeter 等 | P0/P1 无开放；满足本节基线或有已批准容量说明 |
| Release/recovery | 构建、迁移、配置、健康、备份恢复、回滚 | release scripts + clean environment | 从空环境启动和从前一版本升级/回滚均有报告 |

### 12.2 每个纵向切片的最低验收

- 需求/旅程/规则编号、UI 路由、API、表/migration、owner module 和权限边界可追踪。
- 至少一个允许和一个拒绝角色；跨 system/tenant 负例；状态非法转换负例。
- 写入后通过独立查询和数据库断言读回；副作用 Todo/Message/Audit/Job/Outbox 有证据或明确不适用。
- 前端使用真实 API 完成旅程；桌面和移动目标视口无重叠、截断、假按钮和 console error。
- 任务证据不能设置 `userAccepted`；只有最终整体验收由用户确认。

### 12.3 性能和容量验收基线

在一套标准单实例部署基线上，以测试报告记录 CPU/内存/数据库数据量，不脱离环境承诺绝对性能：

- 常用列表在 100 万条记录、命中已发布索引和 100 并发读条件下，API P95 不超过 1.5s；详情 P95 不超过 800ms；普通写入 P95 不超过 1.5s（不含外部 provider）。
- 首屏业务壳在局域网/正常工作站 P75 不超过 2.5s；切换 context 后不残留旧数据；表格只渲染可视行。
- 超过 10,000 行导入/导出、文件预览、重统计必须异步；单批大小和并发由配额控制，不把整个文件载入 JVM。
- 审计、调用日志、任务明细按时间/系统分区或归档；所有容量阈值可配置并在健康页可见。

这些是工程初始验收目标。若目标部署硬件无法达到，必须用同一数据集报告瓶颈和调整结果，不可静默降低。

## 13. 运行、发布、备份和文档

### 13.1 环境与制品

- 环境：`dev`、`test`、`prod`；配置外置并从环境变量/SecretRef 注入，仓库只留无敏感模板。
- 后端产出可执行 Undertow jar；前端产出带 content hash 的静态文件；生产由反向代理同源提供 SPA/API，避免宽泛 CORS。
- 发布包结构：`backend/`、`frontend/`、`config/`、`migrations/`、`scripts/`、`docs/`、`checksums.sha256`、`VERSION`、`release-manifest.json`。
- 脚本同时提供 PowerShell 和 POSIX shell：preflight、start、stop、restart、status、health、backup、restore、upgrade、rollback。

### 13.2 健康和可观测

- liveness 只检查进程；readiness 检查数据库、Redis、存储和 Flyway schema；外部 SSO/AI/通知为 capability health，不因未配置拖垮核心服务。
- 每个请求生成/传播 requestId、traceId；结构化日志含 actor/source/system/tenant（允许为空）和稳定 error code，不含 token/secret/敏感字段。
- 指标包括 API latency/error、DB pool/slow query、Redis/job lag/retry/dead、outbox lag/dead、file/storage、OpenAPI rate/reject、AI usage/provider failure。
- 运维健康页只展示授权后的摘要和修复入口，敏感诊断仅写受控日志。

### 13.3 备份、升级和回滚

1. preflight 校验 Java、端口、配置、数据库/Redis/存储、磁盘、当前版本和待执行 migration。
2. 升级前备份数据库、文件、配置快照和 release manifest；Secret 备份使用独立加密/密钥管理，不明文导出。
3. 先执行兼容的 expand migration，再部署后端和前端，再做 readiness/冒烟/关键旅程。
4. 应用/前端可切回上一制品；数据库使用向前补偿 migration 或已验证备份恢复，禁止对已执行 Flyway 文件做回退修改。
5. 单节点初始恢复目标：每日全量和必要增量备份，默认 RPO 24h、RTO 4h；生产上线前按实际业务级别重新确认并完成恢复演练。

### 13.4 最终文档

必须随系统持续生成并实测：架构/模块、数据库字典和迁移、配置、部署/升级/回滚、管理员手册、普通用户手册、移动使用、OpenAPI、打印/导入导出、备份恢复、监控告警、错误码/FAQ 和发布说明。文档中的命令必须在干净环境执行验证。

## 14. 实施路线和首个纵向切片

### 14.1 切片顺序

| slice | 角色旅程/结果 | 核心范围 | Gate 重点 |
|---|---|---|---|
| VS1 身份与系统上下文 | JRN-A1/A2/A4/S1/C1 起点 | 工程骨架、Flyway/generator、注册首个系统、登录/退出、系统切换、权限快照、系统壳和审计 | 原子注册、平台/系统隔离、真实浏览器进入正确壳 |
| VS2 平台/系统组织权限 | JRN-PA1/PA2/PA3/S2/S3/C2/C3/C5 | 系统/租户生命周期、组织成员、角色/权限/数据范围、申请 | 跨系统/租户拒绝、有效权限解释 |
| VS3 无代码配置发布 | JRN-C6/C10/PUB1/B2 | 模块组/模块/字段/字典/页面/动作/规则、配置版本和运行导航 | 草稿不污染运行、发布检查/快照/回滚 |
| VS4 动态业务运行 | JRN-B3/B4/B5/B11 | 列表/详情/表单、typed value/index、关联/子表/团队/评论/历史、搜索/收藏 | 百万数据索引方案、字段/动作/数据权限、移动布局 |
| VS5 文件/导入导出/打印 | JRN-B6/B7 的非流程部分 | 文件/引用/预览、导入预检/回滚、导出 job、打印版本/PDF | 补偿、异步恢复、字段脱敏和结果文件 |
| VS6 Flow/Todo/Message | JRN-P2/P6/P7/B6/B9/B10/C9 | Flow 设计/模拟/发布/运行、审批、待办、消息 | 快照、重复处理、父子流程、outbox 副作用 |
| VS7 工作管理 | JRN-P5/B8/C7 | 项目/任务/日报/日历/看板、工作配置 | 多视图同一事实、到期/审核副作用 |
| VS8 仪表盘/数据源/KPI | JRN-P1/PA4/B1/C11 | 数据源版本、统计查询、仪表盘发布、缓存/钻取、KPI 计算 | 权限缓存键、部分失败、计算可解释 |
| VS9 应用与 OpenAPI | JRN-P3/C12/E1/E2 | 应用/scope/SecretRef/轮换、验签/防重放/限流/幂等、回调日志 | 外部负例、密钥只一次展示、owner 写入 |
| VS10 AI Agent | JRN-P9/AI1/AI2/C8 | provider/policy/session/tool/确认/脱敏/降级 | 平台/系统隔离、人工确认、owner 权限和审计 |
| VS11 SSO/运维/功能开关 | JRN-A3/C4/C13/PA5/PA6 | 找回/MFA、全部 SSO provider、日志健康、feature flag、归档/配额 | provider 失败降级、审计/归档和灰度回退 |
| VS12 发布和整体验收准备 | JRN-OPS1 + 全部旅程回归 | clean deploy、升级/备份/恢复/回滚、性能安全、完整文档 | release gate 通过，提交用户整体验收 |

切片只是实施顺序，不删除后续范围。VS1-VS12 累积在同一可运行系统上，每个 Gate 通过后才进入下一批高依赖工作；同一切片中互不依赖的 UI、模块、测试和文档任务可并行。

### 14.2 VS1 冻结合同

- DDL：core job/outbox/audit 最小表，account/credential/system/tenant/member/role/permission/binding/context session。
- Generator：`un_plat_*` 和最小 `un_sys_*`/`un_audit_*` 生成 base；生成报告必须可重复。
- API：register、login、refresh、logout、me、system list、system switch、current context。
- Frontend：认证页；平台工作台壳占位状态；系统业务壳初始设置状态；无成员页；路由/请求上下文守卫。
- 业务：注册原子建立首个系统和 owner；登录建立账号会话；切换建立服务端 context 和权限快照；退出清理全部 context。
- 证据：数据库读回、事务回滚、重复注册幂等、错误登录限制、无成员/禁用成员拒绝、伪造 systemId 拒绝、浏览器桌面/移动进入正确壳、审计记录。
- 明确不在 VS1：模块配置、动态记录、Flow、OpenAPI、AI 等后续业务；页面可以显示“尚未配置”的真实状态，不做假功能入口。

## 15. 需求和旅程设计覆盖

### 15.1 需求覆盖

| 设计区域 | 覆盖需求 |
|---|---|
| 身份/上下文/壳 | REQ-AUTH-001, REQ-AUTH-002, REQ-SSO-001, REQ-CTX-001, REQ-CTX-002, REQ-SHELL-001, REQ-PLATFORM-001, REQ-PLATFORM-002, REQ-SYSTEM-001, REQ-TENANT-001 |
| 组织/权限/配置 | REQ-ORG-001, REQ-RBAC-001, REQ-RBAC-002, REQ-MODULE-GROUP-001, REQ-MODULE-001, REQ-FIELD-001, REQ-DICT-001, REQ-PAGE-001, REQ-MENU-001, REQ-RULE-001, REQ-CONFIG-001, REQ-FLAG-001 |
| 运行数据/协作 | REQ-RUNTIME-001, REQ-RUNTIME-002, REQ-RUNTIME-003, REQ-EFFICIENCY-001, REQ-DATA-001, REQ-DATA-002, REQ-DATA-MODEL-001, REQ-COLLAB-001, REQ-FILE-001 |
| 批处理/打印/统计 | REQ-IMPORT-001, REQ-EXPORT-001, REQ-PRINT-001, REQ-DASH-001, REQ-DATASOURCE-001, REQ-KPI-001, REQ-JOB-001 |
| Flow/协作域 | REQ-FLOW-001, REQ-FLOW-002, REQ-FLOW-003, REQ-TODO-001, REQ-MESSAGE-001, REQ-WORK-001, REQ-EVENT-001 |
| 外部/AI | REQ-APP-001, REQ-OPENAPI-001, REQ-AI-001, REQ-AI-002 |
| 安全/运行/交付 | REQ-AUDIT-001, REQ-SECURITY-001, REQ-OBS-001, REQ-PERF-001, REQ-MIGRATION-001, REQ-DEPLOY-001, REQ-DOCS-001, REQ-MOBILE-001 |

### 15.2 旅程覆盖

| 设计/实施区域 | 覆盖旅程 |
|---|---|
| 认证和 context | JRN-A1, JRN-A2, JRN-A3, JRN-A4, JRN-S1, JRN-S2, JRN-S3 |
| 平台运行 | JRN-P1, JRN-P2, JRN-P3, JRN-P4, JRN-P5, JRN-P6, JRN-P7, JRN-P8, JRN-P9 |
| 平台后台 | JRN-PA1, JRN-PA2, JRN-PA3, JRN-PA4, JRN-PA5, JRN-PA6 |
| 系统业务 | JRN-B1, JRN-B2, JRN-B3, JRN-B4, JRN-B5, JRN-B6, JRN-B7, JRN-B8, JRN-B9, JRN-B10, JRN-B11 |
| 系统后台 | JRN-C1, JRN-C2, JRN-C3, JRN-C4, JRN-C5, JRN-C6, JRN-C7, JRN-C8, JRN-C9, JRN-C10, JRN-C11, JRN-C12, JRN-C13 |
| 外部、AI、发布 | JRN-E1, JRN-E2, JRN-AI1, JRN-AI2, JRN-PUB1, JRN-OPS1 |

### 15.3 非功能覆盖

| NFR | 设计位置 |
|---|---|
| NFR-SEC-001 | 第 8、9、12 节：认证、权限、脱敏、验签、安全测试 |
| NFR-REL-001 | 第 7、10、13 节：事务、幂等、outbox/job、备份恢复 |
| NFR-PERF-001 | 第 5、7、12 节：服务端表格、typed index、性能基线 |
| NFR-OBS-001 | 第 9、10、13 节：request/trace、审计、任务和指标 |
| NFR-USABILITY-001 | 第 4、5、12 节：四套壳、页面模式、真实旅程浏览器验收 |
| NFR-A11Y-001 | 第 5、12 节：键盘/焦点/错误/移动替代布局和 axe |
| NFR-MAINT-001 | 第 6、7、11 节：模块边界、迁移、base/manage 和 ArchUnit |
| NFR-PORT-001 | 第 13 节：外置配置、制品、脚本、升级和回滚 |

自动一致性检查必须证明 57 条 REQ、52 条 JRN 和 8 条 NFR 均在本文出现且无未定义引用。

## 16. 风险、缓解和不阻断决定

| id | 风险 | 缓解/决定 | owner | 状态 |
|---|---|---|---|---|
| RISK-S2-001 | 全量范围大，页面批次容易产生无行为原型 | 严格按 VS1-VS12 角色旅程纵向切片；页面不能脱离 API/数据/权限/副作用验收 | pm/leader | CONTROLLED |
| RISK-S2-002 | 动态字段查询和迁移复杂 | typed value + index 表；发布生成索引计划；不兼容变更先预演/回填 | dba/architect | CONTROLLED |
| RISK-S2-003 | 模块化单体可能形成跨模块耦合 | public facade、owner write、outbox、ArchUnit；禁止跨模块 mapper/entity | architect/backend | CONTROLLED |
| RISK-S2-004 | Redis 队列丢失或 worker 崩溃 | DB job 事实源、lease、恢复扫描、有限重试和 dead 状态 | backend/ops/test | CONTROLLED |
| RISK-S2-005 | 外部 SSO/AI/通知环境不齐 | provider SPI、capability health、禁用原因、测试 stub 只用于 contract；真实 provider 在对应切片验证 | architect/ops | CONTROLLED |
| RISK-S2-006 | Spring Boot 最新版与 Undertow 冲突 | 固定 Boot 3.5.16；升级前必须重新验证 Undertow 和 MyBatis-Plus | architect/backend | CLOSED |
| RISK-S2-007 | 粗略原型误导实现 | 原型降级为覆盖参考；S2 UI 合同和真实浏览器旅程为实现依据 | uiux/frontend/test | CONTROLLED |
| RISK-S2-008 | 默认环境 Java 指向 JDK 8 | 所有 Maven 命令显式设置项目配置中的 JDK 21 路径；preflight 阻断错误 Java | ops/backend | CONTROLLED |

当前无必须由用户决策才能进入 VS1 的事项；普通工程选择已由专业角色职责边界关闭。

## 17. 多角色 Review

| role | 关注点 | verdict | issue/evidence |
|---|---|---|---|
| product | 全量目标、角色入口、切片不缩减范围 | pass | `MEETING-S2-001` |
| uiux | 四套壳、页面模式、真实状态、移动和原型差异 | pass | `MEETING-S2-001` |
| architect | 模块、上下文、可靠性、安全和 provider 边界 | pass | `MEETING-S2-001` |
| dba | 动态模型、索引、版本、迁移、审计和生成 | pass | `MEETING-S2-001` |
| backend | API、事务、权限、幂等、事件/作业和读回 | pass | `MEETING-S2-001` |
| frontend | 路由/store/schema/table/flow 组件和浏览器状态 | pass | `MEETING-S2-001` |
| test | 覆盖、负例、证据层级、性能安全和用户验收边界 | pass | `MEETING-S2-001` |
| ops | 运行、配置、健康、备份恢复、发布回滚 | pass | `MEETING-S2-001` |

## 18. S2 Gate

- [x] 57 条需求和 52 条角色旅程均有设计映射。
- [x] UI、模块、数据、接口、权限、状态、错误和副作用一致。
- [x] 生成/手写边界、迁移、测试、发布和恢复方案清楚。
- [x] ENG-001 至 ENG-007 均关闭。
- [x] 多角色独立 review 和 PM 集成会审完成。
- [x] P0/P1 设计问题关闭或按权限升级。
- [x] VS1 不需要猜测关键产品/技术前提。
- [x] Leader 完成设计节点验收。

当前 verdict：`pass`。`designAccepted=true`、`implementationReady=true`；只允许进入 VS1 的任务拆分和实现，后续切片仍受各自 Gate 约束。

## 19. Leader 验收

- verdict: `pass`
- accepted_by: `leader`
- accepted_at: `2026-07-10T21:01:12+08:00`
- reviewed_meeting: `.cursor/session/meetings/S2-design-review.md`
- evidence:
  - base 和 instance framework validator 均通过。
  - 57 条正式需求、52 条角色旅程、8 条 NFR 设计缺失均为 0。
  - `ENG-001` 至 `ENG-007` 全部关闭，110 个命名核心表均有 owner domain。
  - Java 21.0.10、Maven 3.8.5、Node 24.14.0 和 npm 11.9.0 在当前机器实际验证。
  - UTF-8 strict check 与 `git diff --check` 通过。
- constraints:
  - 设计通过不等于任何业务功能已经实现或用户已经验收。
  - VS1 仅实现注册/认证/上下文/权限快照/初始壳/审计和必要工程骨架。
  - 后续范围必须按 VS2-VS12 累积完成，不能用占位入口代替。
- next_node: `S3-VS1-IDENTITY-CONTEXT`
