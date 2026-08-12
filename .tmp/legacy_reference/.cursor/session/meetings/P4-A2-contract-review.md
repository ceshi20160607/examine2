# P4-A2 创建与激活合同评审

- reviewed_at: 2026-07-17T14:21:34+08:00
- coordinator: pm
- participants: product, architect, backend, frontend, uiux, test
- requirements: REQ-RUNTIME-002, REQ-DATA-001, REQ-RBAC-001, REQ-MOBILE-001
- journeys: JRN-B4, JRN-B11
- verdict: PASS

## 需求结论

本节点必须交付普通成员可操作的完整最小业务结果，而不是新增若干写接口：用户从真实记录列表进入 schema 驱动表单，保存 MySQL 草稿，显式激活，再从列表和详情读回。编辑、autosave 和草稿恢复推迟到 P4-B1，避免首个写切片失控。

## 产品与 UI 结论

创建和激活是两个明确动作。创建表单一次收集本节点支持字段；保存成功后展示 DRAFT，并允许用户激活。选择类字段显示可读 label，不能要求用户填写数据库 ID。所有失败保留输入并提供可理解反馈，桌面和移动使用同一业务合同。

## 架构与后端结论

使用现有 `un_module_record`/`un_module_record_value`，本节点不新增业务表。创建绑定 active immutable config snapshot；激活采用 DRAFT + expectedVersion CAS。模块级 create/update 权限和数据范围都由 core port 调用平台实现，module 禁止直接访问平台表。幂等复用 core idempotency 能力。

## 测试结论

独立验收覆盖成功旅程、八类字段、未知/只读/非法类型、缺必填激活、旧快照、幂等重放、幂等冲突、错误版本、重复激活、无权限/越权、刷新与后端重启读回，并在桌面和移动端执行真实浏览器旅程。

## Leader 决策

合同边界、依赖、失败语义和完成边界明确，可以进入 P4-A2-01。任何新增编辑、autosave、归档或复杂查询需求均回到 PM 排入后续节点，不能污染当前执行。
