# VS2 Schema 证据

- checked_at: `2026-07-11T09:44:00+08:00`
- migration: `V2_0_0__vs2_org_permission.sql`
- database: MySQL `8.0.44`（Testcontainers）与 MySQL `8.4`（隔离本地容器）
- verdict: `pass`

## 验证结果

1. 空 schema 依次执行 V1、V2 成功，共 `31` 张表、`149` 个独立索引、`215` 个约束、`24` 个数据库生成列。
2. V1 代表数据包含 account/system/tenant/member/role/permission/binding/context；执行 V2 后原数据保留，并成功回填 `member_tenant=1`、`authz_version=1`、`role_draft=1`、context `authz_epoch=1`。
3. `un_plat_department_closure` 使用可写 `tenant_id` 和数据库生成 `tenant_key`；跨租户 ancestor/descendant closure 插入被组合外键拒绝，失败后路径行数为 `0`。
4. 平台 authz epoch 使用正数雪花主键；system authz epoch 主键等于 system id，符合 scope check。
5. role/permission、department/closure、member role/tenant、data scope target 均保留 scope/system/tenant 组合约束；活动申请使用生成 marker 关闭 nullable unique 漏洞。

## 约束分布

| 类型 | 数量 |
|---|---:|
| CHECK | 78 |
| FOREIGN KEY | 56 |
| PRIMARY KEY | 31 |
| UNIQUE | 50 |

连接凭据只在命令进程环境中读取，未写入源码、证据或报告。
