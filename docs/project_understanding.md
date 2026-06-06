# PM 主持项目理解与 API 阶段结论

## 审查输入与边界

本文件最初由 PM 在项目理解决策模式下生成，用于记录 PRD 生成前的多角色理解审查结论。第 2 次 API 契约闭环中仅针对 `BAPI-001` 修订当前阶段结论，不重写历史 issue 台账。

- `docs/user_requirement.md`
- `docs/requirement_analysis.md`
- `docs/legacy_reference.md`
- `docs/service_info.md`
- `docs/cleanup_report.md`
- `docs/understanding/dba_review.md`
- `docs/understanding/backend_review.md`
- `docs/understanding/frontend_review.md`
- `docs/understanding/test_review.md`
- `.codex/state.json`

当前阶段以 `.codex/state.json` 为准：`current_step=13`，`status=API_REVIEW_FAIL_LOOP_2_PM_FIXING`，`api_frozen=false`。当前活动区 `docs/prd.md` 已存在，PRD 复审已通过；本次不读取旧项目目录，不生成 SQL，不写代码，不进入 DB 设计、任务拆分或实现阶段。

## PRD 与原始需求一致性结论

当前 `docs/prd.md` 已完成并经过 PRD 复审闭环，PRD 与原始需求、需求分析和旧项目参考摘要的主线一致。PRD 已吸收平台用户与系统成员边界、`un_plat_`/`un_openapi_` 表前缀、Maven 多模块、`base/manage` 分层、`examine-generator`、阶段边界、状态矩阵、权限矩阵、页面交互、初始化数据和验收标准等理解审查决策。

理解阶段历史 issue 中，`PU-017` 和 `PU-018` 已在 PRD 复审与后续 API 审查输入中关闭或降级为非 API 阶段阻塞项：

- `PU-017`：最小端到端测试数据边界已由 PRD 和 API 中的生产 seed、创建系统事务初始化、测试样例数据边界承接；不再阻塞 API 契约审查闭环。
- `PU-018`：旧项目 flow/DDL 不一致和 `un_app_` 历史域问题已由 PRD、旧项目参考摘要和 API 表域边界承接；DBA API 复核已确认不再阻塞 API 契约审查闭环。

当前产品基础口径继续保持为：

- 平台用户是全局登录主体，账号基础数据维护在平台层。
- 自定义系统内用户不是独立登录账号，而是平台用户在某个自定义系统里的成员扩展。
- 自定义系统创建人默认成为该系统的系统超级管理员。
- 系统内组织架构、部门、角色、菜单、按钮、字段、数据范围和流程权限由该系统自己的配置维护。
- 平台超级管理员负责平台账号、系统创建、全局配置、审计和运维，不默认绕过系统内权限直接修改业务数据；如需运维型只读能力，必须在 PRD 中明确审计边界。
- 后端规划新增 `examine-generator` 模块，承载 MyBatis-Plus 代码生成、后续 XML 模板、通用 CRUD 扩展模板、表到模块映射和可选生成接口。生成结果只进入各业务模块 `base` 层，业务编排仍在 `manage` 层。

## 各角色评审摘要

| 角色 | 审查结论 | PM 摘要 |
| --- | --- | --- |
| DBA | fail | 业务域和旧项目表前缀识别充分，但表前缀、OpenAPI 表域、系统/租户层级、配置版本快照、EAV 物理模型、状态枚举、导入、文件引用、权限颗粒度和初始化数据仍需 PRD 冻结。 |
| backend | fail | 模块职责、base/manage 分层、旧项目风险识别基本到位，但接口语义、事务边界、权限判定、流程状态回写、附件补偿、导入导出、OpenAPI、安全、错误码和异步一致性仍需 PM 决策。 |
| frontend | fail | 已确认旧项目无可复用前端源码，不能照搬调试式 CRUD 页面；但页面导航、角色工作台、列表/表单/详情矩阵、状态禁用态、保存刷新、移动端边界和 typed SDK 前置契约需要 PRD 明确。编码问题经 UTF-8 读取复核属于读取方式风险，不作为内容阻塞。 |
| test | fail | 端到端闭环、角色、权限、数据隔离、并发、异常和旧能力回归范围已可识别；但 MVP 边界、状态矩阵、权限验收矩阵、最小测试数据、错误码/审计断言和幂等预期结果需要冻结。 |

## PM 总体决策

### 采纳项

