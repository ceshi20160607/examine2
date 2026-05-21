# 开发执行清单（单一事实源）

本文是**“计划 + 排期 + 完成情况”**的唯一事实源（Single Source of Truth, SSOT）。  
产品/需求与领域边界以 **`README.md`** 为根本；本文只负责把需求拆成可执行任务并维护完成标识。

---

## 1. 使用约定

### 1.1 状态标识

- ✅ 已完成：代码已合入当前分支，且不再阻塞后续任务
- 🟡 进行中：已部分落地或正在收尾
- ⬜ 未开始：尚未启动
- ⛔ 阻塞：明确依赖未满足或存在阻塞问题

### 1.2 维护规则

- 本文**随代码提交更新**；当 README 契约变更时，先更新 README，再同步本文任务与验收口径。
- 每个任务尽量补一条“证据”（类名/接口/模块）用于追溯；证据不是详文档，只是定位锚点。

---

## 2. 当前总体结论

- **v1 功能面**：里程碑 A–E、上线收口 F、缺口补齐 G/H、收官 I 均已 ✅（见下文各表）。
- **可部署**：后端 `examine-web` + Web `vue3` + 移动端 `uniapp` 契约对齐；生产见 `docs/deploy/production.md`。
- **后续迭代（非阻塞上线）**：无阻塞项；移动端已提供 **画布设计**（`temp_ver_graph_designer`，触摸拖拽 + 连线模式）、**列表编辑**（`temp_ver_graph_edit`）、**只读预览**（`temp_ver_graph_preview`），均写同一 `graph-designer` API。
- **只想能跑、不要 CI/CD**：见 **`docs/deploy/simple-run.md`**；Windows 可在仓库根执行 `.\scripts\deploy\run-backend.ps1`。
- **维护**：进度只在本文更新；需求变更追加 `docs/requirements-log.md`。

---

## 3. 里程碑 A–E（与 `README.md` §12 对齐）

> 备注：里程碑本身以 README 为准；本节是把里程碑拆到可执行任务，并维护完成情况。

### A 平台入口（systemId=0 → 进入系统 → 选择租户）

| ID | 任务 | 状态 | 验收要点 | 证据（示例） |
|---|---|---|---|---|
| A-1 | 注册/登录/登出、token 刷新；Redis 会话包含 `platId/systemId/tenantId` | ✅ | 未登录 401；平台态仅允许平台接口；会话切换不串 system | `examine-web` 登录与会话代码；README §1.2–§1.3 |
| A-2 | 系统列表/创建/启停/软删；进入系统（会话内更新 `systemId`） | ✅ | 进入系统后 system 口接口可用；未进入系统访问 system 口 403 | `SystemContextInterceptor`、进入系统接口 |
| A-3 | 多租户：选择 tenant 写入会话（token 不变） | ✅ | 开启多租户系统必须先选 tenant；否则 403 | 选择租户接口；README §1.3 |
| A-4 | message/todo 平台级入口（可先空列表/占位） | ✅ | 有入口/空态；可见性框架预留 | `PlatformInboxController`（messages 查询 `un_plat_msg`；todos 空态） |

### B 组织与权限（成员/角色/菜单与 AuthContext）

| ID | 任务 | 状态 | 验收要点 | 证据（示例） |
|---|---|---|---|---|
| B-1 | 自建系统内接口前缀与“必须已进入系统/选择租户”校验链 | ✅ | system 内接口统一被拦截器保护 | README §12.1：`/v1/system/**`、`SystemContextInterceptor` |
| B-2 | 成员/角色/菜单权限基础能力（RBAC + 菜单树） | ✅ | 角色/菜单/角色-菜单权限/成员角色分配可维护；变更触发权限缓存失效与接口门缓存失效 | `SystemModuleRbacController`（`/v1/system/module/rbac`）；`SystemModuleRbacService`；`ModuleAuthService`（cache coordinator） |
| B-3 | AuthContext 计算/缓存/失效（5min TTL 可配） | ✅ | 接口硬过滤生效；缓存可命中可失效 | `ModuleAuthService`（Redis 5min）；`ModuleAuthContextInterceptor`；成员/角色/权限变更失效（`ModuleMemberServiceImpl/ModuleRoleServiceImpl/ModuleRolePermServiceImpl/ModuleRoleMenuPermServiceImpl`）；菜单接口门变更 evict（`ModuleMenuServiceImpl`） |

### C 无代码与 `examine-module`（模块/字段/列表/字典/关系/CRUD）

