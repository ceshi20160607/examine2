# 平台基础表（仅平台域）

逻辑主键名以 README / 开发方案为准（如 **`platId`** 对应列 **`plat_account_id`**）。

---

## 会话策略

- **登录后的 token / 当前 `systemId` / `tenantId` 等会话态**：仅存 **Redis**，**不使用** `plat_session` 表。

---

## 表清单与用途

| 表名 | 用途 |
|------|------|
| **`un_plat_account`** | 平台账号：登录名、密码摘要与盐、手机/邮箱、展示信息、状态、最近登录等。 |
| **`un_plat_config`** | **平台基础配置**：`config_key` 唯一；`config_value` + `value_type`（string/json/number 等）；`group_code` 分组（如 security、notice、ui）；可存公告开关、功能开关等，与 **`un_plat_msg` 内容**配合（配置里定义「是否展示某类通告」、默认文案键等，具体由产品定）。 |
| **`un_plat_login_log`** | **登录日志**：成功/失败、`plat_account_id`（失败可为空）、尝试登录名、IP、UA、设备、时间。 |
| **`un_plat_oper_log`** | **操作日志**（与登录分离）：控制台关键操作，如创建系统、改平台配置；`module_code` / `action_code` / `resource_*` / `detail_json` / `request_id`。业务数据上的操作仍可走 **`audit_biz_log`**（见 schema 第九节）。 |
| **`un_plat_msg`** | **平台级消息**：`msg_type`（如 `announcement` / `tip` / `notice` / `alert` / `maintenance`…）区分**通告、提示、系统告警**等，便于扩展；`source_type` 表示来源（配置发布 / 运营录入 / 系统生成）；`payload_json` 扩展链接、附加参数；`publish_time` / `expire_time` / `status` 控制展示周期。与 **`sys_message`（用户站内信）** 区分：`un_plat_msg` 偏**全平台或按策略广播**，`sys_message` 偏**到人**。 |
| **`un_plat_system`** | 自建「系统/应用」主数据：主键即 **`systemId`**；**预置一行 `id = 0`**，表示 README 中的**平台占位**（`systemId=0`）。非 0 行为用户创建的自建系统。 |
| **`un_plat_tenant`** | 租户：**预置一行 `id = 0` 且 `system_id = 0`**，表示**默认租户占位**（与关多租户时的 `tenantId=0` 约定一致）。其它行为各业务系统下的租户。 |

---

## 与「业务消息」的边界

| 表 | 典型场景 |
|----|----------|
| **`un_plat_msg`** | 运维公告、平台升级提示、登录页通知、全局维护窗口等（类型可扩展）。 |
| **`sys_message`**（v3 第八节） | 用户收件箱式消息（某系统/租户下、到人）。 |

---

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04-04 | 初稿：plat_account、sys_system、sys_tenant |
| 2026-04-04 | 去掉 plat_session（会话改 Redis）；新增 plat_config、plat_login_log、plat_oper_log、plat_msg；sys_* 更名为 plat_system、plat_tenant，并预置 id=0 种子数据 |
