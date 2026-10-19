# 第 15 次重构接续计划

## 依据

- 唯一产品需求源：`docs/user_requirement.md`；94 张表的名称和用途以 `docs/表意义.md` 为准。
- `docs/old_table1.sql` 只参考字段和早期审批逻辑，禁止执行。
- 后端接口范围、依赖顺序与验收点见 `projects/unexamine/backend-plan.md`；已做过的真实验证见 `projects/unexamine/backend-acceptance.md`。
- 业务归属、表与模块依赖见 `projects/unexamine/business-modules.md`；一个 Maven 子模块对应一个实际业务域。

## 当前步骤

1. 按 94 张原名表建 `unexamine_r15`，在各业务模块生成各自的 Entity/Mapper；已完成，保留数据库和验收数据。
2. 按 10 个业务域拆分 `examine-*` 子模块、生成代码所有权、Controller 与接口文档；`base/manage` 在各域包下平级，`examine-web` 为唯一入口；已经干净构建、94 表生成和 9999 真实入口验证。
3. 逐模块完成基础接口，并核对真实请求、持久化、授权、失败结果及跨模块业务链；当前进行中。
4. 由用户验收后端业务边界和接口；未验收前不启动前端开发。
5. 按已验收接口开发一个前端，再验收真实页面行为。

每项实现只围绕可观察结果，不为尚未确定的外部服务、计算口径或未来扩展预建抽象。未完成能力如实列于 `backend-acceptance.md`，编译成功不代表整个后端已验收。