| ID | 任务 | 状态 | 验收要点 | 证据（示例） |
|---|---|---|---|---|
| C-1 | 模块/字段/表单/列表/关系等元数据接口 | ✅ | app/model/field/relation/action 元数据可维护；提供 `/v1/system/module/meta/**` 供系统态联调 | `SystemModuleMetaController`；`SystemModuleMetaService` |
| C-2 | 字典/字典项 | ✅ | 字典与字典项可维护；提供 `/v1/system/module/dicts/**` 供系统态联调 | `SystemModuleDictController`；`SystemModuleDictService` |
| C-3 | 列表视图/列配置/筛选模板 | ✅ | 视图/列/筛选模板可维护；提供 `/v1/system/module/list-views/**` 供系统态联调 | `SystemModuleListViewController`；`SystemModuleListViewService` |
| C-4 | 导出模板/字段（导出配置） | ✅ | 导出配置可维护（先不要求真实导出实现）；提供 `/v1/system/module/exports/**` 供系统态联调 | `SystemModuleExportController`；`SystemModuleExportService` |
| C-5 | 业务行数据 CRUD、`*_data` / EAV、DSL 白名单 | ✅ | 数据可写可查；查询不允许任意 SQL | `SystemModuleRecordController`；EAV 含 `value_text` + typed `value_num`/`value_dt`（V23）；`EavTypedValueSupport`；DSL `eq` 按字段类型解析 |

### D flow（审批引擎主链路）

| ID | 任务 | 状态 | 验收要点 |
|---|---|---|---|
| D-1 | 模板→运行时复制，实例快照与首任务生成（MVP-1：支持 vars 落库） | ✅ | 改模板不追溯已发起实例；vars 写入 `un_flow_record_var`；图同步 `un_flow_record_node/line` | `SystemFlowController#start`；`FlowEngineService#startByTempCode`；`FlowRecordGraphSyncService` |
| D-2 | 办理推进（MVP-1：approve 按边分支 + 异常兜底；reject 终态） | ✅ | 有关系表则走 `un_flow_record_line`；否则 `graph_json`；`un_flow_log_action` | `FlowEngineService#advanceFromRelationalLines/advanceFromGraphJsonEdges` |
| D-5 | subflow 子流程（父实例挂起、子跑完回父继续） | ✅ | 下一节点 `type=subflow` 且 `config.sub_temp_code` 指向子模板；可选 `copy_vars` 复制 `un_flow_record_var`；子结束（approve 到 end）触发父从 `parent_node_key` 再推进；支持嵌套 subflow | `FlowEngineService`（`startSubflowChildCore`/`resumeParentAfterSubflowChildCompleted`/`enterSubflowChild`）；联调种子 `demo_subflow_child` / `demo_parent_subflow` 见 `docs/sql/08_flow_seed.sql` |
| D-6 | 抄送节点 `cc`（不阻塞推进） | ✅ | 下一节点 `type=cc`；`config.plat_ids` 为抄送对象 platId 数组，空则回退发起人；落库 `un_flow_task`(taskType=cc)+`un_flow_task_actor`；多段 cc 链自动穿过；父流程 subflow 恢复后同样穿过 cc | `FlowEngineService#consumeCcChain`/`emitCcNode`；种子 `demo_cc` 见 `docs/sql/08_flow_seed.sql` |
| D-7 | 会签（`sign_mode=all` + `plat_ids`） | ✅ | 同节点多人多条待办；最后一人同意后才 `advance`；同意返回 `countersign_wait`；拒绝时取消同节点其余待办 | `FlowEngineService#createApproveTasksForNode`/`hasPendingSameNodeApproveTasks`；种子 `demo_countersign_all` 见 `docs/sql/08_flow_seed.sql` |
| D-8 | 或签（`sign_mode=any` + `plat_ids`） | ✅ | 单条待办，`candidateJson` 存候选人；任一人可同意/拒绝；`canActOnTask`；转交后清空候选 | `FlowEngineService#canActOnTask`；种子 `demo_any_sign` 见 `docs/sql/08_flow_seed.sql` |
| D-4 | 撤回/终止/转交 + 轨迹 | ✅ | 发起人撤回/终止；处理人转交；`un_flow_log_trace` | `FlowEngineService#withdrawFlow/#terminateFlow/#transferTask`；`SystemFlowController` |
| D-3 | module 触发 flow 的 Facade 契约 | ✅ | 无绑定不触发；绑定存在则调 `FlowEngineService`；`biz_type=module:app:{appId}:model:{modelId}` | `ModuleFlowTriggerService`；`ModuleRecordFacadeService`（create/update 后）；`un_flow_binding` |

