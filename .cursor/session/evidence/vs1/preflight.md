# VS1 环境预检证据

- checked_at: `2026-07-10T22:46:04+08:00`
- verdict: `pass`
- scope: `S3-VS1-IDENTITY-CONTEXT`

| 项目 | 实际结果 | 结论 |
|---|---|---|
| Java | Oracle JDK `21.0.10` | 满足 Java 21 合同 |
| Maven | `3.8.5`，运行于 Java 21 | Enforcer 通过 |
| Node/npm | `24.14.0` / `11.9.0` | 前端命令通过 |
| Docker | server `29.6.1` | Testcontainers 和隔离环境可用 |
| MySQL | 隔离 MySQL 8.0 集成测试；隔离 MySQL 8.4 schema/generator 复核 | 连接、迁移、读回通过 |
| Redis | 隔离 Redis 7.4 | 会话、限流、切换和清理通过 |
| 用户待决 | 无活动项 | 不阻断 VS1 |

敏感连接信息仅从环境读取，未进入源码、报告或本证据。