- 采纳 `un_plat_` 作为平台、账号、系统、租户、平台角色和平台权限表前缀，拒绝 `un_platt_`。
- 采纳后端 Maven 多模块结构：`examine-core`、`examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app`、`examine-generator`、`examine-web`。
- 采纳 `base` 与 `manage` 分层：`base` 仅为 MyBatis-Plus 贴表 CRUD；`manage` 承载 Controller、BO/VO/DTO、权限、事务、业务校验和转换。
- 采纳平台用户与系统成员分离模型：平台用户全局登录，系统成员是平台用户在系统内的成员扩展。
- 采纳系统创建人默认成为该自定义系统超级管理员。
- 采纳系统内权限由系统自己的组织架构和角色权限配置维护。
- 采纳 API 契约冻结后再做前端 typed SDK 和页面接口映射的前置约束。
- 采纳旧项目只作为参考，不沿用旧生成式 CRUD Controller、PO 入参出参、运行时 schema repair、手工 Flyway 标记和乱码文案。

### 拒绝项

- 拒绝沿用或生成任何 `un_platt_` 表前缀。
- 拒绝把归档 PRD、归档 API、归档 DB 设计、归档 SQL 或归档代码作为当前活动区输入。
- 拒绝把平台超级管理员设计成可绕过系统内权限直接修改所有业务数据的入口。
- 拒绝把旧项目的 `/add`、`/update`、`/queryPageList`、`/deleteByIds` 调试式接口作为新 API 契约。
- 拒绝把 `examine-web` 继续作为业务实现堆放模块。
- 拒绝在 MVP 引入通用脚本执行平台或把智能助手作为核心闭环前置条件。

### 延后项

- 完整移动端应用、复杂移动端配置能力、扫码拍照之外的高级移动工作台能力延后；MVP 只在 PRD 中明确 Web 优先和移动端轻量运行入口边界。
- 复杂打印模板可视化设计、套打、PDF、历史模板打印延后；MVP 可保留模板管理和版本预留。
- 高级仪表盘/KPI 全量闭环、个人仪表盘、组件缓存、复杂报表延后；MVP 只保留基础统计或配置预留。
- 导入回滚、行级错误明细和复杂导入冲突处理可延后；MVP 是否实现导入执行由新 PRD 明确，至少不能影响导出任务闭环。
- 智能助手、命令中心、全局搜索、灰度、备份恢复、多环境、容量配额、完整数据脱敏作为后续增强。

## Issue 台账