### E 对外应用（`/v1/**` + appId/secret + 幂等）

| ID | 任务 | 状态 | 验收要点 |
|---|---|---|---|
| E-1 | 对外应用 CRUD、`appId/secret`、轮换 | ✅ | secret 仅创建/轮换可见；资料可 PUT 更新（不含编码与密钥） | `PlatformAppController`（`/v1/platform/apps` CRUD+`PUT /{id}`）；`PlatformAppManageService`；`un_app_client/un_app_client_credential`（BCrypt `secret_hash`） |
| E-2 | 开放接口 `/v1/open/**`：AK/SK 或 HMAC 签名 + 目标 system/tenant + 代操作 platId | ✅ | 签名：`X-Timestamp`+`X-Signature`；兼容 `X-Secret`/`Basic`；全平台 client 须 `X-Target-System-Id` | `OpenApiAuthenticationFilter`；`OpenApiFlowController`/`OpenApiModuleRecordController` |
| E-3 | 开放 API 幂等（`Idempotency-Key` + Redis） | ✅ | 成功响应缓存 24h；重放响应头 `X-Idempotency-Replay: 1` | `OpenApiIdempotencyService` |

---

## 3.0 上线执行策略（2026-05 约定）

**目标**：功能完整、可部署上线。

**推荐节奏（按模块竖切，非「全部后端再全部前端」）**：

1. **后端**：该模块 API + 数据权限 + 示例 `config_json` 定稿 → 可 Postman/Swagger 联调  
2. **移动端（uniapp）**：管理配置 + 运行时入口 → 真机走通  
3. **Web（vue3）**：与 uniapp 同契约，可晚一期（管理台大屏）  
4. **部署**：`application-prod`、Redis/MySQL、手工 SQL 清单、冒烟用例  

里程碑 A–E 主体已在后端；以下为 **F 阶段（上线收口）**。

### F 上线收口（后端优先 → 前端对接）

| ID | 任务 | 状态 | 验收要点 | 证据 |
|---|---|---|---|---|
| F-1 | 角色 `data_scope` + 记录数据权限 | ✅ | 非 owner 按角色范围过滤 create_user_id | `ModuleDataScopeService`；`ModuleRecordFacadeService` |
| F-2 | 流程绑定 `un_flow_binding` | ✅ | create/update 触发 flow | `SystemModuleFlowBindingService` |
| F-3 | 页面设计器 `/v1/system/module/pages/**` | ✅ | 页面/区块 CRUD + runtime | `SystemModulePageService` |
| F-4 | 页面运行时 list/form/detail | ✅ | pageId 驱动跳转与字段覆盖 | `GET .../pages/{id}/runtime`；`runtime/entry.vue` |
| F-5 | 运行时菜单（按角色过滤） | ✅ | `GET .../runtime-menus` | `SystemModuleRbacService#listRuntimeMenus` |
| F-6 | 角色页面权限 | ✅ | `page-perms` set/list | `SystemModuleRbacService#setRolePagePerms` |
| F-7 | 关系运行时查询子记录 | ✅ | `POST .../records/query-by-relation` + `fkField` | `ModuleRelationRecordService` |
| F-8 | 关系/页面移动端管理 UI | ✅ | relations、pages、menus | `meta/relations.vue`；`pages/*`；`runtime/menus.vue` |
| F-9 | Web 管理端 vue3 | ✅ | 登录/系统/应用配置/记录/流程/平台收件箱/开放应用/上传 | `web/vue3` AdminLayout + 全量 views + API |
| F-10 | 部署文档与 prod 配置样例 | ✅ | 冒烟清单 + prod profile | `docs/deploy/production.md`；`application-prod.yml` |

手工 SQL（Flyway 可选）：`data_scope` 见 `docs/sql/manual/V21__module_role_data_scope.sql`；`n-n` 配置见 `docs/sql/manual/V22__nn_relation_config_example.md`。

### G 缺口补齐（2026-05 续）

| ID | 任务 | 状态 | 说明 |
|---|---|---|---|
| G-1 | n-n 关系 `query-by-relation` | ✅ | `linkModelId` + `srcFkField` + `dstFkField` |
| G-2 | 平台待办可见系统（owner + 成员） | ✅ | `PlatformSystemAccessService` |
| G-3 | Web 记录表单（复杂字段+子表） | ✅ | `RecordFormView` + `TagField`/`RegionAddressField`/`RefSubTableField` |
| G-4 | Web 字典项/流程版本/导出任务/注册/实例详情/开放应用详情 | ✅ | 对应 views + router |
| G-5 | 流程可视化设计器 | ✅ | `FlowGraphDesignerView` + `graph-designer` API |
| G-6 | 开放 API 签名校验 | ✅ | `X-Timestamp`+`X-Signature` HMAC v1；`sign_secret_enc`；兼容 `X-Secret` |
| G-7 | EAV 性能优化 | ✅ | `idx_module_record_data_eq`；`listByRecordIds` 批量查询 |

### H 上线体验（2026-05 续）

| ID | 任务 | 状态 | 说明 |
|---|---|---|---|
| H-1 | 列表查询批量附带 EAV 字段 | ✅ | `includeFieldCodes` → `list[].data`；Web/移动端去掉 N+1 `getRecord` |
| H-2 | Web 开放应用详情 + 签名说明 | ✅ | `OpenAppDetailView` 轮换密钥与签名文档 |
| H-3 | 关联字段/子表批量 EAV | ✅ | `refPicker` + `RefSubTableField` 用 `includeFieldCodes`/`id in` |
| H-4 | 子表按关系加载（1-n FK） | ✅ | `relationId` 或自动匹配；`query-by-relation`；新建子行 `linkFkField` |
| H-5 | 导出任务列表 + 记录列表多列 | ✅ | `ExportJobsView` 状态筛选/轮询/鉴权下载；`RecordsListView` 按 `columnFieldCodes`+`includeFieldCodes`；`RelationsView` 展示关系 ID 与配置提示 |

### I 功能收官（2026-05）

| ID | 任务 | 状态 | 说明 |
|---|---|---|---|
| I-1 | 开放 API 记录全链路 | ✅ | `OpenApiModuleRecordController`：detail/query/delete/query-by-relation/history |
| I-2 | Web 导出发起 + 签名/评分字段 | ✅ | `ExportsView` `createExportJob`；`SignatureField`/`RatingField` |
| I-3 | Web 平台创建系统 + RBAC dataScope | ✅ | `SystemsView`；`RbacView` 数据权限与 perm-preview |
| I-4 | Web 平台收件箱抄送已读 | ✅ | `PlatformInboxView` + `readPlatformCc` |
| I-5 | 文档与契约同步 | ✅ | `openapi-contract.md`（含 §9 graph-designer）、`curl-examples.md`、`mobile-api-coverage.md`；冒烟 `e2e-smoke` 含 graph-designer |

---

## 3.1 上线准备（生产化收口项）

| ID | 任务 | 状态 | 验收要点 | 证据（示例） |
|---|---|---|---|---|
| P-1 | Flyway 基线迁移（platform/upload/module/flow/app/plat_rbac） | ✅ | `db/migration/V*` 齐全；启动时可自动建表/初始化种子；不依赖手工跑 `docs/sql` | `examine-web`：`spring.flyway.*`；`backend/examine-web/src/main/resources/db/migration/` |
| P-2 | requestId 贯穿（入参可传、响应回传、日志可关联） | ✅ | 支持从 `X-Request-Id` 透传；否则自动生成；响应头回写；异常响应 `ApiResult.requestId` 一致 | `RequestContextFilter`；`GlobalExceptionHandler`；`PlatOperLogInterceptor` |
| P-3 | 最小指标暴露（Actuator metrics + Prometheus） | ✅ | `management.endpoints.web.exposure.include` 含 `metrics/prometheus`；具备 `/actuator/prometheus` | `examine-web`：`micrometer-registry-prometheus`；`application.yml` management 配置 |

---

## 4. 文档收敛结果（你要的“统一一份”）

- **需求根本**：`README.md`
- **执行清单（唯一进度表）**：`docs/development-tracker.md`（本文）
- **需求记录（变更日志）**：`docs/requirements-log.md`

### 4.1 v1 交付核对（2026-05-21）

| 端 | 范围 | 状态 |
|----|------|------|
| 后端 | A–E + P（Flyway/指标/requestId） | ✅ |
| Web | 管理台全视图 + `FlowGraphDesignerView` + 筛选模板 | ✅ |
| 移动端 | 元数据/记录/流程/上传/RBAC + 流程图画布·列表·预览 | ✅ |
| 契约/冒烟 | `openapi-contract` §9、`curl-examples`、`e2e-smoke`（含 graph-designer 发布） | ✅ |

**新开功能**：仅通过 `requirements-log.md` 追加 + tracker 新任务行。

