# REV-003 质量测试构建复审

- 任务: REV-003
- 执行时间: 2026-06-08
- 负责角色: reviewer
- 结论: pass
- target: none

## Findings

本轮复审未发现新的 P1 质量或构建阻塞。

### P2 OpenAPI 和并发场景仍有上线前专项覆盖空间

- file: `docs/test_report.md`
- evidence: TEST-005 已补 accessKey、timestamp、body hash、signature、scope 和 rate limit 专属错误码断言；nonce replay、IP 白名单、OpenAPI 幂等冲突，以及自动编号、流程任务、导出任务并发压测仍作为未覆盖风险记录。
- impact: 当前 P6 MVP 主链路和主要 OpenAPI 安全负向断言已通过，但高安全/高并发上线环境仍建议补专项自动化。
- target: test/backend
- recommendation: 后续补充 API 自动化或专用脚本，覆盖 OpenAPI nonce replay、IP 白名单、OpenAPI 幂等冲突和三个并发敏感点。
- status: deferred，P2 上线前增强项，不阻塞当前 P6 MVP 验收。

### P2 创建系统幂等 smoke 通过但生产级一致性未覆盖

- file: `backend/examine-plat/src/main/java/com/unique/examine/plat/manage/service/impl/PlatformCenterServiceImpl.java`
- evidence: 当前测试验证单进程同一幂等键不同请求体返回 409；REV-001 已记录实现是本机静态 `ConcurrentHashMap`。
- impact: 多实例、重启、事务提交失败和缓存过期场景未被测试覆盖。
- target: backend
- recommendation: 若上线到多节点环境，应把创建系统幂等迁移到共享存储或数据库后，再增加跨进程/重启/过期测试。
- status: deferred，P2 上线前生产增强项。

## Closed Items

| 原 issue | 复审结论 |
| --- | --- |
| `REV-001-FE-BUILD-ENTRY` | closed。VAL-002 复验 `npm.cmd ci; npm.cmd run build` 通过。 |
| `REV-003-TEST-OPENAPI-ASSERTION` | closed。TEST-004/TEST-005 已补 OpenAPI accessKey 和安全负向矩阵专属错误码断言。 |
| `REV-003-TEST-RISK-MATRIX` | partially closed。核心 OpenAPI 安全错误码矩阵已补；nonce replay、IP、OpenAPI 幂等冲突和并发压测作为 P2 风险继续跟踪。 |

## Pass Items

| 检查项 | 结论 |
| --- | --- |
| 后端单元测试 | `mvn -pl examine-app -am test` 复验通过，core 13、plat 12、upload 4、module 21、flow 2、app 11。 |
| 后端 clean compile | VAL-001 复验通过，8 个模块 SUCCESS。 |
| 后端主链路 HTTP smoke | TEST-003 已跑通注册、登录、系统、应用、模块、字段、发布、运行记录提交和导出任务创建。 |
| 前端 clean build | VAL-002 复验通过，`npm.cmd ci; npm.cmd run build` 成功。 |
| 契约同步 | VAL-003 复验通过，API ID、错误码、状态枚举、字段类型和 AUTH 鉴权均同步。 |
| 空白检查 | `git diff --check` 通过，仅有 LF/CRLF 工作区转换 warning。 |

## Review Conclusion

质量测试构建复审通过，target=none。剩余 P2 风险建议进入后续上线前增强清单。