| issueId | 提出角色 | 问题 | 影响 | 阻塞级别 | 建议责任方 | PM 决策 | 处理轮次 | 当前状态 | 复核角色 | 关闭条件 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| PU-001 | DBA/backend/test | 当前评审文件提到旧 PRD 草稿与 `un_plat_` 冲突，但活动区 PRD 已清理不存在。 | 若误用归档 PRD，会污染新 PRD、DB 和 API。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：不读取、不沿用归档 PRD；新 PRD 从当前输入重新生成。 | 1/3 | 已决策，待新 PRD 吸收 | DBA/backend/test | 新 PRD 明确当前活动区重建原则，且不引用归档 PRD。 |
| PU-002 | DBA/backend/test | 平台表前缀必须统一，旧错误前缀 `un_platt_` 不能继续出现。 | 影响表设计、代码生成、模块映射和测试数据。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：平台与租户、账号、系统、平台 RBAC 统一使用 `un_plat_`。 | 1/3 | 已决策，待新 PRD 吸收 | DBA/backend/test | 新 PRD 数据规则和技术架构中明确 `un_plat_`，不得出现 `un_platt_`。 |
| PU-003 | DBA/backend | OpenAPI 表域使用 `un_app_` 还是 `un_openapi_` 不明确。 | 影响 DB 前缀、API 模块、代码生成和旧表迁移映射。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：新项目 OpenAPI 中心表使用 `un_openapi_`；`examine-app` 是后端模块名，旧 `un_app_` 只作历史参考。 | 1/3 | 已决策，待新 PRD 吸收 | DBA/backend | 新 PRD 明确 `examine-app` 职责和 `un_openapi_` 表前缀映射，旧 `un_app_` 标为需重命名参考。 |
| PU-004 | DBA/backend/test | 系统、租户、平台账号、系统成员、角色权限层级口径需要冻结。 | 影响外键、唯一索引、权限过滤、登录主体和成员管理。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：平台用户全局登录；系统成员是平台用户在系统内的扩展；系统创建人默认系统超级管理员；系统可选多租户，单租户也初始化默认租户。 | 1/3 | 已决策，待新 PRD 吸收 | DBA/backend/test | 新 PRD 权限规则、数据规则和业务流程中完整说明平台账号、系统成员、租户、系统内组织角色边界。 |
| PU-005 | DBA/backend/test | MVP 边界未冻结，字段类型、导入、打印、仪表盘/KPI、移动端等范围过宽。 | 影响验收范围、接口数量、表设计和任务拆分。 | 阻塞 API/任务拆分，不阻塞 PRD 生成 | PM | PM 直接决策：新 PRD 必须按模块拆出 MVP、后续增强、暂不做；不把全部设想压入 MVP。 | 1/3 | 已决策，待新 PRD 吸收 | backend/frontend/test | 新 PRD 每个模块均标注 MVP、后续增强、暂不做和验收标准。 |
| PU-006 | DBA/backend | 配置草稿、发布、版本、快照、回滚范围未明确。 | 影响字段、页面、流程、导出模板、打印模板、仪表盘表结构和运行态解释。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：MVP 至少对模块、字段、页面、流程、导出配置保留发布版本语义；打印和仪表盘可按阶段预留。 | 1/3 | 已决策，待新 PRD 细化 | DBA/backend | 新 PRD 明确配置发布范围、运行态读取发布版本、历史记录和审批实例如何按旧版本解释。 |
| PU-007 | DBA/backend | EAV 物理模型、字段值、索引、唯一性、关联和子表边界未到可设计层级。 | 影响动态记录查询性能、唯一校验、字段删除历史展示和 DBA 设计。 | 阻塞 DB，不阻塞 PRD 生成 | PM + analyst | PM 决策：新 PRD 先冻结产品数据规则；analyst 后续可补旧表字段级参考，不作为 PRD 生成前置。 | 1/3 | 部分决策，待 PRD 和 analyst 补充 | DBA/backend | 新 PRD 明确 record、value、index、relation、subtable、history 的产品边界；如 DBA 仍需旧字段摘要，再退 analyst。 |
| PU-008 | DBA/backend/frontend/test | 系统、账号、应用、模块、字段、页面、流程、业务记录、文件、导出任务、OpenAPI 客户端状态矩阵不足。 | 影响按钮可用性、后端状态校验、索引和测试断言。 | 阻塞 API/测试，不阻塞 PRD 生成 | PM | PM 直接决策：新 PRD 必须给核心对象状态枚举、允许操作、禁止操作和状态变化。 | 1/3 | 已决策，待新 PRD 吸收 | DBA/backend/frontend/test | 新 PRD 的数据规则、页面交互和业务流程均包含状态矩阵。 |
| PU-009 | DBA/backend/frontend/test | 权限粒度和数据范围还停留在方向，缺少判定顺序和验收矩阵。 | 影响越权防护、菜单过滤、按钮禁用、字段权限、导出脱敏和 OpenAPI scope。 | 阻塞 API/前端/测试，不阻塞 PRD 生成 | PM | PM 直接决策：新 PRD 明确菜单、操作、字段、数据范围、导出、OpenAPI 权限；角色权限叠加采用“授权并集 + 显式禁用/数据范围按最小可见规则另行定义”的可实现口径。 | 1/3 | 已决策，待新 PRD 细化 | backend/frontend/test | 新 PRD 给出角色 x 页面/操作/字段/数据范围矩阵和权限禁用态。 |
| PU-010 | backend/test | 动态记录保存、流程触发、附件引用、事件扩展、异步任务一致性不足。 | 影响事务边界、补偿、幂等、失败可观测和后端 API 设计。 | 阻塞 API/后端，不阻塞 PRD 生成 | PM | PM 直接决策：新 PRD 按新增、编辑、删除、提交审批、导出任务、OpenAPI 调用分别定义事务范围和失败反馈；禁止 fire-and-forget 参与需回滚主流程。 | 1/3 | 已决策，待新 PRD 吸收 | backend/test | 新 PRD 的业务流程包含事务落点、状态影响、补偿规则和失败页面反馈。 |
| PU-011 | DBA/backend/frontend | 文件生命周期、引用关系、失败补偿和权限归属不足。 | 影响文件表、引用表、业务保存失败补偿、下载预览权限和删除限制。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：文件分临时、已引用、已删除等状态；业务对象保存成功后绑定引用，失败按临时文件规则清理或保留过期清理；下载/预览跟随业务对象权限和文件引用关系。 | 1/3 | 已决策，待新 PRD 细化 | DBA/backend/frontend | 新 PRD 明确文件状态、引用对象、删除规则、失败补偿和权限校验。 |
| PU-012 | DBA/backend/test | 导入导出任务语义不足，导入 MVP 边界不清。 | 影响任务表、状态、重试、结果文件、失败明细和测试范围。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：MVP 必须完成导出任务闭环；导入执行可延后，但新 PRD 要明确是否只预留导入任务模型。 | 1/3 | 已决策，待新 PRD 吸收 | DBA/backend/test | 新 PRD 明确导出同步/异步、任务状态、失败重试、结果文件、权限脱敏；导入是否实现写清楚。 |
| PU-013 | DBA/backend/test | OpenAPI 安全语义不足，客户端归属、签名、幂等、限流、审计主体未冻结。 | 影响外部安全边界、表设计、拦截器、错误码和测试断言。 | 阻塞 API/DB，不阻塞 PRD 生成 | PM | PM 直接决策：OpenAPI 客户端绑定具体系统/租户和授权范围，平台可做全局策略；必须支持签名、时间窗口、幂等键、限流、IP 白名单和调用日志。 | 1/3 | 已决策，待新 PRD 细化 | DBA/backend/test | 新 PRD 给出 OpenAPI 客户端、凭证、scope、日志、幂等、限流和错误反馈要求。 |
| PU-014 | backend/frontend/test | 错误码、统一返回、requestId、审计日志和健康检查输出未拆到契约前置级别。 | 影响 API 冻结、typed SDK、异常测试、日志排查和前端提示。 | 阻塞 API，不阻塞 PRD 生成 | PM | PM 直接决策：新 PRD 需定义错误码命名空间和典型错误；API 阶段再冻结完整错误码。所有接口必须带 requestId。 | 1/3 | 已决策，待新 PRD 和 API 吸收 | backend/frontend/test | 新 PRD 包含模块级错误码范围和可观测要求；API 文档冻结具体错误码。 |
| PU-015 | frontend | 页面入口、导航、角色工作台、列表字段、表单字段、详情区块、空态/错误态/权限禁用态不足。 | 影响前端页面闭环、接口映射和验收。 | 阻塞 API/前端，不阻塞 PRD 生成 | PM | PM 直接决策：新 PRD 必须按页面写入口、字段、筛选、按钮、状态、空态、加载态、错误态、权限禁用态和保存刷新规则。 | 1/3 | 已决策，待新 PRD 吸收 | frontend/test | 新 PRD 页面/交互说明达到页面级矩阵要求。 |
| PU-016 | frontend/backend/test | typed SDK 前置契约粒度不足。 | 影响前端接口类型、枚举、错误码和页面到接口映射闭环。 | 阻塞 API/前端，不阻塞 PRD 生成 | PM/API | PM 决策：PRD 阶段定义上下文字段自动补齐、动态字段值结构、统一分页/排序/筛选方向；API 阶段冻结 typed SDK 所需字段。 | 1/3 | 已决策，待 PRD 和 API 吸收 | frontend/backend/test | API 契约含统一响应、分页、枚举、状态、错误码、动态字段值、文件和任务响应。 |
| PU-017 | test | 最小端到端测试数据准备不明确。 | 影响后续测试计划、初始化 SQL 验收和场景复现。 | 曾阻塞测试/DB | PM + analyst | PM 决策：PRD 明确默认初始化目标；API 补齐生产 seed、创建系统初始化响应和测试样例边界。 | 2/3 | 已关闭 | test/DBA | PRD 与 API 已明确默认平台管理员、角色、菜单、默认系统/租户/应用/字段类型元数据方向，TAPI-002 与 DBA 复核已关闭。 |
| PU-018 | DBA | 旧项目 flow 实体与 DDL 不一致、`un_app_` 历史原因、旧动态记录字段参考需要更细。 | 影响 DBA 后续判断旧表哪些可参考、需重命名、禁止沿用。 | 曾阻塞 DB | analyst/PM | PM 决策：PRD 与 API 明确 `un_app_` 仅为旧 OpenAPI 历史参考，新项目业务应用归 `un_module_`，OpenAPI 归 `un_openapi_`；旧 flow 差异不阻塞 API 阶段。 | 2/3 | 已关闭 | DBA | DBA API 复核已确认 `un_module_`/`un_openapi_`/`un_app_` 表域边界足以进入后续 DB 设计。 |

