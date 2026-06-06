# DBA-002 平台系统 RBAC 字典表设计

- taskId: DBA-002
- 标题: 平台系统 RBAC 字典表设计
- 负责角色: dba
- 所属大任务/模块: DB 设计 / 平台与系统管理
- 目标: 设计平台账号、系统、租户、成员、部门、角色权限和字典相关表。
- 输入文件: `docs/prd.md`、`docs/api.md`、`docs/service_info.md`、`docs/legacy_reference.md`
- 输出文件或输出目录: `docs/db_design_parts/platform-system-rbac-dict.md`

## 详细工作内容

- 设计平台账号、平台角色、平台菜单、系统、租户和系统成员扩展表。
- 设计系统部门、角色、菜单、操作、字段权限、数据范围；OpenAPI scope catalog 与授权归属只记录与系统权限的边界，不重复设计 OpenAPI 凭证表。
- 设计系统字典类型、字典项、引用关系和缓存版本字段。

## 完成状态定义

- 默认状态: pending。
- 完成条件: 平台与系统管理表结构、字段说明、关系和索引写入 `docs/db_design_parts/platform-system-rbac-dict.md`；最终 `docs/db_design.md` 只由 DBA-005 汇总写入。

## 验收标准

- 平台用户与系统成员扩展清晰分离。
- 字典内置只读、引用限制、启停状态和唯一约束可落地。

## 测试/自检要求

- 自检 `systemId + accountId` 成员唯一规则。
- 自检角色权限、字段权限和数据范围能支撑 RBAC API。

## 依赖任务

- DBA-001

## 可并行关系

- 可与 DBA-003、DBA-004 并行；三者输出分片路径互不重叠，最终由 DBA-005 串行合并到 `docs/db_design.md`。

## 不允许事项

- 不把系统成员设计成独立登录账号。
- 不让平台管理员绕过系统权限直接写业务数据。
