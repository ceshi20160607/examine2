# 工程节点 {{NODE_ID}}：{{NODE_NAME}}

## 1. 节点身份

- status_ref: `session/state.json#currentNode/status`
- slice_gate_ref: `session/state.json#active.slice.gate`
- goal_or_journey:
- acceptance_case_ids:
- owner:
- integration_owner:
- quality_stage: functional | hardening
- cycle_id:
- cycle_start_at:
- cycle_end_at: `cycle_start_at + 240m`
- delivery_cycle_minutes: `240`
- task_count: `dependency_graph`
- real_demo_path:
- rolling_package_path:
- formal_checkpoint: CP1 | CP2 | CP3

## 2. 边界

- delivers:
- does_not_deliver:
- deferred_to_hardening:
- remaining_goal_items:

| module capability | before cycle | committed this cycle | status/remaining reason |
|---|---|---|---|
|  | completed / remaining | yes / no | pending |

工程节点必须覆盖一个可说明的模块结果，不能只覆盖一个接口、页面片段或证据动作。功能节点先交付真实能力和合理的参考桌面 UI 基线；移动端、关键断点、全状态截图、跨模块视觉统一和可访问性可以延期，但必须登记到 release 前的 hardening 节点。

### 2.1 参考桌面 UI 基线（用户可见节点必填）

| area | frozen behavior |
|---|---|
| shell/navigation | 现有系统导航和当前位置 |
| page header | 标题、上下文、说明、主操作 |
| list | 搜索/筛选、工具栏、表头、行操作、空/加载/错误状态 |
| detail/edit | 按用户任务组织的标签页，不堆成长页卡片 |
| actions | primary/secondary/danger 层级和权限/禁用反馈 |

## 3. 契约 artifacts

| artifact | owner | accepted/hash | consumers |
|---|---|---|---|
|  |  |  |  |

消费者只等待自己所需 artifact accepted，不等待整个上游 task passed。

## 4. 模块 DAG

| task | module_scope | depends_on_artifacts | write_scope | estimate | mode | acceptance_command |
|---|---|---|---|---:|---|---|
|  |  |  |  |  |  |  |

无依赖且 `module_scope/write_scope` 不重叠时默认 `parallel`。串行任务必须说明共享 schema/migration、lockfile、公共契约、同一聚合根或实际运行资源冲突。

## 5. 分层验证

| level | when | required result |
|---|---|---|
| task | 每个短任务 | 仅受影响模块 build/unit/API/data/permission/UI 中的必要项；禁止打包和全量矩阵 |
| cycle | 240 分钟到点或承诺项提前完成 | 本周期一次结合测试、生产构建、滚动包、关键角色入口、跨层读回和权限正反例 |
| module page | 一个页面模块全部功能完成 | 按页面真实入口执行列表、表单、右侧标签详情、权限和失败恢复旅程 |
| phase | 阶段结束 | 只执行本阶段承诺的可用结果和阶段回归 |
| project | 全部功能阶段结束 | 全项目功能、权限、数据、部署和用户旅程验收 |
| optional performance | 项目功能验收后且项目明确要求 | 单独立项、单独预算、单独数据规模；核心 Base 流程永不默认运行 |

## 6. 完成

- task_results:
- cumulative_regression_result:
- production_build_result:
- rolling_package_result_and_sha256:
- cold_start_and_health_result:
- browser_journey_result:
- completed_module_items:
- remaining_module_items_with_reason:
- deferred_hardening_items:
- verdict: pass | fail | blocked
- next_node:
