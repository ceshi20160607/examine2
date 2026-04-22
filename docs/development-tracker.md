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

## 2. 当前总体结论（对齐你的问题：是否按计划执行？）

- **执行形态**：目前的提交内容更像是“按里程碑 A→B 推进，同时把 `examine-module` 的 CRUD/元数据相关控制器与实体批量铺开”，与既定里程碑分期并不冲突，但**缺少在文档里回填状态**导致看起来“没按计划走”。
- **收口动作**：从现在起，进度只在本文更新；需求澄清/变更只追加到 **`docs/requirements-log.md`**，避免分散在多份文档里。

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
| C-5 | 业务行数据 CRUD、`*_data` / EAV、DSL 白名单 | ✅ | 数据可写可查；查询不允许任意 SQL | `SystemModuleRecordController`（create/detail/query/update/delete）；`un_module_record_data` 为 **EAV**（`field_code`+`value_text`）；变更写入 `un_module_record_history`；DSL 动态条件用 **field_code**；写入/查询均校验 `field_code` 存在于 `un_module_field`（注：typed-value/索引优化后续迭代） |

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
| E-2 | 开放接口 `/v1/open/**`：AK/SK（BCrypt）+ 目标 system/tenant + 代操作 platId | ✅ | `Basic` 或 `X-Access-Key`+`X-Secret`；全平台 client 须 `X-Target-System-Id`；须 `X-Acting-Plat-Id` | `OpenApiAuthenticationFilter`；`TokenAuthenticationFilter`；`OpenApiFlowController`/`OpenApiModuleRecordController` |
| E-3 | 开放 API 幂等（`Idempotency-Key` + Redis） | ✅ | 成功响应缓存 24h；重放响应头 `X-Idempotency-Replay: 1` | `OpenApiIdempotencyService` |

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

