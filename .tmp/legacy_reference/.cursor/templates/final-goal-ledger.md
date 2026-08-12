# 最终目标总账

## 1. Meta

- goal_id:
- project:
- owner: leader
- product_owner:
- created_at:
- updated_at:
- source_requirements: []
- source_designs: []
- status: OPEN | ENGINEERING_PASS | USER_ACCEPTED | BLOCKED

## 2. 最终目标

从用户角度描述最终可使用、可交付的结果。不要只描述文件、页面、接口、模块或技术实现。

## 3. 目标角色

| role | 真实责任 | 真实入口 | 必须完成的业务结果 | 禁止看到/执行 |
|---|---|---|---|---|
|  |  |  |  |  |

## 4. 非完成情况

- 页面存在但角色不能完成业务工作。
- API/数据库可用但前端没有绑定真实链路。
- 生成 CRUD 存在但业务规则、权限、状态或副作用缺失。
- 脚本、构建或截图通过但缺少角色旅程证据。
- 交付包不能按目标方式部署、健康、重启或回滚。
- 用户主观满意度或最终验收没有真实签字。

项目专属非完成情况：

-

## 5. 角色旅程 Gate

| gate_id | role | 业务结果 | evidence path | status |
|---|---|---|---|---|
| JRN-001 |  |  |  | OPEN |

## 6. 需求覆盖

| requirement_id | journey/slice/task | engineering status | user status | gap/next action |
|---|---|---|---|---|
|  |  | OPEN | OPEN |  |

## 7. 证据矩阵

| evidence | required | path | status | notes |
|---|---:|---|---|---|
| Deployed browser journey | yes |  | OPEN |  |
| API/database readback | yes |  | OPEN |  |
| Permission positive/negative | yes |  | OPEN |  |
| States/errors/recovery | yes |  | OPEN |  |
| Reload/relogin/restart | depends |  | OPEN |  |
| Migration/release/rollback | depends |  | OPEN |  |

## 8. 生成与手写边界

| area | generated plumbing | handwritten behavior | frontend/user result | evidence |
|---|---|---|---|---|
|  |  |  |  |  |

## 9. 开放决定

| decision | owner | options | recommended | status | path |
|---|---|---|---|---|---|
|  |  |  |  | OPEN |  |

## 10. 最终签字边界

- engineering_final_gate: PASS | FAIL | OPEN
- user_signoff_required: yes
- user_accepted: false
- user_signoff_path:
- known_limitations:

完成层级固定为 `TASK -> SLICE -> PHASE -> JOURNEY -> RELEASE -> FINAL`。低层状态只能作为高层验收输入，不能自动继承为高层通过。

`session/state.json#completionLedger` 必须逐级保存对象记录：task 指向 slice；slice 列全 required task；phase 列全 required slice；journey 列全 required phase；每条记录都有 `verdict/evidencePath/evidenceSha256`。Integration 只能消费 required journey，Release 只能消费 Integration，FINAL 只能消费 Release；数组 ID 或文字结论不能替代逐级记录。
