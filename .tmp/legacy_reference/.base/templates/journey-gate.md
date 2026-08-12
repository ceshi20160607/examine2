# 角色旅程 Gate

## 1. Meta

- gate_id:
- linked_goal_id:
- linked_requirement_ids: []
- linked_slice_ids: []
- linked_task_ids: []
- status: OPEN | ENGINEERING_PASS | FAIL | BLOCKED | USER_ACCEPTED
- updated_at:

## 2. 角色旅程

- role:
- identity/context:
- real_entry_point:
- business_outcome:
- requirement/design_reference:

## 3. 流程

1. 用户从：
2. 用户执行：
3. 系统必须：
4. 成功结果显示在：
5. 数据必须保存/可读回：
6. 权限拒绝必须：
7. 失败时必须：

## 4. 跨层契约

| layer | required behavior | contract/path | status |
|---|---|---|---|
| Product/UI |  |  | OPEN |
| Frontend |  |  | OPEN |
| Backend/API |  |  | OPEN |
| Data/readback |  |  | OPEN |
| Permission |  |  | OPEN |
| State/error |  |  | OPEN |
| Audit/operations |  |  | OPEN |

## 5. 生成与手写边界

- generated plumbing:
- handwritten business behavior:
- frontend binding:
- not allowed as completion evidence:

## 6. 验收证据

- browser journey:
- API/data readback:
- permission positive/negative:
- states/errors:
- reload/relogin/restart:
- migration/release:
- visual evidence boundary:
- cleanup:

## 7. 非完成情况

- 页面、接口或表存在但业务结果不成立。
- 生产路径使用 mock/fixture/假成功。
- 权限负例、失败状态或持久化读回缺失。
- 证据没有从真实角色入口开始。
- 实现者是唯一验收者。
- 工程证据被写成用户已满意。

## 8. 签字

- engineering_acceptor:
- engineering_verdict:
- user_signoff_required: yes/no
- user_signoff_path:

单个 task 或 slice 通过只增加本旅程的证据，不得直接把本 Gate 改成 `ENGINEERING_PASS`；必须重新验证完整真实入口和跨层结果。
