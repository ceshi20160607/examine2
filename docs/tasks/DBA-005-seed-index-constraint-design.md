# DBA-005 seed 索引约束并发设计

- taskId: DBA-005
- 标题: seed 索引约束并发设计
- 负责角色: dba
- 所属大任务/模块: DB 设计 / 初始化与约束
- 目标: 汇总 DB 设计分片，补齐生产 seed、唯一索引、状态约束、幂等锁和并发控制规则，生成最终 `docs/db_design.md`。
- 输入文件: `docs/prd.md`、`docs/project_understanding.md`、`docs/legacy_reference.md`、`docs/api.md`、`docs/db_design_parts/domain-map.md`、`docs/db_design_parts/platform-system-rbac-dict.md`、`docs/db_design_parts/module-runtime-export.md`、`docs/db_design_parts/flow-upload-openapi-audit.md`、`docs/service_info.md`
- 输出文件或输出目录: `docs/db_design.md`

## 详细工作内容

- 合并 DBA-001 至 DBA-004 的 DB 设计分片，统一表命名、外键关系、索引口径和数据落点说明。
- 复核 `docs/project_understanding.md` 与 `docs/legacy_reference.md`，补充旧项目表结构、迁移脚本、命名差异和不沿用原因。
- 设计平台管理员、平台角色、平台菜单、平台配置、字段类型元数据生产 seed。
- 设计创建系统事务内初始化的默认租户、成员、系统超级管理员角色、系统菜单、默认应用和字段类型引用。
- 定义唯一索引、nullable 唯一规则、逻辑删除、租户维度、幂等键、自动编号并发策略。

## 完成状态定义

- 默认状态: pending。
- 完成条件: `docs/db_design.md` 汇总全部分片，并完整写入 seed、索引、约束、并发规则、旧项目参考关系和迁移注意事项。

## 验收标准

- 测试样例数据不进入生产 `init.sql`。
- 唯一约束兼顾历史空值、逻辑删除、租户和同步场景。
- `docs/db_design.md` 必须说明旧项目可参考表、明确不沿用的问题和新旧结构差异。

## 测试/自检要求

- 自检默认账号密码不写明文。
- 自检幂等冲突、处理中、回放快照规则可落库。

## 依赖任务

- DBA-002
- DBA-003
- DBA-004

## 可并行关系

- 不可并行；本任务负责将分片设计串行合并为最终 `docs/db_design.md`。

## 不允许事项

- 不把演示业务数据写入生产初始化。
- 不用非原子方式生成并发序号。
