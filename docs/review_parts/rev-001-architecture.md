# REV-001 架构复审

- 任务: REV-001
- 执行时间: 2026-06-08
- 负责角色: reviewer
- 结论: pass
- target: none

## Findings

本轮复审未发现新的 P1 架构阻塞。

### P2 创建系统幂等状态仅保存在本机内存，存在集群和生命周期风险

- file: `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/service/impl/PlatformCenterServiceImpl.java`
- evidence: `CREATE_SYSTEM_IDEMPOTENCY` 是 `ConcurrentHashMap` 静态缓存，当前 smoke 和单元测试只证明单 JVM 内不同请求摘要能返回 409。
- impact: 多实例部署、应用重启、事务提交失败后的状态回放和缓存增长仍没有生产级保证。
- target: backend
- recommendation: 后续将创建系统幂等接入统一幂等存储或数据库幂等表，至少包含 requestHash、responseSnapshot、状态、过期时间和事务提交后的落点。
- status: deferred，P2 上线前生产增强项，不阻塞当前 P6 MVP 验收。

## Closed Items

| 原 issue | 复审结论 |
| --- | --- |
| `REV-001-FE-BUILD-ENTRY` | closed。`frontend/package.json`、`frontend/tsconfig.json`、`frontend/package-lock.json` 已补齐，VAL-002 复验 `npm.cmd ci; npm.cmd run build` 通过。 |
| `REV-002-FE-FIELD-TYPES` | closed。字段类型枚举和渲染/校验映射已同步冻结 API，VAL-003 复验 19/19 通过。 |

## Pass Items

| 检查项 | 结论 |
| --- | --- |
| Maven 多模块边界 | `examine-core`、`examine-plat`、`examine-module`、`examine-flow`、`examine-upload`、`examine-app`、`examine-web` 保持多模块结构。 |
| base/manage 分层 | `base` 包保持生成器产物命名，未通过改生成类名避让冲突；业务入口主要在 `manage` 或 web 聚合只读入口。 |
| Bean 同名冲突处理 | `ExamineWebApplication` 使用 `FullyQualifiedAnnotationBeanNameGenerator`，符合“base 生成类名不动、启动扫描层处理冲突”的规则。 |
| manage 命名规范 | `AGENTS.md` 已明确 manage 使用真实业务命名，不使用 `Man`、`ManageImpl` 机械前后缀。 |
| 后端 clean compile | VAL-001 复验通过，8 个 Maven 模块 clean compile SUCCESS。 |
| 前端构建入口 | VAL-002 复验通过，前端 TypeScript 构建入口可复跑。 |

## Review Conclusion

架构复审通过。当前只保留创建系统幂等生产级一致性 P2 风险，建议进入后续上线前增强清单。
