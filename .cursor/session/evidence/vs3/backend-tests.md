# VS3 后端验证证据

- checked_at: `2026-07-15T12:21:47+08:00`
- verdict: `pass_with_observations`
- command: `mvn.cmd test`

## 结果

- Maven 六模块 reactor `BUILD SUCCESS`。
- Surefire 共 `8` tests，`0` failure/error/skipped；其中 `Vs3DraftJourneyIntegrationTest` 为 `3` 个真实 HTTP 集成测试。
- generator 精确输出清单、旧重复 Mapper 家族清理和四前缀连续重生成后重新执行完整 reactor，结果保持通过。
- 集成环境使用真实 Undertow、Testcontainers MySQL `8.0.44`、Redis `7.4` 和 Flyway `V1 -> V3.2`，不是 mock controller 或内存数据库。
- 持久化验收环境从空 MySQL `8.4.10` 库迁移至 V3.2；最新 jar 重启后 `/management/health` 为 `UP`。

## VS3 覆盖行为

- 系统级共享草稿、资源 version 与根 `draftRevision` CAS；错误 revision 拒绝。
- 模块组、模块、字典、字段、页面、动作和递归规则的真实 HTTP 创建、修改与数据库读回。
- 字段类型专属属性、默认值来源、关系过滤条件、子表列/行策略的结构化校验和引用维护。
- 模块复制采用两阶段复制并重映射模块、字段、默认来源、子表列和嵌套过滤条件中的 ID。
- 检查绑定 revision/checksum；脏草稿、过期检查、引用错误和未授权请求被拒绝。
- 发布生成不可变 snapshot；管理员读取版本差异；`V1 -> V2 -> rollback V3` 不改写历史。
- 发布幂等重放、双请求竞争、唯一约束故障回滚、动态权限注册、owner 默认授权和 authz epoch 提升。
- 幂等键过期后可原位承载不同请求；清理不使用无记录范围删除，双发布竞争无死锁。
- 模块删除同步清理页面组件和以模块子资源为 source/target 的引用；回滚重新运行真实检查并恢复字典 closure。
- 普通成员默认拒绝；角色授权后读取 active navigation/definition；直接访问无权限模块由服务端拒绝。

## 保留观察

- 故障原子性当前通过事务内唯一约束异常验证，尚未单独注入 outbox 或 serialization 故障。
- 并发发布证明单轮竞争只有一个成功结果，尚未加入双方同时就绪屏障和重复压力测试。
