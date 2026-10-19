# 当前实施控制

- `rebuild-plan.md`：当前第 15 次干净重构的顺序和验收边界。
- `rebuild-state.json`：只记录当前重构的真实断点，旧验收不沿用。
- `rebuild-ledger.json`：绑定当前步骤所需的需求原文行。
- `execution-control.json`：供 Template Base 读取当前步骤。

每批工作估算不超过 240 分钟；业务行为、持久化、授权和失败结果均经真实入口验证后才记为完成。
