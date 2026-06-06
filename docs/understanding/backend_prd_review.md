# Backend 新 PRD 复审

## 审查范围

本次执行“新 PRD 复审”模式，仅复核以下输入：

- `docs/user_requirement.md`
- `docs/requirement_analysis.md`
- `docs/legacy_reference.md`
- `docs/service_info.md`
- `docs/project_understanding.md`
- `docs/prd.md`

本次未写后端代码，未生成 API，未读取旧项目目录，未修改其它文件。

## 结论

**pass**

`docs/prd.md` 已满足 `docs/project_understanding.md` 中 PU-001 到 PU-018 的后端相关关闭条件。重点项中，模块职责、`examine-generator`、接口语义前置、事务边界、权限判定、数据范围、流程状态回写、附件补偿、导出任务、OpenAPI 鉴权/幂等/限流、错误码命名空间和异步一致性均已达到 PRD 阶段可进入后续多角色复核的明确度。

PU-017、PU-018 属于 PM 已明确的后续 analyst/test/DBA 补充项：不阻塞 PRD 复审通过，但在进入 DB 设计、最小测试样例或旧表字段级迁移映射前仍需按台账继续闭环。

## 重点复核结论

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 模块职责 | pass | PRD 在“后端 Maven 多模块”中明确 `examine-core`、`examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app`、`examine-generator`、`examine-web` 的职责、接口边界、依赖方向和禁止事项。 |
| `examine-generator` | pass | PRD 明确其承载 MyBatis-Plus 代码生成、XML 模板、通用 CRUD 扩展模板、表到模块映射，生成结果落到业务模块 `base` 层，不生成对外 Controller，不生成 `manage` 业务编排。 |
| 接口语义前置 | pass | PRD 未生成 API，但已按平台、系统配置、应用配置、运行台、流程、文件/导出、OpenAPI、运维审计拆出业务接口边界，并声明 API 契约阶段冻结具体字段、分页、枚举、错误码和 typed SDK 所需结构。 |
| 事务边界与异步一致性 | pass | PRD 明确业务记录新增/编辑同一事务处理主记录、字段值、索引、唯一性、自动编号、关联/子表、历史和附件引用校验；提交审批同步创建流程实例和待办；影响主流程回滚的步骤禁止 fire-and-forget。 |
| 权限判定与数据范围 | pass | PRD 明确登录/OpenAPI 签名、系统租户上下文、成员角色状态、菜单/API、按钮、字段、数据范围、业务状态的判定顺序，并给出角色权限矩阵。 |
| 流程状态回写 | pass | PRD 明确审批结束后按流程配置回写业务状态，并写审批日志、操作审计和消息；任务只能成功处理一次，重复点击返回已处理提示或幂等结果。 |
| 附件补偿 | pass | PRD 明确文件临时、已引用、已删除、过期状态；业务保存成功后绑定引用，业务失败时临时文件保留到过期清理或按本次上传补偿删除。 |
| 导出任务 | pass | PRD 明确 MVP 必做导出任务闭环，任务状态包含排队、处理中、成功、失败、取消；创建时保存筛选、权限、脱敏和 requestId 快照，生成成功写结果文件，失败记录原因和可重试次数。 |
| OpenAPI 鉴权/幂等/限流 | pass | PRD 明确客户端绑定系统/租户/scope，调用校验凭证、签名时间窗口、IP 白名单、限流、幂等、scope、上下文和数据范围，日志包含客户端、systemId、tenantId、scope、requestId、结果、耗时和失败原因。 |
| 错误码命名空间 | pass | PRD 明确错误码命名空间为 `PLAT`、`SYS`、`MODULE`、`FIELD`、`PERM`、`FLOW`、`UPLOAD`、`EXPORT`、`OPENAPI`、`AUDIT`、`COMMON`，具体错误码留到 API 阶段冻结。 |

## PU 复核明细

| PU | 后端复核结论 | 复核说明 |
| --- | --- | --- |
| PU-001 | pass | PRD 明确从当前活动区输入重建，不引用归档 PRD/API/DB/SQL/代码。 |
| PU-002 | pass | PRD 明确平台、账号、系统、租户、平台 RBAC 表前缀统一为 `un_plat_`，未发现 `un_platt_`。 |
| PU-003 | pass | PRD 明确 OpenAPI 表前缀统一为 `un_openapi_`，`examine-app` 仅作为后端模块名。 |
| PU-004 | pass | PRD 明确平台用户是全局登录主体，系统成员是平台用户在系统内的扩展，系统创建人默认成为系统超级管理员，并说明租户上下文。 |
| PU-005 | pass | PRD 按模块拆分 MVP、后续增强、暂不做，未把导入、复杂打印、完整 KPI、完整移动端等压入 MVP。 |
| PU-006 | pass | PRD 明确模块、字段、页面、流程、导出配置的草稿、发布、版本语义，运行态读取发布版本，流程实例保存模板版本快照。 |
| PU-007 | pass | PRD 在数据规则中明确 record、value、index、relation、subtable、history 的产品边界。 |
| PU-008 | pass | PRD 给出账号、系统、租户、应用、模块、字段、配置版本、业务记录、流程、文件、导出任务、OpenAPI 客户端状态矩阵。 |
| PU-009 | pass | PRD 给出权限边界、判定顺序和角色矩阵，覆盖菜单、按钮、字段、数据范围、导出、OpenAPI scope。 |
| PU-010 | pass | PRD 明确动态记录保存、提交审批、附件引用、导出任务、OpenAPI 调用的事务和失败反馈原则，并禁止影响回滚主流程的 fire-and-forget。 |
| PU-011 | pass | PRD 明确文件生命周期、引用关系、失败补偿、下载预览权限和删除限制。 |
| PU-012 | pass | PRD 明确 MVP 必做导出任务闭环，导入执行延后，仅预留任务模型和页面入口。 |
| PU-013 | pass | PRD 明确 OpenAPI 客户端归属、凭证、签名、时间窗口、nonce、幂等键、限流、IP 白名单、scope、数据范围和调用日志。 |
| PU-014 | pass | PRD 明确 requestId 贯穿前端错误、后端日志、操作审计、导出任务和 OpenAPI 调用日志，并给出模块级错误码命名空间。 |
| PU-015 | pass | 页面/交互矩阵已覆盖后端需要支撑的入口、字段、筛选、按钮、状态、错误态、权限禁用态和保存刷新规则。 |
| PU-016 | pass | PRD 明确上下文字段由后端补齐，动态字段值包含原值、typed 值、展示快照，并要求统一分页、排序、筛选在 API 阶段闭环。 |
| PU-017 | pass（后续补充项） | PRD 已明确默认平台管理员、角色、菜单、默认系统/租户/应用/字段类型元数据方向；最小端到端样例按 PM 决策留给 analyst/test 后续补齐。 |
| PU-018 | pass（后续补充项） | PRD 已明确旧项目 flow 实体与 DDL 不一致、旧 OpenAPI 表域历史原因由 analyst 后续补充，且不阻塞 PRD；后续进入 DB 设计前仍需闭环。 |

## 后续约束

- 不得基于本 PRD 直接进入后端实现或 API 契约生成，仍需按流程完成 DBA/backend/frontend/test 多角色复核。
- API 阶段必须冻结具体接口、BO/VO、枚举、错误码、分页筛选结构、动态字段值结构、文件/任务响应和 OpenAPI 错误反馈。
- DB 设计前必须处理 PU-018 涉及的旧 flow 表不一致和旧 `un_app_` 到 `un_openapi_` 的历史映射说明。
