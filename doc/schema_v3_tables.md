# schema_v3 表名一览（做什么用）

对应物理脚本：`doc/schema_v3.sql`。下面是**表名 → 用途**速查，方便评审后再看字段。

---

## 平台登录（与租户无关）

| 表名 | 做什么 |
|------|--------|
| **plat_account** | 平台账号：注册/登录身份，手机邮箱密码等（先有独占平台身份，再进各系统）。 |
| **plat_session** | 平台会话：token、设备、过期；可选记录当前选中的系统/租户（`active_system_id` / `active_tenant_id`）。 |

---

## 系统 / 租户 / 成员（Context 里的 system + tenant）

| 表名 | 做什么 |
|------|--------|
| **sys_system** | 低代码「系统/应用」一条记录，对应 README 的 `systemId`；可配置是否多租户、默认 `tenant_id`。旧库 `company_id` 一般映射到这里。 |
| **sys_tenant** | 租户：仅在系统开启多租户时使用；关多租户时业务多用 `default_tenant_id` 一条逻辑。 |
| **sys_member** | 某平台账号在某个 `system_id + tenant_id` 下的成员身份；可绑定 `un_module_user.id` 等业务用户。 |

---

## 流程模板（定义，可版本化；D6 发起时复制到运行时）

| 表名 | 做什么 |
|------|--------|
| **flow_definition** | 流程定义：名称、分组、绑定模块、平台级/模块级等；**改流程 = 新 definition，不删历史**。 |
| **flow_definition_setting** | 流程级高级设置：撤回/通过规则、限时审批等（对齐旧 `un_examine_setting` 一类能力）。 |
| **flow_node** | 流程**模板节点**：顺序、类型（审批/条件/抄送/转交）、审批人规则、`edit_policy` / `connector_config` 等。 |
| **flow_node_assignee** | 模板节点上的候选人：按人/部门/角色/邮箱等（对齐旧 `un_examine_node_user`）。 |

---

## 流程运行时（审批实例；待办、after 在这里）

| 表名 | 做什么 |
|------|--------|
| **flow_record** | **一条审批整单**：关联业务 `relation_id`、固化的 `flow_definition_id`、**整单状态**（审批中/暂停/通过/拒绝/终止/作废）。 |
| **record_node** | **运行时节点**：发起时从 `flow_node` **整表复制**下来的实例；加签（D17）只在这里追加，**不改**模板。 |
| **record_node_user** | **审批人维度待办/结论**：谁在哪个节点、是否已处理；「我的待办」主要查这张 + `record_node`。 |
| **record_node_after** | **节点 after 快照**：每次提交一条 JSON；常态一条；或签多条时**按最后一次为准**（D13/D21）。 |

---

## 异步与幂等

| 表名 | 做什么 |
|------|--------|
| **outbox_event** | Outbox：事务内落库，异步投递回调、导入导出后续、索引刷新等；至少一次消费，靠 `event_id` 幂等。 |
| **idempotency_record** | 写接口幂等：按 `system + tenant + principal + Idempotency-Key` 存首次成功响应，24h 一类 TTL 可清理。 |

---

## 对外开放与集成

| 表名 | 做什么 |
|------|--------|
| **open_app** | 第三方应用：`app_id` / 密钥摘要、绑定 `system_id+tenant_id`、授权范围 JSON、可选映射 `service_member`。 |
| **connector_secret** | 出站 Connector 用的密钥密文（按系统/租户隔离）；节点配置里引用 id，不把明文写进 JSON。 |

---

## 消息与审计

| 表名 | 做什么 |
|------|--------|
| **sys_message** | 站内消息：某人「能看见的消息列表」；与待办分离，待办走 `record_node` / `record_node_user`。 |
| **audit_biz_log** | 业务操作审计：谁对哪条模块数据做了什么（合规、按记录查）。 |
| **audit_flow_trace** | 流程轨迹：节点动作、意见、转交等，挂 `flow_record_id` 展示。 |
| **audit_tech_log** | 技术排障：Outbox、回调、Connector、慢查询等，可短保留。 |

---

## 低代码模块主数据（本文件未重复建表）

| 说明 |
|------|
| **un_module_***（`doc/module.sql`） | 模块树、字段、**槽位主表** `un_module_record`、**EAV** `un_module_record_data` 等；迁移时给这些表补 `system_id` / `tenant_id` 即可与 README 对齐。 |

---

## 张数统计

- 上表共 **22 张**（v3 脚本内 `CREATE TABLE`）。
- 加上 `module.sql` 里原有模块域表，才是完整低代码 + 审批存储。

若你希望把某几张合并或改名，可以按这张清单讨论定稿后再改 `schema_v3.sql`。
