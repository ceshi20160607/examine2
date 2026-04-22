# Flow graph_json 分支推进（vars + fallback）设计

**Goal:** 将当前 flow 的“approve/reject 直接终态”升级为：按 `un_flow_record.graph_json` 推进到下一节点、生成下一条待办；支持条件分支与“都不满足时的兜底（转管理员审批）”。

## 现状（2026-04-16）

- `graph_json` 结构（来自 `docs/sql/08_flow_seed.sql`）：
  - `nodes`: `[{ id, type, name, ... }]`
  - `edges`: `[{ from, to, ... }]`（当前只有 from/to）
- 发起：创建 `un_flow_record` 快照 + 取首个 approve 节点创建 `un_flow_task`
- 办理：`approve/reject` 之后直接将 `instance` 置终态（结束）

## 设计范围（MVP-1）

### 1) 发起时携带 vars，并落库到 variables

- 发起接口 `start` 增加 `vars` 字段：`Map<String, Object>`
- 服务端将 `vars` 落库到 `un_flow_record_var`：
  - `var_key`: Map key
  - `var_type`: `string|number|bool|json`
  - `var_value`: 字符串（json 用 Jackson 序列化为 string）

> 约束：同一 record 内 `var_key` 唯一（表 `uk_flow_var (record_id, var_key)`）。

### 2) edge 增加条件（向后兼容）

在 `graph_json.edges[]` 的每一项上允许新增字段（老数据无这些字段仍可跑）：

- `cond`：条件表达式（可选）
- `priority`：数字（可选，越小越优先；缺省按数组顺序）

**兼容规则：**
- 如果 edges 没有 `cond`（如 demo），则视为“无条件可走”

### 3) 条件表达式（MVP-1 语法）

为避免引入复杂 DSL，MVP-1 仅支持以下形式（均从 variables 读取）：

- `eq(var, literal)`：相等
- `ne(var, literal)`：不等
- `gt(var, number)` / `ge(var, number)` / `lt(var, number)` / `le(var, number)`：数值比较
- `in(var, ["a","b"])`：字符串在集合中
- `exists(var)`：变量存在且非空

**literal 约定：**
- string：双引号 `"x"`
- number：`123` / `12.3`
- bool：`true|false`

### 4) 推进算法（approve + 条件分支）

输入：`instanceId + taskId + action=approve`

步骤：
- 校验：实例运行中、任务待处理、当前用户可办理（同现状）
- 完成当前任务（status=2，finish_time 等）
- 解析 `graph_json`：
  - 找到当前 `nodeId`
  - 收集所有 `edges` 满足 `from == nodeId`
  - 按 `priority` 升序（缺省按数组顺序）
  - 对每条 edge：
    - 若无 `cond`：认为匹配
    - 若有 `cond`：用 variables 计算 true/false
    - 首条匹配即选中 next
  - 若无匹配：抛 `BusinessException(400, "未命中任何分支")`（不做“边级兜底”，兜底走全局/节点异常策略，见下节）
- 得到 `nextNodeId` 后：
  - 若 next 节点 `type=end`：实例置终态（status=2，end_time=now，current_node_id=null）
  - 若 next 节点 `type=approve`：创建下一条 `un_flow_task(status=1)`，实例 `current_node_id=nextNodeId`
  - 其他 type：MVP-1 暂不支持，抛 400

### 5) 节点异常兜底（全局优先 + 节点覆盖）

> 目标：兜底只影响“当前节点怎么继续审批/由谁审批”，不改变最终审批结果语义；管理员（兜底审批人）通过后继续走下一节点。

#### 5.1 配置形态（推荐落在 graph_json.config）

- **全局异常策略**：`graph_json.config.exception_policy`
  - `mode`：`fallback_admin` | `end_instance`
  - `admin_plat_id`：管理员 platId（mode=fallback_admin 时必填）
- **节点级异常策略（可选，覆盖全局）**：`nodes[].config.exception_policy`
  - 结构同上（若缺失则使用全局）

> 兼容：老图没有该配置时，默认 `mode=fallback_admin`，管理员先用发起人代替（MVP），后续再接“参与人规则”。

#### 5.2 触发时机（MVP-1）

当出现以下异常之一，视为“节点配置异常”：
- 当前节点不存在可用的 outgoing edge（无 edges/from 匹配，或解析失败）
- 条件分支存在，但没有任何 cond 命中
- next 节点 type 不支持（非 approve/end）

#### 5.3 行为

- 若 `mode=end_instance`：实例直接终态（写 action_log=terminate/error），不再生成下一 task
- 若 `mode=fallback_admin`：
  - 将“当前节点”的待办**转为管理员待办**（MVP：直接新建一条 task：nodeId 仍为当前节点；assigneePlatId=admin）
  - 管理员 approve 后，继续按“正常推进算法”从当前 nodeId 选择 next 并生成下一节点 task

> 说明：这意味着“异常兜底”不会影响整体结果，只是将当前节点的审批权临时交给管理员。

### 6) reject（MVP-1）

- 保持现状：reject 后实例直接终态（后续再扩展 reject 分支边 `on=reject`）

## 不在本次范围

- 条件分支的复杂表达式（AND/OR、嵌套）
- 并行/汇聚节点、抄送、会签/或签
- 任务候选人规则、管理员自动分配规则
- `/v1/**` 对外鉴权与 flowId 路由（E-2）

## 验收口径（MVP-1）

- 发起时传入 `vars`，能写入 `un_flow_record_var`
- `approve` 后：
  - 能按 `edges[].cond` 命中正确分支并生成下一条 task
  - 若条件都不满足/图异常，能按“全局异常策略”处理：
    - `fallback_admin`：转管理员办理该节点，管理员通过后继续流转到下一节点
    - `end_instance`：按配置直接结束实例
  - 若无异常策略且发生异常，返回 400 且不会把实例错误终结

