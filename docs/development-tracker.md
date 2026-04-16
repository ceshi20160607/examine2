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
| B-2 | 成员/角色/菜单权限基础能力（RBAC + 菜单树） | 🟡 | 菜单/权限查询可用；按 system 隔离 | `ModuleMenuController` 等；README §12.1 |
| B-3 | AuthContext 计算/缓存/失效（5min TTL 可配） | 🟡 | 接口硬过滤生效；缓存可命中可失效 | `AuthContextHolder`、`ModuleAuthContextHolder`、`ModuleAuthCacheCoordinator` |

### C 无代码与 `examine-module`（模块/字段/列表/字典/关系/CRUD）

| ID | 任务 | 状态 | 验收要点 | 证据（示例） |
|---|---|---|---|---|
| C-1 | 模块/字段/表单/列表/关系等元数据接口 | 🟡 | 能创建/更新/查询元数据；按权限隔离 | 多个 `Module*Controller`、`Module*Mapper`、`Module*` PO |
| C-2 | 字典/字典项 | 🟡 | 字典可维护、可被字段引用 | `ModuleDictController`、`ModuleDictItemController` |
| C-3 | 列表视图/列配置/筛选模板 | 🟡 | 列表展示配置可维护 | `ModuleListViewController`、`ModuleListViewColController`、`ModuleListFilterTplController` |
| C-4 | 导出模板/字段（导出配置） | 🟡 | 导出配置可维护（先不要求真实导出实现） | `ModuleExportTplController`、`ModuleExportTplFieldController` |
| C-5 | 业务行数据 CRUD、`*_data` / EAV、DSL 白名单 | ✅ | 数据可写可查；查询不允许任意 SQL | `SystemModuleRecordController`（create/detail/query/update/delete）；`un_module_record_data` 为 **EAV**（`field_code`+`value_text`）；变更写入 `un_module_record_history`；DSL 动态条件用 **field_code**；写入/查询均校验 `field_code` 存在于 `un_module_field`（注：typed-value/索引优化后续迭代） |

### D flow（审批引擎主链路）

| ID | 任务 | 状态 | 验收要点 |
|---|---|---|---|
| D-1 | 模板→运行时复制，`flow_record`/`record_node` 基础模型 | ⬜ | 改模板不追溯已发起实例 |
| D-2 | 发起/办理/终态；单实例、或签、再发起 | ⬜ | 状态机正确、幂等正确 |
| D-3 | module 触发 flow 的 Facade 契约 | ⬜ | module 不内嵌引擎，只调契约 |

### E 对外应用（`/v1/**` + appId/secret + 幂等）

| ID | 任务 | 状态 | 验收要点 |
|---|---|---|---|
| E-1 | 对外应用 CRUD、`appId/secret`、轮换 | ⬜ | secret 仅生成/轮换可见 |
| E-2 | `/v1/**`、flowId 路由、app 鉴权与授权 | ⬜ | 第三方不传 systemId；按授权解析 |
| E-3 | 开放 API 幂等（`Idempotency-Key` + Redis） | ⬜ | 同键重放 200 且标记 replay |

---

## 4. 文档收敛结果（你要的“统一一份”）

- **需求根本**：`README.md`
- **执行清单（唯一进度表）**：`docs/development-tracker.md`（本文）
- **需求记录（变更日志）**：`docs/requirements-log.md`