## 需要 analyst 补充的事项

以下事项为历史理解阶段留下的参考补充方向。当前 `PU-017`、`PU-018` 已关闭，不再阻塞 API 契约审查闭环；若后续 DB 设计或测试设计发现字段级资料仍不足，应按对应阶段重新登记 issue，不回滚当前 API 阶段结论：

| 事项 | 建议修改文件 | 触发条件 | 复核角色 |
| --- | --- | --- | --- |
| 旧项目表清单按“可参考、需重命名、禁止沿用”分类，尤其 flow 实体与 Flyway DDL 不一致表。 | `docs/legacy_reference.md` | DBA 在 PRD 复核后仍无法判断表清单来源。 | DBA |
| 补充旧 `un_app_` 作为 OpenAPI 域的历史原因，并说明新项目改用 `un_openapi_` 的映射风险。 | `docs/legacy_reference.md` | DBA/backend 需要旧表迁移映射。 | DBA/backend |
| 补充旧动态记录、字段值、关联关系、导出任务、OpenAPI 幂等、流程任务并发的字段级参考摘要。 | `docs/legacy_reference.md` | DBA 进入表结构设计前需要字段参考。 | DBA/backend |
| 补充最小端到端验收样例，包括默认系统、租户、角色、用户、部门、应用、模块、字段、流程模板和授权配置。 | `docs/requirement_analysis.md` | test 生成测试计划前仍缺稳定前置数据。 | test |

