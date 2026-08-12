# MEETING-S3-004：VS3 无代码配置发布合同会审

## 1. 身份

- node_id: `S3-VS3-CONFIG-PUBLISH`
- organized_by: `pm`
- date: `2026-07-11`
- participants: `product, uiux, architect, dba, backend, test`
- authority: `leader`
- status: `completed`
- reviewed_output: `.cursor/session/rebuild/vs3-task-plan.md`

## 2. 独立评审结论

- product/uiux: 目标链清楚，但必须冻结发布粒度、并发草稿、规则冲突、VS5/VS8 边界和移动导航；普通成员必须证明读取旧/新 active version，而非管理员预览或静态模块。
- architect/dba/test: 建议 `examine-module` 规范化草稿、系统级完整 snapshot、配置根 CAS、检查绑定 revision/checksum、动态权限通过公开 facade 注册、回滚生成 N+1；迁移、故障原子性、越权和并发均为 Gate。
- PM 本地交叉检查确认：`JRN-C6` 的导入导出/打印和仪表盘分别由 VS5/VS8 累积完成，VS3 不挂无承接动作。

## 3. P0 决定

| topic | decision | reason/impact |
|---|---|---|
| 发布单元 | 一个 system 的模块组、模块、字段、字典、页面、动作、规则形成完整原子 snapshot | 跨对象引用一致，运行端只有一个 active version |
| 草稿并发 | 一个共享草稿；资源 version + 根 draftRevision 乐观锁；检查绑定 revision/checksum | 不做个人分支和自动猜测合并，冲突可解释 |
| 发布/回滚 | 发布事务注册权限、更新 active、epoch、audit/outbox；回滚要求 CLEAN 并生成 N+1 | 禁止半发布、改写历史和指针式假回滚 |
| 状态 | 草稿、检查、发布版本和资源状态分离；模块停用随发布生效 | 可同时表达 active V1 与 dirty V2 草稿 |
| 权限事实 | plat 管授权和 epoch；module 只存资源权限映射并调用 core facade；组可见性由模块推导 | 避免重复 visibleRoleIds 和跨模块 mapper |
| 页面范围 | VS3 只配置 LIST/FORM/DETAIL；仪表盘归 VS8，自定义页面暂不发布 | 避免跨切片假页面 |
| 动作范围 | VS3 配置基础/声明动作和权限；导入导出打印到 VS5 前不进入运行动作 | 不出现“后续开放”的假按钮 |
| 规则 | 有界结构化 AST、类型化操作符和固定效果；禁止脚本/SQL | 可检查、可解释、可安全编译 |
| B2 表面 | active snapshot 生成权限过滤导航和 published definition；VS3 展示真实 schema 空状态 | 不伪造 VS4 记录 CRUD |
| 移动 | 小屏只读配置状态但必须支持 B2 导航；完整三栏设计器仅 `>=1200px` | 符合复杂配置桌面边界且保证普通成员可用 |

## 4. Leader 决定

- decision: `VS3_CONTRACT_FROZEN`
- unresolved_p0: `none`
- user_decision_needed: `no`
- coding_allowed: `true`（只限 VS3 声明范围）
- next_action: `VS3-002 V3 Flyway schema / examine-module / generator`
