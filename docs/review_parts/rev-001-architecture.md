# REV-001 架构审查

- 任务: REV-001
- 执行时间: 2026-06-08
- 负责角色: reviewer
- 结论: fail
- target: frontend

## Findings

### P1 前端工程入口缺失，当前不是可上线前端架构

- file: `frontend/`
- evidence: `docs/build/frontend-clean-build.md` 已记录 `frontend/package.json` 与 `frontend/tsconfig.json` 不存在，`npm.cmd run build` 返回 ENOENT。
- impact: 前端当前只有 `src/` 和 `docs/`，无法执行 build、typecheck、lint、浏览器 E2E，也无法证明页面入口、路由、渲染、刷新和错误态可运行；这会直接阻断上线验收。
- target: frontend
- recommendation: 补齐前端工程入口、构建脚本、tsconfig、应用入口和最小可运行路由后，重跑 VAL-002、TEST 页面 E2E 和后续 review。

### P1 前端字段类型枚举与冻结 API 不一致

- file: `frontend/src/api/enums.ts:22`
- evidence: `docs/api.md:127` 冻结字段类型包含 `MONEY`、`SWITCH`、`MEMBER`、`DEPT`、`AUTO_NO`；前端 `DYNAMIC_FIELD_TYPES` 使用 `DECIMAL`、`RADIO`、`CHECKBOX`、`DICT`、`BOOLEAN`、`SERIAL`，并缺少上述冻结值。
- impact: 动态字段配置、运行台 schema、页面表单渲染、字段校验和契约映射会使用不同语义，后续即使补齐构建也可能出现字段不可渲染或错误控件。
- target: frontend
- recommendation: 前端按冻结 API 同步字段类型枚举和相关页面控件映射；若前端命名确实更合理，必须回到 API 契约评审，不应在实现侧私自改名。

### P2 创建系统幂等状态仅保存在本机内存，存在集群和生命周期风险

- file: `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/service/impl/PlatformCenterServiceImpl.java:83`
- evidence: `CREATE_SYSTEM_IDEMPOTENCY` 是 `ConcurrentHashMap` 静态缓存，`createSystem` 在方法内写入和读取请求摘要。
- impact: 单机 smoke 能通过，但多实例部署、应用重启、事务提交失败后的状态回放和缓存增长都没有稳定保证；上线后同一幂等键打到不同节点可能重复创建或返回不一致结果。
- target: backend
- recommendation: 后续将创建系统幂等接入统一幂等存储或数据库幂等表，至少包含 requestHash、responseSnapshot、状态、过期时间和事务提交后的落点；当前可作为 MVP 风险项保留，但不能当作生产级幂等能力。

## Pass Items

| 检查项 | 结论 |
| --- | --- |
| Maven 多模块边界 | `examine-core`、`examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app`、`examine-web` 保持多模块结构。 |
| base/manage 分层 | `base` 包保持生成器产物命名，未通过改生成类名避让冲突；业务入口主要在 `manage` 或 web 聚合只读入口。 |
| Bean 同名冲突处理 | `ExamineWebApplication` 使用 `FullyQualifiedAnnotationBeanNameGenerator`，符合“base 生成类名不动、启动扫描层处理冲突”的规则。 |
| manage 命名规范 | 当前规范已明确不要使用 `Man`、`ManageImpl` 机械前后缀；本次未发现需要改 base 名称的做法。 |
| 后端 clean compile | VAL-001 通过，8 个 Maven 模块 clean compile SUCCESS。 |

## Open Questions

1. `examine-web` 中 `AuditOpsServiceImpl` 作为审计/运维聚合只读服务可以接受；若后续写入型运维配置落地，应考虑下沉到对应业务模块或独立 ops 模块，避免 web 承载过多业务编排。
2. 创建系统幂等是否要求生产级多实例一致性，需要 PM/backend 在上线前确认。如果要上线到多节点环境，建议作为 backend 回环项处理。