## PM 可在后续新 PRD 中直接决策的事项

- 平台表前缀统一 `un_plat_`。
- OpenAPI 表前缀使用 `un_openapi_`，后端模块仍为 `examine-app`。
- 系统与租户层级：系统为平台创建的业务容器；系统可选多租户；单租户系统初始化默认租户；应用、模块、成员、角色和业务数据均带系统上下文，按需要带租户上下文。
- 平台用户与系统成员模型：平台用户全局登录；系统内成员是平台用户在系统中的扩展；系统创建人默认系统超级管理员。
- 系统内权限维护：系统内组织架构、角色、菜单、按钮、字段、数据范围和流程权限由系统内配置维护。
- MVP 与增强边界：字段类型、导入、打印模板、仪表盘/KPI、移动端、智能助手、命令中心等必须按阶段拆分。
- 配置发布与快照：PRD 先冻结产品规则，DB/API 阶段再细化表和接口。
- 状态矩阵、按钮动作、保存刷新、权限禁用态、错误态和 requestId 展示。
- `examine-generator` 的模块职责、生成范围、生成落位和不得生成对外 Controller 的规则。

## 未决问题

当前没有必须提交用户确认后才能进入 PRD 生成的问题。以下问题需要在新 PRD 中给出 PM 默认方案；若用户后续明确要求调整首期范围，再回到 PRD 修订：

- 导入执行是否进入 MVP，PM 默认方案为“MVP 导出闭环必做，导入执行可延后或只做任务模型预留”。
- 完整移动端是否进入 MVP，PM 默认方案为“Web 优先，移动端只保留轻量运行和审批边界，不做复杂配置器”。
- 打印模板可视化和 PDF 是否进入 MVP，PM 默认方案为“延后，MVP 只保留模板/版本/导出方向或预留”。
- 仪表盘/KPI 是否做完整闭环，PM 默认方案为“MVP 做基础统计或预留，完整组件化闭环延后”。

## API 契约生成前置条件

API 契约生成前置条件已满足：`docs/prd.md` 已存在并通过 PRD 复审，`PU-017`、`PU-018` 已关闭，理解阶段无剩余阻塞 issue。当前允许进入 API 契约审查闭环。

当前限制：

1. `docs/api.md` 尚未冻结，`api_frozen=false`。
2. API 未冻结前，不允许进入 DB 设计、SQL 生成、任务拆分、后端实现或前端实现。
3. API 第 2 次闭环仅处理 `BAPI-001`、`TAPI-005`、`TAPI-006`，修订后必须等待 backend 和 test 对剩余 issue 复核。
4. DBA 与 frontend 第 1 轮 API 复核已通过；若后续因 API 新增契约影响其已通过结论，应重新登记对应 API issue。

## 是否允许进入 API 契约审查闭环

**允许进入 API 契约审查闭环。**

理由：

- PRD 复审已通过，`docs/project_understanding.md` 的历史 PRD 生成前置结论不再作为当前阶段限制。
- `PU-017` 和 `PU-018` 已关闭，不再阻塞 API 契约审查。
- 当前 state、`docs/api.md` 和 `docs/api_review.md` 均指向 step 13 的 API 契约闭环，且 `api_frozen=false`。

限制：

- 仅允许继续 API 契约审查闭环，不允许冻结 API 后的 DB/代码/任务拆分活动。
- API 是否冻结必须以 `docs/api_review.md` 中 PM 汇总 backend、test 复核后的最终结论为准。

## 下一步自动审查动作

1. PM 在 API 契约模式下修订 `docs/api.md` 与 `docs/api_review.md`，仅处理 `BAPI-001`、`TAPI-005`、`TAPI-006`。
2. backend 复核 `BAPI-001` 是否关闭。
3. test 复核 `TAPI-005`、`TAPI-006` 是否关闭。
4. 所有剩余阻塞 issue 关闭前，不得冻结 API，不得进入任务拆分阶段。
