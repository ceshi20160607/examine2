# DB 影响 · 新 IA vs 现有 schema

> **责任:** dba · P1 · 输入: `sql/init.sql` + RES-003 模块组 IA

## 结论摘要

现有 `.oldbk` schema **基本可复用**；新 IA 主要是**菜单/导航模型**变化，不一定新增大量表。

## 1. 模块组（RES-003）

| 需求 | 现有表（预期） | gap |
|------|----------------|-----|
| 模块组实体 | `un_module_*` 模块组/分组表 | 核对 init.sql 是否有 group 表 |
| 组→模块关系 | module.group_id | 若无字段需 migration |
| 运行态顶栏 | 发布菜单 snapshot | 菜单 JSON 需含 group 层级 |

**Build 动作:** DBA 扫描 init.sql 确认；缺则 P2 增量 SQL，不手写 entity。

## 2. 列表场景 / 列偏好

| 需求 | 表 | MVP |
|------|-----|-----|
| 系统默认场景 | list_view / scene 表 | 已有则复用 |
| 用户列偏好 | user_column_pref | 可 JSON 存 member 扩展 |

## 3. 车系统种子

| 数据 | 来源 |
|------|------|
| 字典/模块/字段 | `che-system-seed.md` |
| 账号 che/plat_admin | seed SQL 或 init 数据段 |

## 4. 不需 MVP 改的

- 平台 vs 系统账号分离（已有）
- record_value 宽表模型（已有）
- OpenAPI 表前缀 un_openapi_（已有）

## 5. P2 退出检查

- [ ] init.sql 在车系统种子下可导入
- [ ] generator 可贴表生成 base
- [ ] 模块组字段与 API 契约一致
